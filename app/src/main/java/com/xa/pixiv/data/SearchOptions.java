package com.xa.pixiv.data;

public final class SearchOptions {
    public String sort = "date_desc";
    public String target = "partial_match_for_tags";
    public Integer bookmarkMin;
    public String type = "all";
    /** hide, all, only */
    public String r18 = "hide";
    public String startDate;
    public String endDate;
    /** 0 all, 1 hide AI, 2 only AI */
    public int aiType = 0;
}
