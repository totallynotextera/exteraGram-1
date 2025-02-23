package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.TopicsController;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.FiltersView;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.TopicSearchCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.ChatNotificationsPopupWrapper;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.FragmentContextView;
import org.telegram.ui.Components.InviteMembersBottomSheet;
import org.telegram.ui.Components.JoinGroupAlert;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerItemsEnterAnimator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Components.UnreadCounterTextView;
import org.telegram.ui.Delegates.ChatActivityMemberRequestsDelegate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;

public class TopicsFragment extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, FragmentContextView.ChatActivityInterface {

    final long chatId;
    ArrayList<TLRPC.TL_forumTopic> forumTopics = new ArrayList<>();

    SizeNotifierFrameLayout contentView;
    ChatAvatarContainer avatarContainer;
    ChatActivity.ThemeDelegate themeDelegate;
    FrameLayout floatingButtonContainer;
    Adapter adapter = new Adapter();
    private final TopicsController topicsController;
    OnTopicSelectedListener onTopicSelectedListener;

    private float floatingButtonTranslation;
    private float floatingButtonHideProgress;

    private boolean floatingHidden = false;
    private final AccelerateDecelerateInterpolator floatingInterpolator = new AccelerateDecelerateInterpolator();

    LinearLayoutManager layoutManager;

    private static final int toggle_id = 1;
    private static final int add_member_id = 2;
    private static final int create_topic_id = 3;
    private static final int pin_id = 4;
    private static final int unpin_id = 5;
    private static final int mute_id = 6;
    private static final int delete_id = 7;
    private static final int read_id = 8;
    private static final int close_topic_id = 9;
    private static final int restart_topic_id = 10;
    private static final int delete_chat_id = 11;

    private boolean removeFragmentOnTransitionEnd;
    TLRPC.ChatFull chatFull;
    boolean canShowCreateTopic;
    private UnreadCounterTextView bottomOverlayChatText;
    private RecyclerListView recyclerListView;
    private ActionBarMenuSubItem createTopicSubmenu;
    private ActionBarMenuSubItem addMemberSubMenu;
    private ActionBarMenuSubItem deleteChatSubmenu;
    private boolean bottomPannelVisible = true;
    private float searchAnimationProgress = 0f;
    private float searchAnimation2Progress = 0f;

    HashSet<Integer> selectedTopics = new HashSet<>();
    private NumberTextView selectedDialogsCountTextView;
    private ActionBarMenuItem pinItem;
    private ActionBarMenuItem unpinItem;
    private ActionBarMenuItem muteItem;
    private ActionBarMenuItem deleteItem;
    private ActionBarMenuSubItem readItem;
    private ActionBarMenuSubItem closeTopic;
    private ActionBarMenuSubItem restartTopic;
    ActionBarMenuItem otherItem;
    private RadialProgressView bottomOverlayProgress;
    private FrameLayout bottomOverlayContainer;
    private ActionBarMenuItem searchItem;
    private ActionBarMenuItem other;
    private SearchContainer searchContainer;
    private boolean searching, searchingNotEmpty;
    private boolean opnendForSelect;
    private boolean openedForForward;
    HashSet<Integer> excludeTopics;
    private boolean mute = false;

    private boolean scrollToTop;
    private boolean endReached;
    StickerEmptyView topicsEmptyView;

    FragmentContextView fragmentContextView;
    private ChatObject.Call groupCall;
    private DefaultItemAnimator itemAnimator;
    private boolean loadingTopics;
    RecyclerItemsEnterAnimator itemsEnterAnimator;
    DialogsActivity dialogsActivity;

    private boolean updateAnimated;
    private long lastAnimatedDuration;

    private int transitionAnimationIndex;
    private int transitionAnimationGlobalIndex;
    private View blurredView;

    private boolean joinRequested;
    private ChatActivityMemberRequestsDelegate pendingRequestsDelegate;

    float slideFragmentProgress = 1f;
    boolean isSlideBackTransition;
    boolean isDrawerTransition;
    ValueAnimator slideBackTransitionAnimator;

    private FrameLayout topView;

    public TopicsFragment(Bundle bundle) {
        super(bundle);
        chatId = arguments.getLong("chat_id", 0);
        opnendForSelect = arguments.getBoolean("for_select", false);
        openedForForward = arguments.getBoolean("forward_to", false);
        topicsController = getMessagesController().getTopicsController();
    }

    public static void prepareToSwitchAnimation(ChatActivity chatActivity) {
        boolean needCreateTopicsFragment = false;
        if (chatActivity.getParentLayout().getFragmentStack().size() <= 1) {
            needCreateTopicsFragment = true;
        } else {
            BaseFragment previousFragment = chatActivity.getParentLayout().getFragmentStack().get(chatActivity.getParentLayout().getFragmentStack().size() - 2);
            if (previousFragment instanceof TopicsFragment) {
                TopicsFragment topicsFragment = (TopicsFragment) previousFragment;
                if (topicsFragment.chatId != -chatActivity.getDialogId()) {
                    needCreateTopicsFragment = true;
                }
            } else {
                needCreateTopicsFragment = true;
            }
        }
        if (needCreateTopicsFragment) {
            Bundle bundle = new Bundle();
            bundle.putLong("chat_id", -chatActivity.getDialogId());
            TopicsFragment topicsFragment = new TopicsFragment(bundle);
            chatActivity.getParentLayout().addFragmentToStack(topicsFragment, chatActivity.getParentLayout().getFragmentStack().size() - 1);
        }
        chatActivity.setSwitchFromTopics(true);
        chatActivity.finishFragment();
    }

    @Override
    public View createView(Context context) {
        fragmentView = contentView = new SizeNotifierFrameLayout(context) {
            {
                setWillNotDraw(false);
            }

            @Override
            public void draw(Canvas canvas) {
                super.draw(canvas);

                getParentLayout().drawHeaderShadow(canvas, (int) (actionBar.getY() + actionBar.getHeight() * actionBar.getScaleY()));
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int height = MeasureSpec.getSize(heightMeasureSpec);

                int actionBarHeight = 0;
                for (int i = 0; i < getChildCount(); i++) {
                    View child = getChildAt(i);
                    if (child instanceof ActionBar) {
                        child.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                        actionBarHeight = child.getMeasuredHeight();
                    }
                }
                for (int i = 0; i < getChildCount(); i++) {
                    View child = getChildAt(i);
                    if (!(child instanceof ActionBar)) {
                        if (child.getFitsSystemWindows()) {
                            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                        } else {
                            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, actionBarHeight);
                        }
                    }
                }
                setMeasuredDimension(width, height);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                final int count = getChildCount();

                final int parentLeft = getPaddingLeft();
                final int parentRight = right - left - getPaddingRight();

                final int parentTop = getPaddingTop();
                final int parentBottom = bottom - top - getPaddingBottom();

                for (int i = 0; i < count; i++) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() != GONE) {
                        final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                        final int width = child.getMeasuredWidth();
                        final int height = child.getMeasuredHeight();

                        int childLeft;
                        int childTop;

                        int gravity = lp.gravity;
                        if (gravity == -1) {
                            gravity = Gravity.NO_GRAVITY;
                        }

                        boolean forceLeftGravity = false;
                        final int layoutDirection;
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                            layoutDirection = getLayoutDirection();
                        } else {
                            layoutDirection = 0;
                        }
                        final int absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection);
                        final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                        switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                            case Gravity.CENTER_HORIZONTAL:
                                childLeft = parentLeft + (parentRight - parentLeft - width) / 2 +
                                        lp.leftMargin - lp.rightMargin;
                                break;
                            case Gravity.RIGHT:
                                if (!forceLeftGravity) {
                                    childLeft = parentRight - width - lp.rightMargin;
                                    break;
                                }
                            case Gravity.LEFT:
                            default:
                                childLeft = parentLeft + lp.leftMargin;
                        }

                        switch (verticalGravity) {
                            case Gravity.CENTER_VERTICAL:
                                childTop = parentTop + (parentBottom - parentTop - height) / 2 +
                                        lp.topMargin - lp.bottomMargin;
                                break;
                            case Gravity.BOTTOM:
                                childTop = parentBottom - height - lp.bottomMargin;
                                break;
                            case Gravity.TOP:
                            default:
                                childTop = parentTop + lp.topMargin;
                                if (child == topView) {
                                    topView.setPadding(0, actionBar.getTop() + actionBar.getMeasuredHeight(), 0, 0);
                                } else if (!(child instanceof ActionBar)) {
                                    childTop += actionBar.getTop() + actionBar.getMeasuredHeight();
                                }
                        }

                        child.layout(childLeft, childTop, childLeft + width, childTop + height);
                    }
                }
            }

            @Override
            protected void drawList(Canvas blurCanvas, boolean top) {
                for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                    View child = recyclerListView.getChildAt(i);
                    if (child.getY() < AndroidUtilities.dp(100) && child.getVisibility() == View.VISIBLE) {
                        int restore = blurCanvas.save();
                        blurCanvas.translate(recyclerListView.getX() + child.getX(), getY() + recyclerListView.getY() + child.getY());
                        child.draw(blurCanvas);
                        blurCanvas.restoreToCount(restore);
                    }
                }
            }
        };
        contentView.needBlur = true;

        actionBar.setAddToContainer(false);
        actionBar.setCastShadows(false);
        actionBar.setClipContent(true);

        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (selectedTopics.size() > 0) {
                        clearSelectedTopics();
                        return;
                    }
                    finishFragment();
                    return;
                }
                switch (id) {
                    case toggle_id:
                        switchToChat(false);
                        break;
                    case add_member_id:
                        if (chatFull != null && chatFull.participants != null) {
                            LongSparseArray<TLObject> users = new LongSparseArray<>();
                            for (int a = 0; a < chatFull.participants.participants.size(); a++) {
                                users.put(chatFull.participants.participants.get(a).user_id, null);
                            }
                            long chatId = chatFull.id;
                            InviteMembersBottomSheet bottomSheet = new InviteMembersBottomSheet(context, currentAccount, users, chatFull.id, TopicsFragment.this, themeDelegate) {
                                @Override
                                protected boolean canGenerateLink() {
                                    TLRPC.Chat chat = getMessagesController().getChat(chatId);
                                    return chat != null && ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_INVITE);
                                }
                            };
                            bottomSheet.setDelegate((users1, fwdCount) -> {
                                int N = users1.size();
                                int[] finished = new int[1];
                                for (int a = 0; a < N; a++) {
                                    TLRPC.User user = users1.get(a);
                                    getMessagesController().addUserToChat(chatId, user, fwdCount, null, TopicsFragment.this, () -> {
                                        if (++finished[0] == N) {
                                            BulletinFactory.of(TopicsFragment.this).createUsersAddedBulletin(users1, getMessagesController().getChat(chatId)).show();
                                        }
                                    });
                                }
                            });
                            bottomSheet.show();
                        }
                        break;
                    case create_topic_id:
                        TopicCreateFragment fragment = TopicCreateFragment.create(chatId, 0);
                        presentFragment(fragment);
                        AndroidUtilities.runOnUIThread(() -> {
                            fragment.showKeyboard();
                        }, 200);
                        break;
                    case delete_chat_id:
                        TLRPC.Chat chatLocal = getMessagesController().getChat(chatId);
                        AlertsCreator.createClearOrDeleteDialogAlert(TopicsFragment.this, false, chatLocal, null, false, true, false, (param) -> {
                            getMessagesController().deleteDialog(-chatId, 0);
                            finishFragment();
                        }, themeDelegate);
                        break;
                    case delete_id:
                        deleteTopics(selectedTopics, () -> {
                            clearSelectedTopics();
                        });
                        break;
                    case pin_id:
                    case unpin_id:
                        if (selectedTopics.size() > 0) {
                            scrollToTop = true;
                            updateAnimated = true;
                            topicsController.pinTopic(chatId, selectedTopics.iterator().next(), id == pin_id);
                        }
                        clearSelectedTopics();
                        break;
                    case mute_id:
                        Iterator<Integer> iterator = selectedTopics.iterator();
                        while (iterator.hasNext()) {
                            int topicId = iterator.next();
                            getNotificationsController().muteDialog(-chatId, topicId, mute);
                        }
                        clearSelectedTopics();
                        break;
                    case restart_topic_id:
                    case close_topic_id:
                        updateAnimated = true;
                        ArrayList<Integer> list = new ArrayList<>(selectedTopics);
                        for (int i = 0; i < list.size(); ++i) {
                            topicsController.toggleCloseTopic(chatId, list.get(i), id == close_topic_id);
                        }
                        clearSelectedTopics();
                        break;
                    case read_id:
                        list = new ArrayList<>(selectedTopics);
                        for (int i = 0; i < list.size(); ++i) {
                            TLRPC.TL_forumTopic topic = topicsController.findTopic(chatId, list.get(i));
                            if (topic != null) {
                                getMessagesController().markMentionsAsRead(-chatId, topic.id);
                                getMessagesController().markDialogAsRead(-chatId, topic.top_message, 0, topic.topMessage.date, false, topic.id, 0, true, 0);
                                getMessagesStorage().updateRepliesMaxReadId(chatId, topic.id, topic.top_message, 0, true);
                            }
                        }
                        clearSelectedTopics();
                        break;
                }
                super.onItemClick(id);
            }
        });


        actionBar.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putLong("chat_id", chatId);
            ProfileActivity fragment = new ProfileActivity(args, avatarContainer.getSharedMediaPreloader());
