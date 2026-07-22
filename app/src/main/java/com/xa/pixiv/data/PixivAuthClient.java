package com.xa.pixiv.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Small Pixiv OAuth 2.0 + PKCE client. The flow follows Pixiv's Android client
 * protocol and stores only the one-time verifier in private SharedPreferences.
 */
public final class PixivAuthClient {
    private static final String LOGIN_URL = "https://app-api.pixiv.net/web/v1/login";
    private static final String TOKEN_URL = "https://oauth.secure.pixiv.net/auth/token";
    private static final String CLIENT_ID = "MOBrBDS8blbauoSck0ZfDbtuzpyT";
    private static final String CLIENT_SECRET = "lsACyCD94FhDUtGTXi3QzcFE2uU1hqtDaKeqrdwj";
    private static final String REDIRECT_URI = "https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback";
    private static final String HASH_SECRET = "28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c";
    private static final String PREFS = "xa_pixiv_oauth";
    private static final String VERIFIER = "pkce_verifier";

    private final SharedPreferences prefs;
    private final OkHttpClient client;

    public PixivAuthClient(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        NetworkSettings settings=new NetworkSettings(context);OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if(NetworkSettings.DOH.equals(settings.mode()))builder.dns(new SecurePixivDns());
        if(NetworkSettings.DIRECT.equals(settings.mode()))builder.dns(new SecurePixivDns()).addInterceptor(new CronetInterceptor(context));
        client = builder
                .connectTimeout(18, TimeUnit.SECONDS)
                .readTimeout(22, TimeUnit.SECONDS)
                .writeTimeout(22, TimeUnit.SECONDS)
                .build();
    }

    public String startLoginUrl() {
        String verifier = randomVerifier();
        prefs.edit().putString(VERIFIER, verifier).apply();
        String challenge = base64Url(sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
        return Uri.parse(LOGIN_URL).buildUpon()
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("client", "pixiv-android")
                .build().toString();
    }

    public boolean isCallback(Uri uri) {
        return uri != null && "pixiv".equals(uri.getScheme());
    }

    public AuthResult handleCallback(Uri uri) {
        if (!isCallback(uri)) return AuthResult.failure("这不是 Pixiv 登录回调");
        String code = uri.getQueryParameter("code");
        if (code == null || code.isEmpty()) return AuthResult.failure("登录回调缺少授权码");
        String verifier = prefs.getString(VERIFIER, "");
        if (verifier.isEmpty()) return AuthResult.failure("登录流程已过期，请重新登录");

        FormBody body = new FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("code_verifier", verifier)
                .add("redirect_uri", REDIRECT_URI)
                .add("include_policy", "true")
                .add("get_secure_url", "true")
                .build();
        AuthResult result = executeToken(body);
        if (result.success) prefs.edit().remove(VERIFIER).apply();
        return result;
    }

    public AuthResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) return AuthResult.failure("缺少 refresh token");
        FormBody body = new FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("include_policy", "true")
                .add("get_secure_url", "true")
                .build();
        return executeToken(body);
    }

    private AuthResult executeToken(FormBody body) {
        String clientTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date());
        Request request = new Request.Builder()
                .url(TOKEN_URL)
                .post(body)
                .header("User-Agent", "PixivAndroidApp/5.0.234 (Android " + Build.VERSION.RELEASE + "; " + Build.MODEL + ")")
                .header("App-OS", "android")
                .header("App-OS-Version", Build.VERSION.RELEASE)
                .header("X-Client-Time", clientTime)
                .header("X-Client-Hash", hex(md5((clientTime + HASH_SECRET).getBytes(StandardCharsets.UTF_8))))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String raw = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                return AuthResult.failure("Pixiv 拒绝了登录（HTTP " + response.code() + "）\n" + raw);
            }
            JSONObject root = new JSONObject(raw);
            JSONObject json = root.optJSONObject("response") == null ? root : root.optJSONObject("response");
            String access = json.optString("access_token", "");
            String refresh = json.optString("refresh_token", "");
            if (access.isEmpty() || refresh.isEmpty()) return AuthResult.failure("登录响应中没有 token");
            JSONObject user = json.optJSONObject("user");
            long userId = 0L;
            String userName = "";
            String account = "";
            if (user != null) {
                userId = parseLong(user.opt("id"));
                userName = user.optString("name", "");
                account = user.optString("account", "");
            }
            return AuthResult.success(access, refresh, json.optInt("expires_in", 3600), userId, userName, account);
        } catch (Exception e) {
            return AuthResult.failure(e.getMessage() == null ? "登录网络请求失败" : e.getMessage());
        }
    }

    private static long parseLong(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        try { return Long.parseLong(String.valueOf(value)); } catch (Exception ignored) { return 0L; }
    }

    private static String randomVerifier() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return base64Url(bytes);
    }

    private static String base64Url(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE);
    }

    private static byte[] sha256(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static byte[] md5(byte[] bytes) {
        try { return MessageDigest.getInstance("MD5").digest(bytes); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format(Locale.US, "%02x", value & 0xff));
        return out.toString();
    }

    public static final class AuthResult {
        public final boolean success;
        public final String error;
        public final String accessToken;
        public final String refreshToken;
        public final int expiresIn;
        public final long userId;
        public final String userName;
        public final String userAccount;

        private AuthResult(boolean success, String error, String accessToken, String refreshToken,
                           int expiresIn, long userId, String userName, String userAccount) {
            this.success = success;
            this.error = error;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
            this.userId = userId;
            this.userName = userName == null ? "" : userName;
            this.userAccount = userAccount == null ? "" : userAccount;
        }

        public static AuthResult success(String access, String refresh, int expiresIn,
                                         long userId, String name, String account) {
            return new AuthResult(true, "", access, refresh, expiresIn, userId, name, account);
        }

        public static AuthResult failure(String error) {
            return new AuthResult(false, error == null ? "未知错误" : error, "", "", 0, 0, "", "");
        }
    }
}
