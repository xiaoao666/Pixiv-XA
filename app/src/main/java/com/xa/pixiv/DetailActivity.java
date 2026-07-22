package com.xa.pixiv;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.transition.ChangeBounds;
import android.transition.ChangeImageTransform;
import android.transition.TransitionSet;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.xa.pixiv.data.ArtWork;
import com.xa.pixiv.data.PixivRepository;
import com.xa.pixiv.data.PixivComment;
import com.xa.pixiv.data.PixivStamp;
import com.xa.pixiv.data.PixivUser;
import com.xa.pixiv.data.HistoryStore;
import com.xa.pixiv.data.PixivImages;
import com.xa.pixiv.util.DownloadHelper;
import com.xa.pixiv.ui.ImagePagerAdapter;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DetailActivity extends AppCompatActivity {
    public static final String EXTRA_WORK = "art_work";
    private ArtWork work;
    private PixivRepository repository;
    private ExecutorService executor;
    private MaterialButton bookmarkButton;
    private MaterialButton authorFollowButton;
    private boolean authorFollowed;
    private Long replyTo;

    public static void open(Fragment fragment, ArtWork work, ImageView sharedImage) {
        Intent intent = new Intent(fragment.requireContext(), DetailActivity.class);
        intent.putExtra(EXTRA_WORK, work);
        if (work.getPageCount() > 1) {
            fragment.startActivity(intent);
            return;
        }
        sharedImage.setTransitionName("art_image_" + work.getId());
        ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                fragment.requireActivity(), sharedImage, "art_image_" + work.getId());
        fragment.startActivity(intent, options.toBundle());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        TransitionSet transition = new TransitionSet().addTransition(new ChangeBounds()).addTransition(new ChangeImageTransform());
        transition.setDuration(360L);
        getWindow().setSharedElementEnterTransition(transition);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        work = readWork();
        if (work == null) { finish(); return; }
        repository = new PixivRepository(this);
        executor = Executors.newSingleThreadExecutor();
        if (repository.session().isBookmarked(work.getId())) work.setBookmarked(true);
        new HistoryStore(this).record(work);
        setupArtworkImages(work);
        if (work.getPageCount() > work.getPageUrls().size() && !work.isLocal()
                && repository.session().isLoggedIn()) loadCompleteArtwork();
        ((TextView) findViewById(R.id.detail_title)).setText(work.getTitle());
        ((TextView) findViewById(R.id.detail_author)).setText(work.getAuthor());
        View.OnClickListener openAuthor = v -> openAuthor(work.getAuthorId());
        View authorCard = findViewById(R.id.detail_author_card);
        ImageView authorAvatar = findViewById(R.id.detail_author_avatar);
        findViewById(R.id.detail_author).setOnClickListener(openAuthor);
        View authorAction = findViewById(R.id.detail_author_action);
        authorAction.setVisibility(work.getAuthorId() > 0 ? View.VISIBLE : View.GONE);
        authorAction.setOnClickListener(openAuthor);
        authorAvatar.setOnClickListener(openAuthor);
        authorCard.setOnClickListener(openAuthor);
        if (!work.getAuthorAvatar().isEmpty()) {
            Glide.with(authorAvatar).load(PixivImages.glide(this, work.getAuthorAvatar()))
                    .circleCrop().into(authorAvatar);
        }
        authorFollowButton = findViewById(R.id.detail_author_follow);
        if (work.getAuthorId() > 0) {
            authorFollowButton.setEnabled(false);
            authorFollowButton.setOnClickListener(v -> toggleAuthorFollow());
            if (repository.session().isLoggedIn()) loadAuthorCard();
            else {
                authorFollowButton.setText("登录后关注");
                authorFollowButton.setEnabled(true);
            }
        } else {
            authorCard.setClickable(false);
            authorFollowButton.setVisibility(View.GONE);
        }
        ((TextView) findViewById(R.id.detail_badge)).setText(work.getType().toUpperCase(Locale.ROOT));
        ((TextView) findViewById(R.id.detail_stats)).setText(formatStats());
        ChipGroup tags = findViewById(R.id.detail_tags);
        for (String value : work.getTags()) {
            if (value == null || value.isEmpty()) continue;
            Chip chip = new Chip(this);
            chip.setText("# " + value);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)
                    .putExtra(SearchActivity.EXTRA_QUERY, value)));
            tags.addView(chip);
        }
        bookmarkButton = findViewById(R.id.detail_bookmark);
        renderBookmark();
        bookmarkButton.setOnClickListener(v -> toggleBookmark());
        findViewById(R.id.back_button).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        findViewById(R.id.detail_download).setOnClickListener(v -> DownloadHelper.save(this, work));
        findViewById(R.id.detail_share).setOnClickListener(v -> shareWork());
        findViewById(R.id.comment_send).setOnClickListener(v -> sendComment());
        findViewById(R.id.comment_stamps).setOnClickListener(v -> loadStamps());
        findViewById(R.id.reply_cancel).setOnClickListener(v -> clearReply());
        findViewById(R.id.related_all).setOnClickListener(v -> startActivity(new Intent(this,FeedActivity.class)
                .putExtra(FeedActivity.EXTRA_MODE,"related").putExtra(FeedActivity.EXTRA_ILLUST_ID,work.getId())));
        loadCommunity();
    }

    private void setupArtworkImages(ArtWork artwork) {
        ImageView image = findViewById(R.id.detail_image);
        ViewPager2 pager = findViewById(R.id.detail_image_pager);
        TextView indicator = findViewById(R.id.detail_page_indicator);
        int pages = artwork.getPageUrls().size();
        if (pages > 1) {
            image.setVisibility(View.GONE);
            pager.setVisibility(View.VISIBLE);
            indicator.setVisibility(View.VISIBLE);
            indicator.setText("1 / " + pages + "  ·  左右滑动");
            pager.setAdapter(new ImagePagerAdapter(artwork, position -> openFullImage(position)));
            pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override public void onPageSelected(int position) {
                    indicator.setText((position + 1) + " / " + pages + "  ·  左右滑动");
                }
            });
        } else {
            pager.setVisibility(View.GONE);
            indicator.setVisibility(View.GONE);
            image.setVisibility(View.VISIBLE);
            image.setTransitionName("art_image_" + artwork.getId());
            if (artwork.isLocal()) Glide.with(this).load(artwork.getImageRes()).fitCenter().into(image);
            else Glide.with(this).load(PixivImages.glide(this, artwork.getOriginalUrl())).fitCenter().into(image);
            image.setOnClickListener(v -> openFullImage(0));
        }
    }

    private void loadCompleteArtwork() {
        executor.execute(() -> {
            try {
                ArtWork complete = repository.loadIllust(work.getId());
                runOnUiThread(() -> {
                    work = complete;
                    setupArtworkImages(complete);
                });
            } catch (Exception ignored) { }
        });
    }

    private void openFullImage(int page) {
        startActivity(new Intent(this, FullImageActivity.class)
                .putExtra(FullImageActivity.EXTRA_WORK, work)
                .putExtra(FullImageActivity.EXTRA_PAGE, page));
    }

    private void loadCommunity() {
        if (work.isLocal() || !repository.session().isLoggedIn()) {
            ((TextView) findViewById(R.id.comments_empty)).setText("登录 Pixiv 后可查看与发布评论");
            return;
        }
        executor.execute(() -> {
            java.util.List<ArtWork> related = repository.loadRelated(work.getId()).getItems();
            java.util.List<PixivComment> comments;
            try { comments = repository.loadComments(work.getId()); }
            catch (Exception e) { comments = new java.util.ArrayList<>(); }
            java.util.List<PixivComment> finalComments = comments;
            runOnUiThread(() -> { renderRelated(related); renderComments(finalComments); });
        });
    }

    private void renderRelated(java.util.List<ArtWork> values) {
        LinearLayout strip = findViewById(R.id.related_strip); strip.removeAllViews();
        for (ArtWork item : values.subList(0, Math.min(8, values.size()))) {
            View card = LayoutInflater.from(this).inflate(R.layout.item_related, strip, false);
            ImageView image = card.findViewById(R.id.related_image); ((TextView)card.findViewById(R.id.related_title)).setText(item.getTitle());
            GlideUrl url = PixivImages.glide(this, item.getPreviewUrl());
            Glide.with(image).load(url).centerCrop().into(image);
            card.setOnClickListener(v -> startActivity(new Intent(this, DetailActivity.class).putExtra(EXTRA_WORK, item)));
            strip.addView(card);
        }
    }

    private void renderComments(java.util.List<PixivComment> comments) {
        TextView empty=findViewById(R.id.comments_empty); empty.setVisibility(comments.isEmpty()?View.VISIBLE:View.GONE);
        if(comments.isEmpty()) empty.setText("还没有评论，来写第一条吧");
        LinearLayout list=findViewById(R.id.comments_list);list.removeAllViews();
        for (PixivComment comment : comments) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_comment, list, false);
            bindComment(row, comment, false);
            TextView meta = row.findViewById(R.id.comment_meta);
            meta.setText(comment.date + (comment.hasReplies ? "  · 查看回复" : "") + "  · 点击卡片回复");
            if (comment.hasReplies) {
                meta.setOnClickListener(v -> loadReplies(comment, row, list.indexOfChild(row)));
            }
            row.setOnClickListener(v -> selectReply(comment));
            list.addView(row);
        }
    }

    private void sendComment() {
        EditText input=findViewById(R.id.comment_input);String text=input.getText()==null?"":input.getText().toString().trim();
        if(text.isEmpty()){Toast.makeText(this,"评论不能为空",Toast.LENGTH_SHORT).show();return;}
        View send=findViewById(R.id.comment_send);send.setEnabled(false);Long parent=replyTo;executor.execute(()->{try{repository.addComment(work.getId(),text,parent);
            runOnUiThread(()->{input.setText("");clearReply();send.setEnabled(true);Toast.makeText(this,parent==null?"评论已发布":"回复已发布",Toast.LENGTH_SHORT).show();loadCommunity();});
        }catch(Exception e){runOnUiThread(()->{send.setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});
    }

    private void loadStamps(){View scroller=findViewById(R.id.stamp_scroller);if(scroller.getVisibility()==View.VISIBLE){scroller.setVisibility(View.GONE);return;}scroller.setVisibility(View.VISIBLE);LinearLayout list=findViewById(R.id.stamp_list);if(list.getChildCount()>0)return;executor.execute(()->{try{java.util.List<PixivStamp> stamps=repository.loadStamps();runOnUiThread(()->{for(PixivStamp s:stamps){ImageView image=new ImageView(this);int size=(int)(76*getResources().getDisplayMetrics().density);image.setLayoutParams(new LinearLayout.LayoutParams(size,size));image.setPadding(6,6,6,6);Glide.with(image).load(PixivImages.stamp(this,s.url)).into(image);image.setOnClickListener(v->sendStamp(s.id));list.addView(image);}});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}});}
    private void sendStamp(long id){findViewById(R.id.comment_stamps).setEnabled(false);Long parent=replyTo;executor.execute(()->{try{repository.addStamp(work.getId(),id,parent);runOnUiThread(()->{clearReply();findViewById(R.id.comment_stamps).setEnabled(true);Toast.makeText(this,"贴图已发布",Toast.LENGTH_SHORT).show();loadCommunity();});}catch(Exception e){runOnUiThread(()->{findViewById(R.id.comment_stamps).setEnabled(true);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}});}

    private void loadReplies(PixivComment parent, View anchor, int index) {
        TextView meta = anchor.findViewById(R.id.comment_meta);
        meta.setEnabled(false);
        meta.setText("正在加载回复…");
        executor.execute(() -> {
            try {
                java.util.List<PixivComment> replies = repository.loadReplies(parent.id);
                runOnUiThread(() -> {
                    LinearLayout list = findViewById(R.id.comments_list);
                    int at = index + 1;
                    for (PixivComment comment : replies) {
                        View row = LayoutInflater.from(this).inflate(R.layout.item_comment, list, false);
                        row.setPadding(row.getPaddingLeft() + 32, row.getPaddingTop(), row.getPaddingRight(), row.getPaddingBottom());
                        bindComment(row, comment, true);
                        ((TextView) row.findViewById(R.id.comment_meta)).setText(comment.date + " · 点击卡片回复");
                        row.setOnClickListener(v -> selectReply(comment));
                        list.addView(row, at++);
                    }
                    meta.setText(parent.date + "  · 已展开 " + replies.size() + " 条回复");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    meta.setEnabled(true);
                    meta.setText(parent.date + "  · 查看回复");
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void bindComment(View row, PixivComment comment, boolean reply) {
        TextView user = row.findViewById(R.id.comment_user);
        user.setText((reply ? "↳ " : "") + comment.userName);
        TextView content = row.findViewById(R.id.comment_text);
        ImageView stamp = row.findViewById(R.id.comment_stamp);
        TextView stampError = row.findViewById(R.id.comment_stamp_error);
        content.setVisibility(comment.stampUrl.isEmpty() ? View.VISIBLE : View.GONE);
        content.setText(comment.text);
        if (!comment.stampUrl.isEmpty()) {
            loadCommentStamp(stamp, stampError, comment.stampUrl);
        } else {
            stamp.setVisibility(View.GONE);
            stampError.setVisibility(View.GONE);
        }
        ImageView avatar = row.findViewById(R.id.comment_avatar);
        if (!comment.userAvatar.isEmpty()) Glide.with(avatar).load(pixivImage(comment.userAvatar)).circleCrop().into(avatar);
        View.OnClickListener author = v -> openAuthor(comment.userId);
        avatar.setOnClickListener(author);
        user.setOnClickListener(author);
    }

    private void loadCommentStamp(ImageView stamp, TextView error, String url) {
        error.setVisibility(View.GONE);
        error.setOnClickListener(v -> loadCommentStamp(stamp, error, url));
        stamp.animate().cancel();
        stamp.setAlpha(0.35f);
        stamp.setVisibility(View.VISIBLE);
        Glide.with(stamp)
                .load(PixivImages.stamp(this, url))
                .listener(new RequestListener<Drawable>() {
                    @Override public boolean onLoadFailed(GlideException failure, Object model,
                                                           Target<Drawable> target, boolean first) {
                        stamp.setVisibility(View.GONE);
                        error.setVisibility(View.VISIBLE);
                        return false;
                    }

                    @Override public boolean onResourceReady(Drawable resource, Object model,
                                                              Target<Drawable> target,
                                                              DataSource source, boolean first) {
                        error.setVisibility(View.GONE);
                        stamp.setVisibility(View.VISIBLE);
                        stamp.animate().alpha(1f).setDuration(180L).start();
                        return false;
                    }
                })
                .into(stamp);
    }

    private void loadAuthorCard() {
        executor.execute(() -> {
            try {
                PixivUser user = repository.loadUser(work.getAuthorId());
                runOnUiThread(() -> {
                    authorFollowed = user.followed;
                    ((TextView) findViewById(R.id.detail_author)).setText(user.name);
                    authorFollowButton.setEnabled(true);
                    renderAuthorFollow();
                    if (!user.avatar.isEmpty()) {
                        ImageView avatar = findViewById(R.id.detail_author_avatar);
                        Glide.with(avatar).load(PixivImages.glide(this, user.avatar))
                                .circleCrop().into(avatar);
                    }
                });
            } catch (Exception ignored) {
                runOnUiThread(() -> {
                    authorFollowButton.setText("关注");
                    authorFollowButton.setEnabled(true);
                });
            }
        });
    }

    private void toggleAuthorFollow() {
        if (!repository.session().isLoggedIn()) {
            Toast.makeText(this, "登录 Pixiv 后才能关注画师", Toast.LENGTH_SHORT).show();
            return;
        }
        authorFollowButton.setEnabled(false);
        boolean next = !authorFollowed;
        executor.execute(() -> {
            try {
                repository.setFollow(work.getAuthorId(), next);
                runOnUiThread(() -> {
                    authorFollowed = next;
                    authorFollowButton.setEnabled(true);
                    renderAuthorFollow();
                    Toast.makeText(this, next ? "已关注画师" : "已取消关注", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    authorFollowButton.setEnabled(true);
                    Toast.makeText(this, error.getMessage() == null ? "关注状态更新失败" : error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void renderAuthorFollow() {
        authorFollowButton.setText(authorFollowed ? "已关注" : "关注");
    }

    private GlideUrl pixivImage(String url) {
        return PixivImages.glide(this, url);
    }

    private void selectReply(PixivComment comment) {
        replyTo = comment.id;
        ((TextView) findViewById(R.id.reply_target)).setText("正在回复 @" + comment.userName);
        findViewById(R.id.reply_banner).setVisibility(View.VISIBLE);
        EditText input = findViewById(R.id.comment_input);
        input.setHint("回复 @" + comment.userName);
        input.requestFocus();
    }

    private void clearReply() {
        replyTo = null;
        findViewById(R.id.reply_banner).setVisibility(View.GONE);
        ((EditText) findViewById(R.id.comment_input)).setHint("写下评论");
    }

    private void openAuthor(long userId) {
        if (userId <= 0) return;
        startActivity(new Intent(this, AuthorActivity.class)
                .putExtra(AuthorActivity.EXTRA_USER_ID, userId));
    }

    private ArtWork readWork() {
        if (Build.VERSION.SDK_INT >= 33) return getIntent().getSerializableExtra(EXTRA_WORK, ArtWork.class);
        return (ArtWork) getIntent().getSerializableExtra(EXTRA_WORK);
    }

    private String formatStats() {
        String likes = work.getBookmarkCount() >= 10000
                ? String.format(Locale.US, "%.1fw", work.getBookmarkCount() / 10000f)
                : String.valueOf(work.getBookmarkCount());
        return "♥ " + likes + "    " + work.getWidth() + " × " + work.getHeight() + "    " + work.getPageCount() + "P";
    }

    private void toggleBookmark() {
        bookmarkButton.setEnabled(false);
        executor.execute(() -> {
            try {
                boolean state = repository.toggleBookmark(work);
                new HistoryStore(this).record(work);
                runOnUiThread(() -> {
                    bookmarkButton.setEnabled(true);
                    renderBookmark();
                    Toast.makeText(this, state ? "已同步到 Pixiv 收藏" : "已从 Pixiv 收藏移除", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    bookmarkButton.setEnabled(true);
                    Toast.makeText(this, error.getMessage() == null ? "收藏同步失败，请检查网络" : error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void renderBookmark() {
        bookmarkButton.setText(work.isBookmarked() ? "已收藏" : "收藏");
        bookmarkButton.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.white)));
    }

    private void shareWork() {
        String url = work.isLocal() ? "XA 本地演示作品：" + work.getTitle()
                : "https://www.pixiv.net/artworks/" + work.getId();
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, work.getTitle() + " · " + work.getAuthor() + "\n" + url);
        startActivity(Intent.createChooser(share, "分享作品"));
    }

    @Override protected void onDestroy() {
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }
}