//                fragment.setChatInfo(parentFragment.getCurrentChatInfo());
//                fragment.setPlayProfileAnimation(byAvatar ? 2 : 1);
            presentFragment(fragment);
        });

        ActionBarMenu menu = actionBar.createMenu();

        searchItem = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                animateToSearchView(true);
                searchContainer.setSearchString("");
                searchContainer.setAlpha(0);
                searchContainer.emptyView.showProgress(true, false);
            }


            @Override
            public void onSearchCollapse() {
                animateToSearchView(false);
            }

            @Override
            public void onTextChanged(EditText editText) {
                String text = editText.getText().toString();
                searchContainer.setSearchString(text);
            }

            @Override
            public void onSearchFilterCleared(FiltersView.MediaFilterData filterData) {

            }
        });
        searchItem.setSearchPaddingStart(56);
        searchItem.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));
        other = menu.addItem(0, R.drawable.ic_ab_other, themeDelegate);
        other.addSubItem(toggle_id, R.drawable.msg_discussion, LocaleController.getString("TopicViewAsMessages", R.string.TopicViewAsMessages));
        addMemberSubMenu = other.addSubItem(add_member_id, R.drawable.msg_addcontact, LocaleController.getString("AddMember", R.string.AddMember));
        createTopicSubmenu = other.addSubItem(create_topic_id, R.drawable.msg_topic_create, LocaleController.getString("CreateTopic", R.string.CreateTopic));
        deleteChatSubmenu = other.addSubItem(delete_chat_id, R.drawable.msg_leave, LocaleController.getString("LeaveMegaMenu", R.string.LeaveMegaMenu), themeDelegate);

        avatarContainer = new ChatAvatarContainer(context, this, false);
        avatarContainer.getAvatarImageView().setRoundRadius(AndroidUtilities.dp(16));
        avatarContainer.setOccupyStatusBar(!AndroidUtilities.isTablet());
        actionBar.addView(avatarContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, !inPreviewMode ? 56 : 0, 0, 86, 0));

        recyclerListView = new RecyclerListView(context) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                checkForLoadMore();
            }
        };
        DefaultItemAnimator defaultItemAnimator = new DefaultItemAnimator();
        defaultItemAnimator.setSupportsChangeAnimations(false);
        defaultItemAnimator.setDelayAnimations(false);
        recyclerListView.setItemAnimator(itemAnimator = defaultItemAnimator);
        recyclerListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                checkForLoadMore();
            }
        });
        recyclerListView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);
        recyclerListView.setItemsEnterAnimator(itemsEnterAnimator = new RecyclerItemsEnterAnimator(recyclerListView, true));
        recyclerListView.setOnItemClickListener((view, position) -> {
            if (opnendForSelect) {
                TLRPC.TL_forumTopic topic = forumTopics.get(position);
                if (onTopicSelectedListener != null) {
                    onTopicSelectedListener.onTopicSelected(topic);
                }
                if (dialogsActivity != null) {
                    dialogsActivity.didSelectResult(-chatId, topic.id, true, false);
                }
                removeFragmentOnTransitionEnd = true;
                return;
            }
            if (selectedTopics.size() > 0) {
                toggleSelection(view);
                return;
            }
            TLRPC.TL_forumTopic topic = forumTopics.get(position);
            ForumUtilities.openTopic(TopicsFragment.this, chatId, topic, 0);
        });
        recyclerListView.setOnItemLongClickListener((view, position, x, y) -> {
            if (opnendForSelect) {
                return false;
            }
            if (!actionBar.isActionModeShowed() && !AndroidUtilities.isTablet() && view instanceof TopicDialogCell) {
                TopicDialogCell cell = (TopicDialogCell) view;
                if (cell.isPointInsideAvatar(x, y)) {
                    return showChatPreview(cell);
                }
            }
            toggleSelection(view);
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            return true;
        });
        recyclerListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                contentView.invalidateBlur();
            }
        });
        recyclerListView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
        recyclerListView.setAdapter(adapter);
        recyclerListView.setClipToPadding(false);
        recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {

            int prevPosition;
            int prevTop;

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                if (firstVisibleItem != RecyclerView.NO_POSITION) {
                    RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(firstVisibleItem);

                    int firstViewTop = 0;
                    if (holder != null) {
                        firstViewTop = holder.itemView.getTop();
                    }
                    boolean goingDown;
                    boolean changed = true;
                    if (prevPosition == firstVisibleItem) {
                        final int topDelta = prevTop - firstViewTop;
                        goingDown = firstViewTop < prevTop;
                        changed = Math.abs(topDelta) > 1;
                    } else {
                        goingDown = firstVisibleItem > prevPosition;
                    }

                    hideFloatingButton(goingDown || !canShowCreateTopic, true);
                }
            }
        });

        contentView.addView(recyclerListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        ((ViewGroup.MarginLayoutParams) recyclerListView.getLayoutParams()).topMargin = -AndroidUtilities.dp(100);
        floatingButtonContainer = new FrameLayout(getContext());
        floatingButtonContainer.setVisibility(View.VISIBLE);
        contentView.addView(floatingButtonContainer, LayoutHelper.createFrame((Build.VERSION.SDK_INT >= 21 ? 56 : 60), (Build.VERSION.SDK_INT >= 21 ? 56 : 60), (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, LocaleController.isRTL ? 14 : 0, 0, LocaleController.isRTL ? 0 : 14, 14));
        floatingButtonContainer.setOnClickListener(v -> {
            presentFragment(TopicCreateFragment.create(chatId, 0));
        });

        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = ContextCompat.getDrawable(getParentActivity(), R.drawable.floating_shadow).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            drawable = combinedDrawable;
        } else {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(floatingButtonContainer, View.TRANSLATION_Z, AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(floatingButtonContainer, View.TRANSLATION_Z, AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            floatingButtonContainer.setStateListAnimator(animator);
            floatingButtonContainer.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                }
            });
        }
        floatingButtonContainer.setBackground(drawable);
        RLottieImageView floatingButton = new RLottieImageView(context);
        floatingButton.setImageResource(R.drawable.ic_chatlist_add_2);
        floatingButtonContainer.setContentDescription(LocaleController.getString("CreateTopic", R.string.CreateTopic));

        floatingButtonContainer.addView(floatingButton, LayoutHelper.createFrame(24, 24, Gravity.CENTER));


        FlickerLoadingView flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setViewType(FlickerLoadingView.TOPIC_CELL_TYPE);
        flickerLoadingView.setVisibility(View.GONE);
        flickerLoadingView.showDate(true);

        EmptyViewContainer emptyViewContainer = new EmptyViewContainer(context);
        emptyViewContainer.textView.setAlpha(0);

        topicsEmptyView = new StickerEmptyView(context, flickerLoadingView, StickerEmptyView.STICKER_TYPE_NO_CONTACTS) {
            boolean showProgressInternal;

            @Override
            public void showProgress(boolean show, boolean animated) {
                super.showProgress(show, animated);
                showProgressInternal = show;
                if (animated) {
                    emptyViewContainer.textView.animate().alpha(show ? 0f : 1f).start();
                } else {
                    emptyViewContainer.textView.animate().cancel();
                    emptyViewContainer.textView.setAlpha(show ? 0f : 1f);
                }
            }
        };
        try {
            topicsEmptyView.stickerView.getImageReceiver().setAutoRepeat(2);
        } catch (Exception ignore) {}
        topicsEmptyView.showProgress(loadingTopics, fragmentBeginToShow);
        topicsEmptyView.title.setText(LocaleController.getString("NoTopics", R.string.NoTopics));
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder("d");
        ColoredImageSpan coloredImageSpan = new ColoredImageSpan(R.drawable.ic_ab_other);
        coloredImageSpan.setSize(AndroidUtilities.dp(16));
        spannableStringBuilder.setSpan(coloredImageSpan, 0, 1, 0);
        topicsEmptyView.subtitle.setText(
                AndroidUtilities.replaceCharSequence("%s", LocaleController.getString("NoTopicsDescription", R.string.NoTopicsDescription), spannableStringBuilder)
        );


        emptyViewContainer.addView(flickerLoadingView);
        emptyViewContainer.addView(topicsEmptyView);
        contentView.addView(emptyViewContainer);

        recyclerListView.setEmptyView(emptyViewContainer);

        bottomOverlayContainer = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                Theme.chat_composeShadowDrawable.draw(canvas);
                super.dispatchDraw(canvas);
            }
        };
        bottomOverlayChatText = new UnreadCounterTextView(context);
        bottomOverlayContainer.addView(bottomOverlayChatText);
        contentView.addView(bottomOverlayContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));
        bottomOverlayChatText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                joinToGroup();
            }
        });

        bottomOverlayProgress = new RadialProgressView(context, themeDelegate);
        bottomOverlayProgress.setSize(AndroidUtilities.dp(22));
        bottomOverlayProgress.setProgressColor(getThemedColor(Theme.key_chat_fieldOverlayText));
        bottomOverlayProgress.setVisibility(View.INVISIBLE);
        bottomOverlayContainer.addView(bottomOverlayProgress, LayoutHelper.createFrame(30, 30, Gravity.CENTER));

        updateChatInfo();

        bottomOverlayChatText.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_chat_fieldOverlayText), 26), Theme.RIPPLE_MASK_ALL));
        floatingButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
        bottomOverlayContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        actionBar.setActionModeColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));

        searchContainer = new SearchContainer(context);
        searchContainer.setVisibility(View.GONE);
        contentView.addView(searchContainer);
        EditTextBoldCursor editText = searchItem.getSearchField();

        searchContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
