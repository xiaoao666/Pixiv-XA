package com.xa.pixiv.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class SessionStore {
    private static final String PREFS = "xa_pixiv_session";
    private static final String ACCESS = "access_token";
    private static final String REFRESH = "refresh_token";
    private static final String EXPIRES_AT = "expires_at";
    private static final String USER_ID = "user_id";
    private static final String USER_NAME = "user_name";
    private static final String USER_ACCOUNT = "user_account";
    private static final String BOOKMARKS = "local_bookmarks";
    private static final String ONBOARDED = "onboarded";
    private static final String R18_ENABLED = "r18_enabled";

    private final SharedPreferences prefs;

    public SessionStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveAuth(PixivAuthClient.AuthResult result) {
        SharedPreferences.Editor edit = prefs.edit()
                .putString(ACCESS, result.accessToken)
                .putString(REFRESH, result.refreshToken)
                .putLong(EXPIRES_AT, System.currentTimeMillis() + result.expiresIn * 1000L)
                .putBoolean(ONBOARDED, true);
        if (result.userId > 0) edit.putLong(USER_ID, result.userId);
        if (!result.userName.isEmpty()) edit.putString(USER_NAME, result.userName);
        if (!result.userAccount.isEmpty()) edit.putString(USER_ACCOUNT, result.userAccount);
        edit.apply();
    }

    public boolean isLoggedIn() { return !getAccessToken().isEmpty(); }
    public String getAccessToken() { return prefs.getString(ACCESS, ""); }
    public String getRefreshToken() { return prefs.getString(REFRESH, ""); }
    public long getExpiresAt() { return prefs.getLong(EXPIRES_AT, 0L); }
    public long getUserId() { return prefs.getLong(USER_ID, 0L); }
    public String getUserName() { return prefs.getString(USER_NAME, ""); }
    public String getUserAccount() { return prefs.getString(USER_ACCOUNT, ""); }
    public boolean isOnboarded() { return prefs.getBoolean(ONBOARDED, false); }
    public void setOnboarded() { prefs.edit().putBoolean(ONBOARDED, true).apply(); }
    public boolean isR18Enabled() { return prefs.getBoolean(R18_ENABLED, false); }
    public void setR18Enabled(boolean enabled) { prefs.edit().putBoolean(R18_ENABLED, enabled).apply(); }

    public void logout() {
        prefs.edit().remove(ACCESS).remove(REFRESH).remove(EXPIRES_AT)
                .remove(USER_ID).remove(USER_NAME).remove(USER_ACCOUNT).apply();
    }

    public Set<String> getBookmarkedIds() {
        Set<String> stored = prefs.getStringSet(BOOKMARKS, Collections.emptySet());
        return new HashSet<>(stored == null ? Collections.emptySet() : stored);
    }

    public boolean isBookmarked(long id) {
        return getBookmarkedIds().contains(String.valueOf(id));
    }

    public boolean toggleBookmark(long id) {
        Set<String> values = getBookmarkedIds();
        String key = String.valueOf(id);
        boolean added;
        if (values.contains(key)) {
            values.remove(key);
            added = false;
        } else {
            values.add(key);
            added = true;
        }
        prefs.edit().putStringSet(BOOKMARKS, values).apply();
        return added;
    }

    public void setBookmarked(long id, boolean bookmarked) {
        Set<String> values = getBookmarkedIds();
        String key = String.valueOf(id);
        if (bookmarked) values.add(key); else values.remove(key);
        prefs.edit().putStringSet(BOOKMARKS, values).apply();
    }
}
