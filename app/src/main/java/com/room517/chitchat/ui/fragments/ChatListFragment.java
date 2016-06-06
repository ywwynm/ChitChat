package com.room517.chitchat.ui.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.room517.chitchat.App;
import com.room517.chitchat.Event;
import com.room517.chitchat.R;
import com.room517.chitchat.db.ChatDao;
import com.room517.chitchat.helpers.NotificationHelper;
import com.room517.chitchat.model.Chat;
import com.room517.chitchat.model.ChatDetail;
import com.room517.chitchat.ui.adapters.ChatListAdapter;
import com.room517.chitchat.ui.dialogs.SimpleListDialog;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Created by ywwynm on 2016/5/24.
 * 对话列表Fragment
 */
public class ChatListFragment extends BaseFragment {

    public static ChatListFragment newInstance(Bundle args) {
        ChatListFragment chatListFragment = new ChatListFragment();
        chatListFragment.setArguments(args);
        return chatListFragment;
    }

    private ChatDao mChatDao;

    private LinearLayout mLlEmpty;
    private ScrollView mScrollView;

    private TextView[]        mTvChats;
    private CardView[]        mCvChats;
    private RecyclerView[]    mRvChats;
    private ChatListAdapter[] mAdapters;

    @Override
    protected int getLayoutRes() {
        return R.layout.fragment_chat_list;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.act_search) {
            mChatDao.updateChatsToNormal();
        }
        return true;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        EventBus.getDefault().register(this);
        super.init();
        return mContentView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        EventBus eventBus = EventBus.getDefault();
        for (ChatListAdapter adapter : mAdapters) {
            if (adapter != null) {
                eventBus.unregister(adapter);
            }
        }
        eventBus.unregister(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        NotificationManagerCompat manager = NotificationManagerCompat.from(getActivity());
        for (ChatListAdapter adapter : mAdapters) {
            if (adapter != null) {
                for (Chat chat : adapter.getChats()) {
                    String userId = chat.getUserId();
                    manager.cancel(userId.hashCode());
                    NotificationHelper.putUnreadCount(userId, 0);
                }
                adapter.notifyDataSetChanged();
            }
        }
        App.setWrChatList(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        App.setWrChatList(null);
    }

    @Override
    protected void initMember() {
        mChatDao = ChatDao.getInstance();

        mTvChats = new TextView[2];
        mCvChats = new CardView[2];
        mRvChats = new RecyclerView[2];
        mAdapters = new ChatListAdapter[2];
    }

    @Override
    protected void findViews() {
        mLlEmpty    = f(R.id.ll_empty_state_chat_list);
        mScrollView = f(R.id.sv_chat_list);

        mTvChats[0] = f(R.id.tv_chats_normal);
        mCvChats[0] = f(R.id.cv_chats_normal);
        mRvChats[0] = f(R.id.rv_chats_normal);
        mTvChats[1] = f(R.id.tv_chats_sticky);
        mCvChats[1] = f(R.id.cv_chats_sticky);
        mRvChats[1] = f(R.id.rv_chats_sticky);
    }

    @Override
    protected void initUI() {
        setVisibilities();

        if (!mChatDao.noChats()) {
            if (!mChatDao.noNormalChats()) {
                initChatList(Chat.TYPE_NORMAL);
            }
            if (!mChatDao.noStickyChats()) {
                initChatList(Chat.TYPE_STICKY);
            }
        }
    }

    private void setVisibilities() {
        if (mChatDao.noChats()) { // 没有任何聊天
            mLlEmpty.setVisibility(View.VISIBLE);
            mScrollView.setVisibility(View.GONE);
        } else {
            mLlEmpty.setVisibility(View.GONE);
            mScrollView.setVisibility(View.VISIBLE);

            if (mChatDao.noNormalChats()) { // 没有普通聊天
                mTvChats[0].setVisibility(View.GONE);
                mCvChats[0].setVisibility(View.GONE);
            } else {
                mTvChats[0].setVisibility(View.VISIBLE);
                mCvChats[0].setVisibility(View.VISIBLE);
            }

            if (mChatDao.noStickyChats()) { // 没有置顶聊天
                mTvChats[1].setVisibility(View.GONE);
                mCvChats[1].setVisibility(View.GONE);
            } else {
                mTvChats[1].setVisibility(View.VISIBLE);
                mCvChats[1].setVisibility(View.VISIBLE);
            }
        }
    }

    private void initChatList(@Chat.Type int type) {
        mAdapters[type] = new ChatListAdapter(
                getActivity(), sortChats(mChatDao.getChats(type)), type);
        mRvChats[type].setAdapter(mAdapters[type]);
        mRvChats[type].setLayoutManager(new LinearLayoutManager(getActivity()));
    }

    private List<Chat> sortChats(List<Chat> chats) {
        Collections.sort(chats, new Comparator<Chat>() {
            @Override
            public int compare(Chat c1, Chat c2) {
                ChatDetail cd1 = mChatDao.getLastChatDetail(c1.getUserId());
                ChatDetail cd2 = mChatDao.getLastChatDetail(c2.getUserId());
                Long t1 = cd1.getTime();
                Long t2 = cd2.getTime();
                return -t1.compareTo(t2);
            }
        });
        return chats;
    }

    @Override
    protected void setupEvents() {

    }

    @Subscribe
    public void onMessageReceived(Event.ReceiveMessage event) {
        onNewMessage(event.chatDetail);
    }

    @Subscribe
    public void onMessageSent(Event.SendMessage event) {
        onNewMessage(event.chatDetail);
    }

    private void onNewMessage(ChatDetail chatDetail) {
        setVisibilities();
        Chat chat = mChatDao.getChat(chatDetail, false);
        int type = chat.getType();
        if (mAdapters[type] == null) {
            initChatList(type);
            mAdapters[type].getUnreadCounts().set(0, 1);
            mAdapters[type].notifyItemChanged(0);
        }
    }

    @Subscribe
    public void onChatListLongClicked(Event.ChatListLongClick event) {
        Chat chat = event.chat;
        SimpleListDialog sld = new SimpleListDialog();

        List<String> items = new ArrayList<>();
        if (chat.getType() == Chat.TYPE_NORMAL) {
            items.add(getString(R.string.act_sticky_on_top));
        } else {
            items.add(getString(R.string.act_remove_from_top));
        }
        items.add(getString(R.string.act_delete));
        sld.setItems(items);

        List<View.OnClickListener> onItemClickListeners = new ArrayList<>();
        onItemClickListeners.add(getStickyListener(sld, chat));
        sld.setOnItemClickListeners(onItemClickListeners);

        sld.show(getActivity().getFragmentManager(), SimpleListDialog.class.getSimpleName());
    }

    private View.OnClickListener getStickyListener(final SimpleListDialog sld, final Chat chat) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String userId = chat.getUserId();
                int fromType = chat.getType();
                int toType = 1 - fromType;

                chat.setType(toType);

                HashMap<String, Object> infoMap= mAdapters[fromType].getInfoMap(userId);
                mAdapters[fromType].remove(userId);
                if (mAdapters[fromType].getUnreadCounts().isEmpty()) {
                    mTvChats [fromType].setVisibility(View.GONE);
                    mCvChats [fromType].setVisibility(View.GONE);
                }

                if (mAdapters[toType] == null) {
                    initChatList(toType);
                }
                mTvChats[toType].setVisibility(View.VISIBLE);
                mCvChats[toType].setVisibility(View.VISIBLE);
                mAdapters[toType].add(infoMap);

                mChatDao.updateChat(userId, toType);

                sld.dismiss();
            }
        };
    }
}