//        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
//        editText.setHintTextColor(Theme.getColor(Theme.key_player_time));
//        editText.setCursorColor(Theme.getColor(Theme.key_chat_messagePanelCursor));

        actionBar.setDrawBlurBackground(contentView);

        getMessagesStorage().loadChatInfo(chatId, true, null, true, false, 0);

        topView = new FrameLayout(context);
        contentView.addView(topView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.TOP));

        TLRPC.Chat currentChat = getCurrentChat();
        if (currentChat != null) {
            pendingRequestsDelegate = new ChatActivityMemberRequestsDelegate(this, currentChat, this::updateTopView);
            pendingRequestsDelegate.setChatInfo(chatFull, false);
            topView.addView(pendingRequestsDelegate.getView(), ViewGroup.LayoutParams.MATCH_PARENT, pendingRequestsDelegate.getViewHeight());
        }

        fragmentContextView = new FragmentContextView(context, this, false, themeDelegate) {
            @Override
            public void setTopPadding(float value) {
                super.topPadding = value;
                updateTopView();
            }
        };
        topView.addView(fragmentContextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.TOP | Gravity.LEFT));

        FrameLayout.LayoutParams layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        if (inPreviewMode && Build.VERSION.SDK_INT >= 21) {
            layoutParams.topMargin = AndroidUtilities.statusBarHeight;
        }
        contentView.addView(actionBar, layoutParams);

        checkForLoadMore();

        blurredView = new View(context) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                if (fragmentView != null) {
                    fragmentView.invalidate();
                }
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            blurredView.setForeground(new ColorDrawable(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_windowBackgroundWhite), 100)));
        }
        blurredView.setFocusable(false);
        blurredView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        blurredView.setOnClickListener(e -> {
            finishPreviewFragment();
        });
        blurredView.setFitsSystemWindows(true);

        bottomPannelVisible = true;

        updateChatInfo();

        return fragmentView;
    }

    public void switchToChat(boolean removeFragment) {
        removeFragmentOnTransitionEnd = removeFragment;

        Bundle bundle = new Bundle();
        bundle.putLong("chat_id", chatId);
        ChatActivity chatActivity = new ChatActivity(bundle);
        chatActivity.setSwitchFromTopics(true);
        presentFragment(chatActivity);
    }

    private void updateTopView() {
        float translation = 0;
        if (fragmentContextView != null) {
            translation += Math.max(0, fragmentContextView.getTopPadding());
            fragmentContextView.setTranslationY(translation);
        }
        View pendingRequestsView = pendingRequestsDelegate != null ? pendingRequestsDelegate.getView() : null;
        if (pendingRequestsView != null) {
            pendingRequestsView.setTranslationY(translation + pendingRequestsDelegate.getViewEnterOffset());
            translation += pendingRequestsDelegate.getViewEnterOffset() + pendingRequestsDelegate.getViewHeight();
        }
        recyclerListView.setTranslationY(Math.max(0, translation));
    }


    private void deleteTopics(HashSet<Integer> selectedTopics, Runnable runnable) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(LocaleController.getPluralString("DeleteTopics", selectedTopics.size()));
        ArrayList<Integer> topicsToRemove = new ArrayList<>(selectedTopics);
        if (selectedTopics.size() == 1) {
            TLRPC.TL_forumTopic topic = topicsController.findTopic(chatId, topicsToRemove.get(0));
            builder.setMessage(LocaleController.formatString("DeleteSelectedTopic", R.string.DeleteSelectedTopic, topic.title));
        } else {
            builder.setMessage(LocaleController.getString("DeleteSelectedTopics", R.string.DeleteSelectedTopics));
        }
        builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                excludeTopics = new HashSet<>();
                excludeTopics.addAll(selectedTopics);
                updateTopicsList(true, false);
                BulletinFactory.of(TopicsFragment.this).createUndoBulletin(LocaleController.getPluralString("TopicsDeleted", selectedTopics.size()), () -> {
                    excludeTopics = null;
                    updateTopicsList(true, false);
                }, () -> {
                    topicsController.deleteTopics(chatId, topicsToRemove);
                    runnable.run();
                }).show();
                clearSelectedTopics();
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
        }
    }

    private boolean showChatPreview(TopicDialogCell cell) {
        cell.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        final ActionBarPopupWindow.ActionBarPopupWindowLayout[] previewMenu = new ActionBarPopupWindow.ActionBarPopupWindowLayout[1];
        int flags = ActionBarPopupWindow.ActionBarPopupWindowLayout.FLAG_USE_SWIPEBACK;
        previewMenu[0] = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getParentActivity(), R.drawable.popup_fixed_alert, getResourceProvider(), flags);

        TLRPC.TL_forumTopic topic = cell.forumTopic;
        ChatNotificationsPopupWrapper chatNotificationsPopupWrapper = new ChatNotificationsPopupWrapper(getContext(), currentAccount, previewMenu[0].getSwipeBack(), false, false, new ChatNotificationsPopupWrapper.Callback() {
            @Override
            public void dismiss() {
                finishPreviewFragment();
            }

            @Override
            public void toggleSound() {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                boolean enabled = !preferences.getBoolean("sound_enabled_" + NotificationsController.getSharedPrefKey(-chatId, topic.id), true);
                preferences.edit().putBoolean("sound_enabled_" + NotificationsController.getSharedPrefKey(-chatId, topic.id), enabled).apply();
                finishPreviewFragment();
                if (BulletinFactory.canShowBulletin(TopicsFragment.this)) {
                    BulletinFactory.createSoundEnabledBulletin(TopicsFragment.this, enabled ? NotificationsController.SETTING_SOUND_ON : NotificationsController.SETTING_SOUND_OFF, getResourceProvider()).show();
                }

            }

            @Override
            public void muteFor(int timeInSeconds) {
                finishPreviewFragment();
                if (timeInSeconds == 0) {
                    if (getMessagesController().isDialogMuted(-chatId, topic.id)) {
                        getNotificationsController().muteDialog(-chatId, topic.id, false);
                    }
                    if (BulletinFactory.canShowBulletin(TopicsFragment.this)) {
                        BulletinFactory.createMuteBulletin(TopicsFragment.this, NotificationsController.SETTING_MUTE_UNMUTE, timeInSeconds, getResourceProvider()).show();
                    }
                } else {
                    getNotificationsController().muteUntil(-chatId, topic.id, timeInSeconds);
                    if (BulletinFactory.canShowBulletin(TopicsFragment.this)) {
                        BulletinFactory.createMuteBulletin(TopicsFragment.this, NotificationsController.SETTING_MUTE_CUSTOM, timeInSeconds, getResourceProvider()).show();
                    }
                }

            }

            @Override
            public void showCustomize() {
                finishPreviewFragment();
                AndroidUtilities.runOnUIThread(() -> {
                    Bundle args = new Bundle();
                    args.putLong("dialog_id", -chatId);
                    args.putInt("topic_id", topic.id);
                    presentFragment(new ProfileNotificationsActivity(args, themeDelegate));
                }, 500);
            }

            @Override
            public void toggleMute() {
                finishPreviewFragment();
                boolean mute = !getMessagesController().isDialogMuted(-chatId, topic.id);
                getNotificationsController().muteDialog(-chatId, topic.id, mute);

                if (BulletinFactory.canShowBulletin(TopicsFragment.this)) {
                    BulletinFactory.createMuteBulletin(TopicsFragment.this, mute ? NotificationsController.SETTING_MUTE_FOREVER : NotificationsController.SETTING_MUTE_UNMUTE, mute ? Integer.MAX_VALUE : 0, getResourceProvider()).show();
                }
            }
        }, getResourceProvider());

        int muteForegroundIndex = previewMenu[0].addViewToSwipeBack(chatNotificationsPopupWrapper.windowLayout);
        chatNotificationsPopupWrapper.type = ChatNotificationsPopupWrapper.TYPE_PREVIEW_MENU;
        chatNotificationsPopupWrapper.update(-chatId, topic.id, null);

        if (ChatObject.canManageTopics(getCurrentChat())) {
            ActionBarMenuSubItem pinItem = new ActionBarMenuSubItem(getParentActivity(), true, false);
            if (topic.pinned) {
                pinItem.setTextAndIcon(LocaleController.getString("DialogUnpin", R.string.DialogUnpin), R.drawable.msg_unpin);
            } else {
                pinItem.setTextAndIcon(LocaleController.getString("DialogPin", R.string.DialogPin), R.drawable.msg_pin);
            }
            pinItem.setMinimumWidth(160);
            pinItem.setOnClickListener(e -> {
                scrollToTop = true;
                updateAnimated = true;
                topicsController.pinTopic(chatId, topic.id, !topic.pinned);
                finishPreviewFragment();
            });

            previewMenu[0].addView(pinItem);
        }

        ActionBarMenuSubItem muteItem = new ActionBarMenuSubItem(getParentActivity(), false, false);
        if (getMessagesController().isDialogMuted(-chatId, topic.id)) {
            muteItem.setTextAndIcon(LocaleController.getString("Unmute", R.string.Unmute), R.drawable.msg_mute);
        } else {
            muteItem.setTextAndIcon(LocaleController.getString("Mute", R.string.Mute), R.drawable.msg_unmute);
        }
        muteItem.setMinimumWidth(160);
        muteItem.setOnClickListener(e -> {
            if (getMessagesController().isDialogMuted(-chatId, topic.id)) {
                getNotificationsController().muteDialog(-chatId, topic.id, false);
                finishPreviewFragment();
                if (BulletinFactory.canShowBulletin(TopicsFragment.this)) {
                    BulletinFactory.createMuteBulletin(TopicsFragment.this, NotificationsController.SETTING_MUTE_UNMUTE, 0, getResourceProvider()).show();
                }
            } else {
                previewMenu[0].getSwipeBack().openForeground(muteForegroundIndex);
            }
        });
        previewMenu[0].addView(muteItem);

        if (ChatObject.canManageTopic(currentAccount, getCurrentChat(), topic)) {
            ActionBarMenuSubItem closeItem = new ActionBarMenuSubItem(getParentActivity(), false, false);
            if (topic.closed) {
                closeItem.setTextAndIcon(LocaleController.getString("RestartTopic", R.string.RestartTopic), R.drawable.msg_topic_restart);
            } else {
                closeItem.setTextAndIcon(LocaleController.getString("CloseTopic", R.string.CloseTopic), R.drawable.msg_topic_close);
            }
            closeItem.setMinimumWidth(160);
            closeItem.setOnClickListener(e -> {
                updateAnimated = true;
                topicsController.toggleCloseTopic(chatId, topic.id, !topic.closed);
                finishPreviewFragment();
            });
            previewMenu[0].addView(closeItem);
        }

        if (ChatObject.canManageTopics(getCurrentChat())) {
            ActionBarMenuSubItem deleteItem = new ActionBarMenuSubItem(getParentActivity(), false, true);
            deleteItem.setTextAndIcon(LocaleController.getString("DeleteTopics_one", R.string.DeleteTopics_one), R.drawable.msg_delete);
            deleteItem.setIconColor(getThemedColor(Theme.key_dialogRedIcon));
            deleteItem.setTextColor(getThemedColor(Theme.key_dialogTextRed));
            deleteItem.setMinimumWidth(160);
            deleteItem.setOnClickListener(e -> {
                HashSet<Integer> hashSet = new HashSet();
                hashSet.add(topic.id);
                deleteTopics(hashSet, () -> {
                    finishPreviewFragment();
                });
            });
            previewMenu[0].addView(deleteItem);
        }

        prepareBlurBitmap();
        Bundle bundle = new Bundle();
        bundle.putLong("chat_id", chatId);
        ChatActivity chatActivity = new ChatActivity(bundle);
        ForumUtilities.applyTopic(chatActivity, MessagesStorage.TopicKey.of(-chatId, cell.forumTopic.id));
        presentFragmentAsPreviewWithMenu(chatActivity, previewMenu[0]);
        return false;
    }

    private void checkLoading() {
        loadingTopics = topicsController.isLoading(chatId);
        if (topicsEmptyView != null && forumTopics.size() == 0) {
            topicsEmptyView.showProgress(loadingTopics, fragmentBeginToShow);
        }
        updateCreateTopicButton(true);
    }

    ValueAnimator searchAnimator;
    ValueAnimator searchAnimator2;
    boolean animateSearchWithScale;

    private void animateToSearchView(boolean showSearch) {
        searching = showSearch;
        if (searchAnimator != null) {
            searchAnimator.removeAllListeners();
            searchAnimator.cancel();
        }
        searchAnimator = ValueAnimator.ofFloat(searchAnimationProgress, showSearch ? 1f : 0);
        AndroidUtilities.updateViewVisibilityAnimated(searchContainer, false, 1f, true);
        animateSearchWithScale = !showSearch && searchContainer.getVisibility() == View.VISIBLE && searchContainer.getAlpha() == 1f;
        searchAnimator.addUpdateListener(animation -> updateSearchProgress((Float) animation.getAnimatedValue()));
        if (!showSearch) {
            other.setVisibility(View.VISIBLE);
        } else {
            searchContainer.setVisibility(View.VISIBLE);
            AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
            updateCreateTopicButton(false);
        }
        searchAnimator.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                updateSearchProgress(showSearch ? 1f : 0);
                if (showSearch) {
                    other.setVisibility(View.GONE);
                } else {
                    AndroidUtilities.setAdjustResizeToNothing(getParentActivity(), classGuid);
                    searchContainer.setVisibility(View.GONE);
                    updateCreateTopicButton(true);
                }
            }
        });
        searchAnimator.setDuration(200);
        searchAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        searchAnimator.start();

        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors, true);
    }

    private void updateCreateTopicButton(boolean animated) {
        if (createTopicSubmenu == null) {
            return;
        }
        TLRPC.Chat chatLocal = getMessagesController().getChat(chatId);
        canShowCreateTopic = !ChatObject.isNotInChat(getMessagesController().getChat(chatId)) && ChatObject.canCreateTopic(chatLocal) && !searching && !opnendForSelect && !loadingTopics;
        createTopicSubmenu.setVisibility(canShowCreateTopic ? View.VISIBLE : View.GONE);
        hideFloatingButton(!canShowCreateTopic, animated);
    }

    private void updateSearchProgress(float value) {
        searchAnimationProgress = value;

        avatarContainer.getTitleTextView().setAlpha(1f - value);
        avatarContainer.getSubtitleTextView().setAlpha(1f - value);

        if (animateSearchWithScale) {
            float scale = 0.98f + 0.02f * (1f - searchAnimationProgress);
            recyclerListView.setScaleX(scale);
            recyclerListView.setScaleY(scale);
        }
    }

    private ArrayList<TLRPC.TL_forumTopic> getSelectedTopics() {
        ArrayList<TLRPC.TL_forumTopic> topics = new ArrayList<>();
        Iterator<Integer> iterator = selectedTopics.iterator();
        while (iterator.hasNext()) {
            int topicId = iterator.next();
            TLRPC.TL_forumTopic topic = topicsController.findTopic(chatId, topicId);
            if (topic != null) {
                topics.add(topic);
            }
        }
        return topics;
    }

    private void joinToGroup() {
        getMessagesController().addUserToChat(chatId, getUserConfig().getCurrentUser(), 0, null, this, false, () -> {
            joinRequested = false;
            updateChatInfo(true);
        }, e -> {
            if (e != null && "INVITE_REQUEST_SENT".equals(e.text)) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                preferences.edit().putLong("dialog_join_requested_time_" + -chatId, System.currentTimeMillis()).commit();
                JoinGroupAlert.showBulletin(getContext(), this, ChatObject.isChannelAndNotMegaGroup(getCurrentChat()));
                updateChatInfo(true);
                return false;
            }
            return true;
        });
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.closeSearchByActiveAction);
        updateChatInfo();
    }

    private void clearSelectedTopics() {
        selectedTopics.clear();
        actionBar.hideActionMode();
        AndroidUtilities.updateVisibleRows(recyclerListView);
    }

    private void toggleSelection(View view) {
        if (view instanceof TopicDialogCell) {
            TopicDialogCell cell = (TopicDialogCell) view;
            int id = cell.forumTopic.id;
            if (!selectedTopics.remove(id)) {
                selectedTopics.add(id);
            }
            cell.setChecked(selectedTopics.contains(id), true);

            TLRPC.Chat currentChat = getMessagesController().getChat(chatId);

            if (selectedTopics.size() > 0) {
                chekActionMode();
                actionBar.showActionMode(true);
                Iterator<Integer> iterator = selectedTopics.iterator();
                int unreadCount = 0, readCount = 0;
                int canPinCount = 0, canUnpinCount = 0;
                int canMuteCount = 0, canUnmuteCount = 0;
                while (iterator.hasNext()) {
                    int topicId = iterator.next();
                    TLRPC.TL_forumTopic topic = topicsController.findTopic(chatId, topicId);
                    if (topic != null) {
                        if (topic.unread_count != 0) {
                            unreadCount++;
                        } else {
                            readCount++;
                        }
                        if (ChatObject.canManageTopic(currentAccount, currentChat, topic)) {
                            if (topic.pinned) {
                                canUnpinCount++;
                            } else {
                                canPinCount++;
                            }
                        }
                    }
                    if (getMessagesController().isDialogMuted(-chatId, topicId)) {
                        canUnmuteCount++;
                    } else {
                        canMuteCount++;
                    }
                }

                if (unreadCount > 0) {
                    readItem.setVisibility(View.VISIBLE);
                    readItem.setTextAndIcon(LocaleController.getString("MarkAsRead", R.string.MarkAsRead), R.drawable.msg_markread);
                } else {
                    readItem.setVisibility(View.GONE);
                }
                if (canUnmuteCount != 0) {
                    mute = false;
                    muteItem.setIcon(R.drawable.msg_unmute);
                    muteItem.setContentDescription(LocaleController.getString("ChatsUnmute", R.string.ChatsUnmute));
                } else {
                    mute = true;
                    muteItem.setIcon(R.drawable.msg_mute);
                    muteItem.setContentDescription(LocaleController.getString("ChatsMute", R.string.ChatsMute));
                }

                pinItem.setVisibility(canPinCount == 1 && canUnpinCount == 0 ? View.VISIBLE : View.GONE);
                unpinItem.setVisibility(canUnpinCount == 1 && canPinCount == 0 ? View.VISIBLE : View.GONE);
            } else {
                actionBar.hideActionMode();
            }
            selectedDialogsCountTextView.setNumber(selectedTopics.size(), true);

            int canPin = 0;
            int canDeleteCount = 0;
            int closedTopicsCount = 0;
            int openTopicsCount = 0;
            Iterator<Integer> iterator = selectedTopics.iterator();
            while (iterator.hasNext()) {
                int topicId = iterator.next();
                TLRPC.TL_forumTopic topic = topicsController.findTopic(chatId, topicId);
                if (topic != null) {
                    if (ChatObject.canManageTopics(currentChat)) {
                        canDeleteCount++;
                    }
                    if (ChatObject.canManageTopic(currentAccount, currentChat, topic)) {
                        if (topic.closed) {
                            closedTopicsCount++;
                        } else {
                            openTopicsCount++;
                        }
                    }
                }
            }
            closeTopic.setVisibility(closedTopicsCount == 0 && openTopicsCount > 0 ? View.VISIBLE : View.GONE);
            closeTopic.setText(openTopicsCount > 1 ? LocaleController.getString("CloseTopics", R.string.CloseTopics) : LocaleController.getString("CloseTopic", R.string.CloseTopic));
            restartTopic.setVisibility(openTopicsCount == 0 && closedTopicsCount > 0 ? View.VISIBLE : View.GONE);
            restartTopic.setText(closedTopicsCount > 1 ? LocaleController.getString("RestartTopics", R.string.RestartTopics) : LocaleController.getString("RestartTopic", R.string.RestartTopic));
            deleteItem.setVisibility(canDeleteCount == selectedTopics.size() ? View.VISIBLE : View.GONE);

            otherItem.checkHideMenuItem();
        }
    }

    private void chekActionMode() {
        if (actionBar.actionModeIsExist(null)) {
            return;
        }
        final ActionBarMenu actionMode = actionBar.createActionMode(false, null);

        selectedDialogsCountTextView = new NumberTextView(actionMode.getContext());
        selectedDialogsCountTextView.setTextSize(18);
        selectedDialogsCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedDialogsCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionMode.addView(selectedDialogsCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        selectedDialogsCountTextView.setOnTouchListener((v, event) -> true);

        pinItem = actionMode.addItemWithWidth(pin_id, R.drawable.msg_pin, AndroidUtilities.dp(54));
        unpinItem = actionMode.addItemWithWidth(unpin_id, R.drawable.msg_unpin, AndroidUtilities.dp(54));
        muteItem = actionMode.addItemWithWidth(mute_id, R.drawable.msg_mute, AndroidUtilities.dp(54));
        deleteItem = actionMode.addItemWithWidth(delete_id, R.drawable.msg_delete, AndroidUtilities.dp(54), LocaleController.getString("Delete", R.string.Delete));

        otherItem = actionMode.addItemWithWidth(0, R.drawable.ic_ab_other, AndroidUtilities.dp(54), LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
        readItem = otherItem.addSubItem(read_id, R.drawable.msg_markread, LocaleController.getString("MarkAsRead", R.string.MarkAsRead));
        closeTopic = otherItem.addSubItem(close_topic_id, R.drawable.msg_topic_close, LocaleController.getString("CloseTopic", R.string.CloseTopic));
        restartTopic = otherItem.addSubItem(restart_topic_id, R.drawable.msg_topic_restart, LocaleController.getString("RestartTopic", R.string.RestartTopic));
    }

    private void updateChatInfo() {
        updateChatInfo(false);
    }

    private void updateChatInfo(boolean forceAnimate) {
        if (fragmentView == null) {
            return;
        }
        TLRPC.Chat chatLocal = getMessagesController().getChat(chatId);

        avatarContainer.setChatAvatar(chatLocal);
        if (!opnendForSelect) {
            if (chatLocal != null) {
                avatarContainer.setTitle(chatLocal.title);
            }
            updateSubtitle();
        } else {
            if (openedForForward) {
                avatarContainer.setTitle(LocaleController.getString("ForwardTo", R.string.ForwardTo));
            } else {
                avatarContainer.setTitle(LocaleController.getString("SelectTopic", R.string.SelectTopic));
            }
            searchItem.setVisibility(View.GONE);
            if (avatarContainer != null && avatarContainer.getLayoutParams() != null) {
                ((ViewGroup.MarginLayoutParams) avatarContainer.getLayoutParams()).rightMargin = AndroidUtilities.dp(searchItem.getVisibility() == View.VISIBLE ? 86 : 40);
            }
            avatarContainer.updateSubtitle();
            avatarContainer.getSubtitleTextView().setVisibility(View.GONE);
        }
        boolean animated = fragmentBeginToShow || forceAnimate;
        boolean bottomPannelVisibleLocal;
        long requestedTime = MessagesController.getNotificationsSettings(currentAccount).getLong("dialog_join_requested_time_" + -chatId, -1);
        if (chatLocal != null && ChatObject.isNotInChat(chatLocal) && (requestedTime > 0 && System.currentTimeMillis() - requestedTime < 1000 * 60 * 2)) {
            bottomPannelVisibleLocal = true;
            recyclerListView.setPadding(0, AndroidUtilities.dp(100), 0, AndroidUtilities.dp(51));

            bottomOverlayChatText.setText(LocaleController.getString("ChannelJoinRequestSent", R.string.ChannelJoinRequestSent), animated);
            bottomOverlayChatText.setEnabled(false);
            AndroidUtilities.updateViewVisibilityAnimated(bottomOverlayProgress, false, 0.5f, animated);
            AndroidUtilities.updateViewVisibilityAnimated(bottomOverlayChatText, true, 0.5f, animated);
        } else if (chatLocal != null && !opnendForSelect && (ChatObject.isNotInChat(chatLocal) || getMessagesController().isJoiningChannel(chatLocal.id))) {
            bottomPannelVisibleLocal = true;

            recyclerListView.setPadding(0, AndroidUtilities.dp(100), 0, AndroidUtilities.dp(51));
            boolean showProgress = false;
            if (getMessagesController().isJoiningChannel(chatLocal.id)) {
                showProgress = true;
            } else {
                if (chatLocal.join_request) {
                    bottomOverlayChatText.setText(LocaleController.getString("ChannelJoinRequest", R.string.ChannelJoinRequest));
                } else {
                    bottomOverlayChatText.setText(LocaleController.getString("ChannelJoin", R.string.ChannelJoin));
                }
                bottomOverlayChatText.setClickable(true);
                bottomOverlayChatText.setEnabled(true);
            }

            AndroidUtilities.updateViewVisibilityAnimated(bottomOverlayProgress, showProgress, 0.5f, animated);
            AndroidUtilities.updateViewVisibilityAnimated(bottomOverlayChatText, !showProgress, 0.5f, animated);
        } else {
            bottomPannelVisibleLocal = false;
            recyclerListView.setPadding(0, AndroidUtilities.dp(100), 0, 0);
        }

        if (bottomPannelVisible != bottomPannelVisibleLocal) {
            bottomPannelVisible = bottomPannelVisibleLocal;
            bottomOverlayContainer.animate().setListener(null).cancel();
            if (!animated) {
                bottomOverlayContainer.setVisibility(bottomPannelVisibleLocal ? View.VISIBLE : View.GONE);
                bottomOverlayContainer.setTranslationY(bottomPannelVisibleLocal ? 0 : AndroidUtilities.dp(53));
            } else {
                bottomOverlayContainer.animate().translationY(bottomPannelVisibleLocal ? 0 : AndroidUtilities.dp(53)).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!bottomPannelVisibleLocal) {
                            bottomOverlayContainer.setVisibility(View.GONE);
                        }
                    }
                });
            }
        }

        other.setVisibility(opnendForSelect ? View.GONE : View.VISIBLE);
        addMemberSubMenu.setVisibility(ChatObject.canAddUsers(chatLocal) ? View.VISIBLE : View.GONE);

        deleteChatSubmenu.setVisibility(chatLocal != null && !chatLocal.creator && !ChatObject.isNotInChat(chatLocal) ? View.VISIBLE : View.GONE);
        updateCreateTopicButton(true);
        groupCall = getMessagesController().getGroupCall(chatId, true);
    }

    private void updateSubtitle() {
        TLRPC.ChatFull chatFull = getMessagesController().getChatFull(chatId);
        if (this.chatFull != null && this.chatFull.participants != null) {
            chatFull.participants = this.chatFull.participants;
        }
        this.chatFull = chatFull;
        String newSubtitle;
        if (chatFull != null) {
            newSubtitle = LocaleController.formatPluralString("Members", chatFull.participants_count);
        } else {
            newSubtitle = LocaleController.getString("Loading", R.string.Loading).toLowerCase();
        }

        avatarContainer.setSubtitle(newSubtitle);
    }

    @Override
    protected ActionBar createActionBar(Context context) {
        return super.createActionBar(context);
    }

    private static HashSet<Long> settingsPreloaded = new HashSet<>();

    @Override
    public boolean onFragmentCreate() {
        getMessagesController().loadFullChat(chatId, 0, true);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.topicsDidLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.dialogsNeedReload);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.groupCallUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.notificationsSettingsUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatSwithcedToForum);

        updateTopicsList(false, false);
        SelectAnimatedEmojiDialog.preload(currentAccount);

        TLRPC.Chat chatLocal = getMessagesController().getChat(chatId);
        if (ChatObject.isChannel(chatLocal)) {
            getMessagesController().startShortPoll(chatLocal, classGuid, false);
        }
        //TODO remove when server start send in get diff
        if (!settingsPreloaded.contains(chatId)) {
            settingsPreloaded.add(chatId);
            TLRPC.TL_account_getNotifyExceptions exceptionsReq = new TLRPC.TL_account_getNotifyExceptions();
            exceptionsReq.peer = new TLRPC.TL_inputNotifyPeer();
            exceptionsReq.flags |= 1;
            ((TLRPC.TL_inputNotifyPeer) exceptionsReq.peer).peer = getMessagesController().getInputPeer(-chatId);
            getConnectionsManager().sendRequest(exceptionsReq, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.TL_updates) {
                    TLRPC.TL_updates updates = (TLRPC.TL_updates) response;
                    getMessagesController().processUpdates(updates, false);
                }
            }));
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().onAnimationFinish(transitionAnimationIndex);
        NotificationCenter.getGlobalInstance().onAnimationFinish(transitionAnimationGlobalIndex);

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.topicsDidLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.dialogsNeedReload);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.groupCallUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.notificationsSettingsUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatSwithcedToForum);

        TLRPC.Chat chatLocal = getMessagesController().getChat(chatId);
        if (ChatObject.isChannel(chatLocal)) {
            getMessagesController().startShortPoll(chatLocal, classGuid, true);
        }
        super.onFragmentDestroy();
    }


    private void updateTopicsList(boolean animated, boolean enalbeEnterAnimation) {
        if (!animated && (updateAnimated || itemAnimator != null && (System.currentTimeMillis() - lastAnimatedDuration) < itemAnimator.getMoveDuration())) {
            animated = true;
        }
        if (animated) {
            lastAnimatedDuration = System.currentTimeMillis();
        }
        updateAnimated = false;
        ArrayList<TLRPC.TL_forumTopic> topics = topicsController.getTopics(chatId);
        if (topics != null) {
            int oldCount = forumTopics.size();
            forumTopics.clear();
            for (int i = 0; i < topics.size(); i++) {
                if (excludeTopics != null && excludeTopics.contains(topics.get(i).id)) {
                    continue;
                }
                forumTopics.add(topics.get(i));
            }
            if (forumTopics.size() == 1 && forumTopics.get(0).id == 1) {
                forumTopics.clear();
            }

            for (int i = 0; i < forumTopics.size(); ++i) {
                if (forumTopics.get(i).pinned) {
                    forumTopics.add(0, forumTopics.remove(i));
                    break;
                }
            }
            if (recyclerListView != null && recyclerListView.getItemAnimator() != (animated ? itemAnimator : null)) {
                recyclerListView.setItemAnimator(animated ? itemAnimator : null);
            }

            if (adapter != null) {
                adapter.notifyDataSetChanged(true);
            }

            int newCount = forumTopics.size();
            if (fragmentBeginToShow && enalbeEnterAnimation && newCount > oldCount) {
                itemsEnterAnimator.showItemsAnimated(oldCount + 1);
            }

            if (scrollToTop && layoutManager != null) {
                layoutManager.scrollToPosition(0);
                scrollToTop = false;
            }
        }

        checkLoading();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.participants != null && this.chatFull != null) {
                this.chatFull.participants = chatFull.participants;
            }
            if (chatFull.id == chatId) {
                updateChatInfo();
                if (pendingRequestsDelegate != null) {
                    pendingRequestsDelegate.setChatInfo(chatFull, true);
                }
            }
        } else if (id == NotificationCenter.topicsDidLoaded) {
            Long chatId = (Long) args[0];
            if (this.chatId == chatId) {
                updateTopicsList(false, true);
                if (args.length > 1 && (Boolean) args[1]) {
                    checkForLoadMore();
                }
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if (mask == MessagesController.UPDATE_MASK_CHAT) {
                updateChatInfo();
            }
            if ((mask & MessagesController.UPDATE_MASK_SELECT_DIALOG) > 0) {
                updateTopicsList(false, false);
            }
        } else if (id == NotificationCenter.dialogsNeedReload) {
            updateTopicsList(false, false);
        } else if (id == NotificationCenter.groupCallUpdated) {
            Long chatId = (Long) args[0];
            if (this.chatId == chatId) {
                groupCall = getMessagesController().getGroupCall(chatId, false);
                if (fragmentContextView != null) {
                    fragmentContextView.checkCall(true);
                }
            }
        } else if (id == NotificationCenter.notificationsSettingsUpdated) {
            updateTopicsList(false, false);
        } else if (id == NotificationCenter.chatSwithcedToForum) {

        }
    }

    private void checkForLoadMore() {
        if (topicsController.endIsReached(chatId) || layoutManager == null) {
            return;
        }
        int lastPosition = layoutManager.findLastVisibleItemPosition();
        if (forumTopics.isEmpty() || lastPosition >= adapter.getItemCount() - 5) {
            topicsController.loadTopics(chatId);
        }
        checkLoading();
    }

    public void setExcludeTopics(HashSet<Integer> exceptionsTopics) {
        this.excludeTopics = exceptionsTopics;
    }

    @Override
    public ChatObject.Call getGroupCall() {
        return groupCall != null && groupCall.call instanceof TLRPC.TL_groupCall ? groupCall : null;
    }

    @Override
    public TLRPC.Chat getCurrentChat() {
        return getMessagesController().getChat(chatId);
    }

    @Override
    public long getDialogId() {
        return -chatId;
    }

    public void setForwardFromDialogFragment(DialogsActivity dialogsActivity) {
        this.dialogsActivity = dialogsActivity;
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TopicDialogCell dialogCell = new TopicDialogCell(null, parent.getContext(), true, false);
            return new RecyclerListView.Holder(dialogCell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            TLRPC.TL_forumTopic topic = forumTopics.get(position);
            TLRPC.TL_forumTopic nextTopic = null;
            if (position + 1 < forumTopics.size()) {
                nextTopic = forumTopics.get(position + 1);
            }
            TopicDialogCell dialogCell = (TopicDialogCell) holder.itemView;

            TLRPC.Message tlMessage = topic.topMessage;
            int oldId = dialogCell.forumTopic == null ? 0 : dialogCell.forumTopic.id;
            int newId = topic.id;
            boolean animated = oldId == newId && dialogCell.position == position;
            if (tlMessage != null) {
                MessageObject messageObject = new MessageObject(currentAccount, tlMessage, false, false);

                dialogCell.setForumTopic(topic, -chatId, messageObject, animated);
                dialogCell.drawDivider = position != forumTopics.size() - 1;
                dialogCell.fullSeparator = topic.pinned && (nextTopic == null || !nextTopic.pinned);
                dialogCell.setPinForced(topic.pinned);
                dialogCell.position = position;
            }

            dialogCell.setTopicIcon(topic);

            dialogCell.setChecked(selectedTopics.contains(newId), animated);
        }

        @Override
        public int getItemCount() {
            return forumTopics.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        private ArrayList<Integer> hashes = new ArrayList<>();

        @Override
        public void notifyDataSetChanged() {
            hashes.clear();
            for (int i = 0; i < forumTopics.size(); ++i) {
                hashes.add(forumTopics.get(i).id);
            }
            super.notifyDataSetChanged();
        }

        public void notifyDataSetChanged(boolean diff) {
            final ArrayList<Integer> oldHashes = new ArrayList<>(hashes);
            hashes.clear();
            for (int i = 0; i < forumTopics.size(); ++i) {
                hashes.add(forumTopics.get(i).id);
            }

            if (diff) {
                DiffUtil.calculateDiff(new DiffUtil.Callback() {
                    @Override
                    public int getOldListSize() {
                        return oldHashes.size();
                    }

                    @Override
                    public int getNewListSize() {
                        return hashes.size();
                    }

                    @Override
                    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                        return Objects.equals(hashes.get(newItemPosition), oldHashes.get(oldItemPosition));
                    }

                    @Override
                    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                        return false;
                    }
                }).dispatchUpdatesTo(this);
            } else {
                super.notifyDataSetChanged();
            }
        }
    }

    private class TopicDialogCell extends DialogCell {

        public boolean drawDivider;
        public int position = -1;

        public TopicDialogCell(DialogsActivity fragment, Context context, boolean needCheck, boolean forceThreeLines) {
            super(fragment, context, needCheck, forceThreeLines);
            drawAvatar = false;
            messagePaddingStart = 50;
            chekBoxPaddingTop = 24;
            heightDefault = 64;
            heightThreeLines = 76;
        }

        private AnimatedEmojiDrawable animatedEmojiDrawable;
        private Drawable forumIcon;
        boolean attached;
        private boolean closed;

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.drawColor(getThemedColor(Theme.key_windowBackgroundWhite));
            canvas.translate(0, translateY = -AndroidUtilities.dp(4));
            super.onDraw(canvas);
            canvas.restore();
            if (drawDivider) {
                int left = fullSeparator ? 0 : AndroidUtilities.dp(messagePaddingStart);
                if (LocaleController.isRTL) {
                    canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth() - left, getMeasuredHeight() - 1, Theme.dividerPaint);
                } else {
                    canvas.drawLine(left, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
                }
            }
            if (animatedEmojiDrawable != null || forumIcon != null) {
                int padding = AndroidUtilities.dp(10);
                int paddingTop = AndroidUtilities.dp(10);
                int size = AndroidUtilities.dp(28);
                if (animatedEmojiDrawable != null) {
                    if (LocaleController.isRTL) {
                        animatedEmojiDrawable.setBounds(getWidth() - padding - size, paddingTop, getWidth() - padding, paddingTop + size);
                    } else {
                        animatedEmojiDrawable.setBounds(padding, paddingTop, padding + size, paddingTop + size);
                    }
                    animatedEmojiDrawable.draw(canvas);
                } else {
                    if (LocaleController.isRTL) {
                        forumIcon.setBounds(getWidth() - padding - size, paddingTop, getWidth() - padding, paddingTop + size);
                    } else {
                        forumIcon.setBounds(padding, paddingTop, padding + size, paddingTop + size);
                    }
                    forumIcon.draw(canvas);
                }
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            if (animatedEmojiDrawable != null) {
                animatedEmojiDrawable.addView(this);
            }

        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            attached = false;
            if (animatedEmojiDrawable != null) {
                animatedEmojiDrawable.removeView(this);
            }

        }

        public void setAnimatedEmojiDrawable(AnimatedEmojiDrawable animatedEmojiDrawable) {
            if (this.animatedEmojiDrawable == animatedEmojiDrawable) {
                return;
            }
            if (this.animatedEmojiDrawable != null && attached) {
                this.animatedEmojiDrawable.removeView(this);
            }
            this.animatedEmojiDrawable = animatedEmojiDrawable;
            if (animatedEmojiDrawable != null && attached) {
                animatedEmojiDrawable.addView(this);
            }
        }

        public void setForumIcon(Drawable drawable) {
            forumIcon = drawable;
        }

        public void setTopicIcon(TLRPC.TL_forumTopic topic) {
            closed = topic != null && topic.closed;
            if (topic != null && topic.icon_emoji_id != 0) {
                setForumIcon(null);
                setAnimatedEmojiDrawable(new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_FORUM_TOPIC, currentAccount, topic.icon_emoji_id));
            } else {
                setAnimatedEmojiDrawable(null);
                setForumIcon(ForumUtilities.createTopicDrawable(topic));
            }
            buildLayout();
        }

        @Override
        protected boolean drawLock2() {
            return closed;
        }
    }

    private void hideFloatingButton(boolean hide, boolean animated) {
        if (floatingHidden == hide) {
            return;
        }
        floatingHidden = hide;
        boolean animatedLocal = fragmentBeginToShow && animated;
        if (animatedLocal) {
            AnimatorSet animatorSet = new AnimatorSet();
            ValueAnimator valueAnimator = ValueAnimator.ofFloat(floatingButtonHideProgress, floatingHidden ? 1f : 0f);
            valueAnimator.addUpdateListener(animation -> {
                floatingButtonHideProgress = (float) animation.getAnimatedValue();
                floatingButtonTranslation = AndroidUtilities.dp(100) * floatingButtonHideProgress;
                updateFloatingButtonOffset();
            });
            animatorSet.playTogether(valueAnimator);
            animatorSet.setDuration(300);
            animatorSet.setInterpolator(floatingInterpolator);
            animatorSet.start();
        } else {
            floatingButtonHideProgress = floatingHidden ? 1f : 0f;
            floatingButtonTranslation = AndroidUtilities.dp(100) * floatingButtonHideProgress;
            updateFloatingButtonOffset();
        }
        floatingButtonContainer.setClickable(!hide);
    }

    private void updateFloatingButtonOffset() {
        floatingButtonContainer.setTranslationY(floatingButtonTranslation);
    }

    @Override
    public void onBecomeFullyHidden() {
        if (actionBar != null) {
            actionBar.closeSearchField();
        }
    }

    private class EmptyViewContainer extends FrameLayout {

        TextView textView;

        public EmptyViewContainer(Context context) {
            super(context);
            textView = new TextView(context);
            SpannableStringBuilder spannableStringBuilder;
            if (LocaleController.isRTL) {
                spannableStringBuilder = new SpannableStringBuilder("  ");
                spannableStringBuilder.setSpan(new ColoredImageSpan(R.drawable.attach_arrow_left), 0, 1, 0);
                spannableStringBuilder.append(LocaleController.getString("TapToCreateTopicHint", R.string.TapToCreateTopicHint));
            } else {
                spannableStringBuilder = new SpannableStringBuilder(LocaleController.getString("TapToCreateTopicHint", R.string.TapToCreateTopicHint));
                spannableStringBuilder.append("  ");
                spannableStringBuilder.setSpan(new ColoredImageSpan(R.drawable.attach_arrow_right), spannableStringBuilder.length() - 1, spannableStringBuilder.length(), 0);
            }
            textView.setText(spannableStringBuilder);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setLayerType(LAYER_TYPE_HARDWARE, null);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, getResourceProvider()));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 86, 0, 86, 32));
        }

        float progress;
        boolean increment;

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (increment) {
                progress += 16 / 1200f;
                if (progress > 1) {
                    increment = false;
                    progress = 1;
                }
            } else {
                progress -= 16 / 1200f;
                if (progress < 0) {
                    increment = true;
                    progress = 0;
                }
            }
            textView.setTranslationX(CubicBezierInterpolator.DEFAULT.getInterpolation(progress) * AndroidUtilities.dp(8) * (LocaleController.isRTL ? -1 : 1));
            invalidate();
        }
    }

    @Override
    public boolean isLightStatusBar() {
        if (searchingNotEmpty) {
            int color = Theme.getColor(Theme.key_windowBackgroundWhite);
            return ColorUtils.calculateLuminance(color) > 0.7f;
        } else {
            return super.isLightStatusBar();
        }
    }

    private class SearchContainer extends FrameLayout {

        RecyclerListView recyclerView;
        LinearLayoutManager layoutManager;
        SearchAdapter searchAdapter;
        Runnable searchRunnable;
        String searchString = "empty";

        ArrayList<TLRPC.TL_forumTopic> searchResultTopics = new ArrayList<>();
        ArrayList<MessageObject> searchResultMessages = new ArrayList<>();

        int topicsHeaderRow;
        int topicsStartRow;
        int topicsEndRow;

        int messagesHeaderRow;
        int messagesStartRow;
        int messagesEndRow;

        int rowCount;

        boolean isLoading;
        boolean canLoadMore;

        FlickerLoadingView flickerLoadingView;
        StickerEmptyView emptyView;
        RecyclerItemsEnterAnimator itemsEnterAnimator;
        boolean messagesIsLoading;

        public SearchContainer(@NonNull Context context) {
            super(context);
            recyclerView = new RecyclerListView(context);
            recyclerView.setAdapter(searchAdapter = new SearchAdapter());
            recyclerView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
            recyclerView.setOnItemClickListener((view, position) -> {
                if (view instanceof TopicSearchCell) {
                    TopicSearchCell cell = (TopicSearchCell) view;
                    ForumUtilities.openTopic(TopicsFragment.this, chatId, cell.getTopic(), 0);
                } else if (view instanceof TopicDialogCell) {
                    TopicDialogCell cell = (TopicDialogCell) view;
                    ForumUtilities.openTopic(TopicsFragment.this, chatId, cell.forumTopic, cell.getMessageId());
                }
            });
            recyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    if (canLoadMore) {
                        int lastPosition = layoutManager.findLastVisibleItemPosition();
                        if (lastPosition + 5 >= rowCount) {
                            loadMessages(searchString);
                        }
                    }

                    if (searching && (dx != 0 || dy != 0)) {
                        AndroidUtilities.hideKeyboard(searchItem.getSearchField());
                    }
                }
            });

            flickerLoadingView = new FlickerLoadingView(context);
            flickerLoadingView.setViewType(FlickerLoadingView.DIALOG_CELL_TYPE);
            flickerLoadingView.showDate(false);
            flickerLoadingView.setUseHeaderOffset(true);

            emptyView = new StickerEmptyView(context, flickerLoadingView, StickerEmptyView.STICKER_TYPE_SEARCH);
            emptyView.title.setText(LocaleController.getString("NoResult", R.string.NoResult));
            emptyView.subtitle.setVisibility(View.GONE);
            emptyView.setVisibility(View.GONE);
            emptyView.addView(flickerLoadingView, 0);
            emptyView.setAnimateLayoutChange(true);

            recyclerView.setEmptyView(emptyView);
            recyclerView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);
            addView(emptyView);
            addView(recyclerView);
            updateRows();

            itemsEnterAnimator = new RecyclerItemsEnterAnimator(recyclerView, true);
            recyclerView.setItemsEnterAnimator(itemsEnterAnimator);
        }

        public void setSearchString(String searchString) {
            if (this.searchString.equals(searchString)) {
                return;
            }
            this.searchString = searchString;
            if (searchRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                searchRunnable = null;
            }

            AndroidUtilities.updateViewVisibilityAnimated(searchContainer, !TextUtils.isEmpty(searchString), 1f, true);

            messagesIsLoading = false;
            canLoadMore = false;
            searchResultTopics.clear();
            searchResultMessages.clear();
            updateRows();
            if (TextUtils.isEmpty(searchString)) {
                isLoading = false;
                // emptyView.showProgress(true, true);
                return;
            } else {
                updateRows();
            }

            isLoading = true;
            emptyView.showProgress(isLoading, true);


            searchRunnable = () -> {
                String searchTrimmed = searchString.trim().toLowerCase();
                ArrayList<TLRPC.TL_forumTopic> topics = new ArrayList<>();
                for (int i = 0; i < forumTopics.size(); i++) {
                    if (forumTopics.get(i).title.toLowerCase().contains(searchTrimmed)) {
                        topics.add(forumTopics.get(i));
                        forumTopics.get(i).searchQuery = searchTrimmed;
                    }
                }

                searchResultTopics.clear();
                searchResultTopics.addAll(topics);
                updateRows();

                if (!searchResultTopics.isEmpty()) {
                    isLoading = false;
                    //   emptyView.showProgress(isLoading, true);
                    itemsEnterAnimator.showItemsAnimated(0);
                }

                loadMessages(searchString);
            };
            AndroidUtilities.runOnUIThread(searchRunnable, 200);
        }

        private void loadMessages(String searchString) {
            if (messagesIsLoading) {
                return;
            }
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.peer = getMessagesController().getInputPeer(-chatId);
            req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
            req.limit = 20;
            req.q = searchString;
            if (!searchResultMessages.isEmpty()) {
                req.offset_id = searchResultMessages.get(searchResultMessages.size() - 1).getId();
            }
            messagesIsLoading = true;
//            if (query.equals(lastMessagesSearchString) && !searchResultMessages.isEmpty()) {
//                MessageObject lastMessage = searchResultMessages.get(searchResultMessages.size() - 1);
//                req.offset_id = lastMessage.getId();
//                req.offset_rate = nextSearchRate;
//                long id = MessageObject.getPeerId(lastMessage.messageOwner.peer_id);
//                req.offset_peer = MessagesController.getInstance(currentAccount).getInputPeer(id);
//            } else {
//                req.offset_rate = 0;
//                req.offset_id = 0;
//                req.offset_peer = new TLRPC.TL_inputPeerEmpty();
//            }
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (searchString.equals(this.searchString)) {
                    int oldRowCount = rowCount;
                    messagesIsLoading = false;
                    isLoading = false;
                    if (response instanceof TLRPC.messages_Messages) {
                        TLRPC.messages_Messages messages = (TLRPC.messages_Messages) response;

                        for (int i = 0; i < messages.messages.size(); i++) {
                            TLRPC.Message message = messages.messages.get(i);
                            MessageObject messageObject = new MessageObject(currentAccount, message, false, false);
                            messageObject.setQuery(searchString);
                            searchResultMessages.add(messageObject);
                        }
                        updateRows();
                        canLoadMore = searchResultMessages.size() < messages.count && !messages.messages.isEmpty();
                    } else {
                        canLoadMore = false;
                    }

                    if (rowCount == 0) {
                        emptyView.showProgress(isLoading, true);
                    }
                    itemsEnterAnimator.showItemsAnimated(oldRowCount);
                }
            }));
        }

        private void updateRows() {
            topicsHeaderRow = -1;
            topicsStartRow = -1;
            topicsEndRow = -1;
            messagesHeaderRow = -1;
            messagesStartRow = -1;
            messagesEndRow = -1;

            rowCount = 0;

            if (!searchResultTopics.isEmpty()) {
                topicsHeaderRow = rowCount++;
                topicsStartRow = rowCount;
                rowCount += searchResultTopics.size();
                topicsEndRow = rowCount;
            }

            if (!searchResultMessages.isEmpty()) {
                messagesHeaderRow = rowCount++;
                messagesStartRow = rowCount;
                rowCount += searchResultMessages.size();
                messagesEndRow = rowCount;
            }

            searchAdapter.notifyDataSetChanged();
        }

        private class SearchAdapter extends RecyclerListView.SelectionAdapter {

            private final static int VIEW_TYPE_HEADER = 1;
            private final static int VIEW_TYPE_TOPIC = 2;
            private final static int VIEW_TYPE_MESSAGE = 3;

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view;
                switch (viewType) {
                    case VIEW_TYPE_HEADER:
                        view = new GraySectionCell(parent.getContext());
                        break;
                    case VIEW_TYPE_TOPIC:
                        view = new TopicSearchCell(parent.getContext());
                        break;
                    case VIEW_TYPE_MESSAGE:
                        view = new TopicDialogCell(null, parent.getContext(), false, true);
                        break;
                    default:
                        throw new RuntimeException("unsupported view type");
                }


                view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (getItemViewType(position) == VIEW_TYPE_HEADER) {
                    GraySectionCell headerCell = (GraySectionCell) holder.itemView;
                    if (position == topicsHeaderRow) {
                        headerCell.setText(LocaleController.getString("Topics", R.string.Topics));
                    }
                    if (position == messagesHeaderRow) {
                        headerCell.setText(LocaleController.getString("SearchMessages", R.string.SearchMessages));
                    }
                }
                if (getItemViewType(position) == VIEW_TYPE_TOPIC) {
                    TLRPC.TL_forumTopic topic = searchResultTopics.get(position - topicsStartRow);
                    TopicSearchCell topicSearchCell = (TopicSearchCell) holder.itemView;
                    topicSearchCell.setTopic(topic);
                    topicSearchCell.drawDivider = position != topicsEndRow - 1;
                }
                if (getItemViewType(position) == VIEW_TYPE_MESSAGE) {
                    MessageObject message = searchResultMessages.get(position - messagesStartRow);
                    TopicDialogCell dialogCell = (TopicDialogCell) holder.itemView;
                    dialogCell.drawDivider = position != messagesEndRow - 1;
                    int topicId = MessageObject.getTopicId(message.messageOwner);
                    if (topicId == 0) {
                        topicId = 1;
                    }
                    TLRPC.TL_forumTopic topic = topicsController.findTopic(chatId, topicId);
                    if (topic == null) {
                        FileLog.d("cant find topic " + topicId);
                    } else {
                        dialogCell.setForumTopic(topic, message.getDialogId(), message, false);
                        dialogCell.setPinForced(topic.pinned);
                        dialogCell.setTopicIcon(topic);
                    }
                }
            }

            @Override
            public int getItemViewType(int position) {
                if (position == messagesHeaderRow || position == topicsHeaderRow) {
                    return VIEW_TYPE_HEADER;
                }
                if (position >= topicsStartRow && position < topicsEndRow) {
                    return VIEW_TYPE_TOPIC;
                }
                if (position >= messagesStartRow && position < messagesEndRow) {
                    return VIEW_TYPE_MESSAGE;
                }
                return 0;
            }

            @Override
            public int getItemCount() {
                if (isLoading) {
                    return 0;
                }
                return rowCount;
            }

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return holder.getItemViewType() == VIEW_TYPE_MESSAGE || holder.getItemViewType() == VIEW_TYPE_TOPIC;
            }
        }
    }

    public void setOnTopicSelectedListener(OnTopicSelectedListener listener) {
        onTopicSelectedListener = listener;
    }

    public interface OnTopicSelectedListener {
        void onTopicSelected(TLRPC.TL_forumTopic topic);
    }

    @Override
    public void onResume() {
        super.onResume();
        getMessagesController().getTopicsController().onTopicFragmentResume(chatId);
        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return bottomOverlayChatText != null && bottomOverlayChatText.getVisibility() == View.VISIBLE ? bottomOverlayChatText.getMeasuredHeight() : 0;
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        getMessagesController().getTopicsController().onTopicFragmentPause(chatId);
        Bulletin.removeDelegate(this);
    }

    @Override
    public void prepareFragmentToSlide(boolean topFragment, boolean beginSlide) {
        if (!topFragment && beginSlide) {
            isSlideBackTransition = true;
            setFragmentIsSliding(true);
        } else {
            slideBackTransitionAnimator = null;
            isSlideBackTransition = false;
            setFragmentIsSliding(false);
            setSlideTransitionProgress(1f);
        }
    }

    private void setFragmentIsSliding(boolean sliding) {
        if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
            return;
        }
        ViewGroup v = contentView;
        if (v != null) {
            if (sliding) {
                v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                v.setClipChildren(false);
                v.setClipToPadding(false);
            } else {
                v.setLayerType(View.LAYER_TYPE_NONE, null);
                v.setClipChildren(true);
                v.setClipToPadding(true);
            }
        }
        contentView.requestLayout();
        actionBar.requestLayout();
    }

    @Override
    public void onSlideProgress(boolean isOpen, float progress) {
        if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
            return;
        }
        if (isSlideBackTransition && slideBackTransitionAnimator == null) {
            setSlideTransitionProgress(progress);
        }
    }

    private void setSlideTransitionProgress(float progress) {
        if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
            return;
        }
        slideFragmentProgress = progress;
        if (fragmentView != null) {
            fragmentView.invalidate();
        }

        View v = recyclerListView;
        if (v != null) {
            float s = 1f - 0.05f * (1f - slideFragmentProgress);
            v.setPivotX(0);
            v.setPivotY(0);
            v.setScaleX(s);
            v.setScaleY(s);

            topView.setPivotX(0);
            topView.setPivotY(0);
            topView.setScaleX(s);
            topView.setScaleY(s);

            actionBar.setPivotX(0);
            actionBar.setPivotY(0);
            actionBar.setScaleX(s);
            actionBar.setScaleY(s);
        }
    }

    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);

        transitionAnimationIndex = getNotificationCenter().setAnimationInProgress(transitionAnimationIndex, new int[] {NotificationCenter.topicsDidLoaded});
        transitionAnimationGlobalIndex = NotificationCenter.getGlobalInstance().setAnimationInProgress(transitionAnimationGlobalIndex, new int[0]);
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (isOpen && blurredView != null) {
            if (blurredView.getParent() != null) {
                ((ViewGroup) blurredView.getParent()).removeView(blurredView);
            }
            blurredView.setBackground(null);
        }

        getNotificationCenter().onAnimationFinish(transitionAnimationIndex);
        NotificationCenter.getGlobalInstance().onAnimationFinish(transitionAnimationGlobalIndex);

        if (!isOpen && (opnendForSelect || removeFragmentOnTransitionEnd)) {
            removeSelfFromStack();
            if (dialogsActivity != null) {
                dialogsActivity.removeSelfFromStack();
            }
        }
    }

    @Override
    public void drawOverlay(Canvas canvas, View parent) {
        canvas.save();
        canvas.translate(contentView.getX(), contentView.getY());
        if (fragmentContextView != null && fragmentContextView.isCallStyle()) {
            float alpha = 1f;//(blurredView != null && blurredView.getVisibility() == View.VISIBLE) ? 1f - blurredView.getAlpha() : 1f;
            if (alpha > 0) {
                if (alpha == 1f) {
                    canvas.save();
                } else {
                    canvas.saveLayerAlpha(fragmentContextView.getX(), topView.getY() + fragmentContextView.getY() - AndroidUtilities.dp(30), fragmentContextView.getX() + fragmentContextView.getMeasuredWidth(), topView.getY() + fragmentContextView.getY() + fragmentContextView.getMeasuredHeight(), (int) (255 * alpha), Canvas.ALL_SAVE_FLAG);
                }
                canvas.translate(fragmentContextView.getX(), topView.getY() + fragmentContextView.getY());
                fragmentContextView.setDrawOverlay(true);
                fragmentContextView.draw(canvas);
                fragmentContextView.setDrawOverlay(false);
                canvas.restore();
            }
            parent.invalidate();
        }
        canvas.restore();
    }

    private void prepareBlurBitmap() {
        if (blurredView == null || parentLayout == null || SharedConfig.useLNavigation) {
            return;
        }
        int w = (int) (fragmentView.getMeasuredWidth() / 6.0f);
        int h = (int) (fragmentView.getMeasuredHeight() / 6.0f);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(1.0f / 6.0f, 1.0f / 6.0f);
        parentLayout.getView().draw(canvas);
        Utilities.stackBlurBitmap(bitmap, Math.max(7, Math.max(w, h) / 180));
        blurredView.setBackground(new BitmapDrawable(bitmap));
        blurredView.setAlpha(0.0f);
        if (blurredView.getParent() != null) {
            ((ViewGroup) blurredView.getParent()).removeView(blurredView);
        }
        parentLayout.getOverlayContainerView().addView(blurredView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    @Override
    public boolean onBackPressed() {
        if (!selectedTopics.isEmpty()) {
            clearSelectedTopics();
            return false;
        }
        return super.onBackPressed();
    }

    @Override
    public void onTransitionAnimationProgress(boolean isOpen, float progress) {
        if (blurredView != null && blurredView.getVisibility() == View.VISIBLE) {
            if (isOpen) {
                blurredView.setAlpha(1.0f - progress);
            } else {
                blurredView.setAlpha(progress);
            }
        }
    }
}
