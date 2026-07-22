package com.xa.pixiv.data;

import com.xa.pixiv.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class DemoRepository {
    private DemoRepository() {}

    public static List<ArtWork> all() {
        List<ArtWork> list = new ArrayList<>();
        list.add(new ArtWork(1001, "暮色列车", "星屑研究所", "illust", "", "", R.drawable.demo_art_01, 776, 1600, 1, 8421, tags("原创", "夜空", "少女"), false));
        list.add(new ArtWork(1002, "旧书店的午后", "玻璃汽水", "illust", "", "", R.drawable.demo_art_02, 776, 1600, 1, 6250, tags("日常", "暖色", "室内"), false));
        list.add(new ArtWork(1003, "海风来信", "Shiro Note", "manga", "", "", R.drawable.demo_art_03, 1078, 1600, 4, 11320, tags("Blue Archive", "夏日", "漫画"), false));
        list.add(new ArtWork(1004, "After School", "melo", "illust", "", "", R.drawable.demo_art_04, 1134, 1600, 1, 9750, tags("制服", "粉色", "氛围"), false));
        list.add(new ArtWork(1005, "抱一下吗，老师？", "Azel", "illust", "", "", R.drawable.demo_art_05, 1132, 1600, 1, 15470, tags("白洲梓", "Blue Archive", "可爱"), false));
        list.add(new ArtWork(1006, "你在看什么！", "azuney", "manga", "", "", R.drawable.demo_art_06, 1024, 1536, 2, 7241, tags("表情", "漫画", "校园"), false));
        list.add(new ArtWork(1007, "Memory Fragment", "Shirasu Archive", "illust", "", "", R.drawable.demo_art_07, 1600, 964, 1, 18620, tags("白洲梓", "蓝色", "横图"), false));
        list.add(new ArtWork(1008, "围炉煮茶", "F0rest", "illust", "", "", R.drawable.demo_art_08, 1132, 1600, 2, 13280, tags("阿罗娜", "普拉娜", "冬日"), false));
        list.add(new ArtWork(1009, "FOX 小队", "Kivotos Journal", "manga", "", "", R.drawable.demo_art_09, 1600, 1291, 3, 8640, tags("FOX小队", "群像", "漫画"), false));
        list.add(new ArtWork(1010, "超辉夜姬！", "GA", "illust", "", "", R.drawable.demo_art_10, 1600, 894, 1, 20314, tags("群像", "舞台", "色彩"), false));
        list.add(new ArtWork(1011, "Happy Birthday", "Santinn", "illust", "", "", R.drawable.demo_art_11, 1536, 864, 2, 16100, tags("生日", "白洲梓", "花"), false));
        list.add(new ArtWork(1012, "午后时光", "F0rest", "novel", "", "", R.drawable.demo_hero, 1600, 900, 1, 5520, tags("短篇", "治愈", "咖啡"), false));
        return list;
    }

    public static List<ArtWork> search(String query) {
        if (query == null || query.trim().isEmpty()) return all();
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<ArtWork> out = new ArrayList<>();
        for (ArtWork work : all()) {
            boolean match = work.getTitle().toLowerCase(Locale.ROOT).contains(needle)
                    || work.getAuthor().toLowerCase(Locale.ROOT).contains(needle);
            if (!match) {
                for (String tag : work.getTags()) {
                    if (tag.toLowerCase(Locale.ROOT).contains(needle)) {
                        match = true;
                        break;
                    }
                }
            }
            if (match) out.add(work);
        }
        return out;
    }

    private static List<String> tags(String... values) {
        return Arrays.asList(values);
    }
}
