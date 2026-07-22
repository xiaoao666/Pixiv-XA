package com.xa.pixiv.data;
public final class PixivUser {
    public final long id; public final String name,account,comment,avatar; public final boolean followed;
    public final int illusts,manga,publicBookmarks;
    public PixivUser(long i,String n,String a,String c,String v,boolean f,int x,int m,int b){id=i;name=n;account=a;comment=c;avatar=v;followed=f;illusts=x;manga=m;publicBookmarks=b;}
}
