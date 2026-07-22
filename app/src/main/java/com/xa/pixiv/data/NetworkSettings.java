package com.xa.pixiv.data;
import android.content.Context;import android.content.SharedPreferences;
public final class NetworkSettings{public static final String SYSTEM="system",DOH="doh",DIRECT="direct";private final SharedPreferences p;public NetworkSettings(Context c){p=c.getSharedPreferences("xa_network",Context.MODE_PRIVATE);}public String mode(){return p.getString("mode",DOH);}public void setMode(String m){p.edit().putString("mode",m).apply();}}
