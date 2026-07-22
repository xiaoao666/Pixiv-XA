package com.xa.pixiv.data;

public final class PixivComment {
    public final long id;
    public final String userName;
    public final String text;
    public final String date;
    public final boolean hasReplies;
    public final String stampUrl;
    public final long userId;
    public final String userAvatar;

    public PixivComment(long id, String userName, String text, String date, boolean hasReplies) {
        this.id = id;
        this.userName = userName;
        this.text = text;
        this.date = date;
        this.hasReplies = hasReplies;
        this.stampUrl = "";
        this.userId = 0L;
        this.userAvatar = "";
    }

    public PixivComment(long id,String userName,String text,String date,boolean hasReplies,String stampUrl,long userId,String userAvatar){this.id=id;this.userName=userName;this.text=text;this.date=date;this.hasReplies=hasReplies;this.stampUrl=stampUrl==null?"":stampUrl;this.userId=userId;this.userAvatar=userAvatar==null?"":userAvatar;}
}
