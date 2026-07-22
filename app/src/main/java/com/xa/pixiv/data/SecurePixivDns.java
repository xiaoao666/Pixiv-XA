package com.xa.pixiv.data;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Shaft-inspired DoH/fallback DNS that preserves standard TLS hostname verification. */
public final class SecurePixivDns implements Dns {
    private static final String[] API = {"104.18.42.239", "172.64.145.17"};
    private static final String[] IMAGE = {"210.140.139.134", "210.140.139.133", "210.140.139.131"};
    private final OkHttpClient bootstrap = new OkHttpClient();
    @Override public List<InetAddress> lookup(String host) throws UnknownHostException {
        if (!host.endsWith("pixiv.net") && !host.endsWith("pximg.net")) return Dns.SYSTEM.lookup(host);
        try {
            Request req = new Request.Builder().url("https://1.1.1.1/dns-query?name=" + host + "&type=A")
                    .header("Accept", "application/dns-json").header("Host", "cloudflare-dns.com").build();
            try (Response response = bootstrap.newCall(req).execute()) {
                JSONArray answers = new JSONObject(response.body() == null ? "" : response.body().string()).optJSONArray("Answer");
                List<InetAddress> out = new ArrayList<>();
                if (answers != null) for (int i=0;i<answers.length();i++) { JSONObject a=answers.optJSONObject(i); if(a!=null&&a.optInt("type")==1) out.add(InetAddress.getByName(a.optString("data"))); }
                if (!out.isEmpty()) return out;
            }
        } catch (Exception ignored) {}
        List<InetAddress> fallback = new ArrayList<>();
        for (String ip : host.endsWith("pximg.net") ? IMAGE : API) fallback.add(InetAddress.getByName(ip));
        return fallback;
    }
}
