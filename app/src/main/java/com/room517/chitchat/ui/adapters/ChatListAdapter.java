package com.room517.chitchat.ui.adapters;

import android.app.Activity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.room517.chitchat.Def;
import com.room517.chitchat.Event;
import com.room517.chitchat.R;
import com.room517.chitchat.db.ChatDao;
import com.room517.chitchat.db.UserDao;
import com.room517.chitchat.model.Chat;
import com.room517.chitchat.model.ChatDetail;
import com.room517.chitchat.model.User;
import com.room517.chitchat.utils.DateTimeUtil;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by ywwynm on 2016/5/30.
 * 用于主界面显示聊天列表
 */
public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ChatHolder> {

    @Chat.Type
    private int mType;

    private LayoutInflater mInflater;

    private List<Chat> mChats;
    private List<User> mUsers;
    private List<ChatDetail> mLastChatDetails;
    private List<Integer> mUnreadCounts;

    public ChatListAdapter(Activity activity, List<Chat> chats, @Chat.Type int type) {
        mInflater = LayoutInflater.from(activity);

        int size = chats.size();
        mUsers  = new ArrayList<>(size);
        mLastChatDetails = new ArrayList<>(size);
        mUnreadCounts = new ArrayList<>(size);
        mChats = chats;

        mType = type;

        UserDao userDao = UserDao.getInstance();
        ChatDao chatDao = ChatDao.getInstance();
        for (Chat chat : mChats) {
            String userId = chat.getUserId();
            mUsers.add(userDao.getUserById(userId));
            mLastChatDetails.add(chatDao.getLastChatDetail(userId));
            mUnreadCounts.add(0);
        }

        EventBus.getDefault().register(this); // TODO: 2016/5/30 更改adapter、删除时都要unregister RxBus
    }

    @Subscribe
    public void onMessageReceived(Event.ReceiveMessage event) {
        onNewChatDetailAdded(event.chatDetail, true);
    }

    @Subscribe
    public void onMessageSent(Event.SendMessage event) {
        onNewChatDetailAdded(event.chatDetail, false);
    }

    private void onNewChatDetailAdded(ChatDetail chatDetail, boolean receive) {
        Chat chat = ChatDao.getInstance().getChat(chatDetail, false);
        if (chat.getType() != mType) {
            return;
        }

        String userId = receive ? chatDetail.getFromId() : chatDetail.getToId();
        User user = UserDao.getInstance().getUserById(userId);
        int posBefore = getInfoPosition(userId);
        if (posBefore != -1) {
            mUsers.remove(posBefore);
            mChats.remove(posBefore);
            mLastChatDetails.remove(posBefore);
        }
        mUsers.add(0, user);
        mChats.add(0, chat);
        mLastChatDetails.add(0, chatDetail);

        // 更新未读计数
        if (receive) {
            if (posBefore != -1) {
                int unread = mUnreadCounts.remove(posBefore);
                mUnreadCounts.add(0, unread + 1);
            } else {
                mUnreadCounts.add(0, 1);
            }
        } else {
            if (posBefore != -1) {
                mUnreadCounts.remove(posBefore);
            }
            mUnreadCounts.add(0, 0);
        }

        if (posBefore != -1) {
            if (posBefore == 0) {
                notifyItemChanged(0);
            } else {
                notifyItemMoved(posBefore, 0);
                notifyItemChanged(0);
            }
        } else {
            notifyItemInserted(0);
        }
    }

    @Subscribe
    public void clearUnread(Event.ClearUnread event) {
        int pos = getInfoPosition(event.other.getId());
        if (pos != -1 && mUnreadCounts.get(pos) != 0) {
            mUnreadCounts.set(pos, 0);
            notifyItemChanged(pos);
        }
    }

    private int getInfoPosition(String userId) {
        final int size = mLastChatDetails.size();
        for (int i = 0; i < size; i++) {
            ChatDetail chatDetail = mLastChatDetails.get(i);
            if (userId.equals(chatDetail.getFromId()) || userId.equals(chatDetail.getToId())) {
                return i;
            }
        }
        return -1;
    }

    public List<Integer> getUnreadCounts() {
        return mUnreadCounts;
    }

    public List<Chat> getChats() {
        return mChats;
    }

    public HashMap<String, Object> getInfoMap(String userId) {
        int pos = getInfoPosition(userId);
        HashMap<String, Object> infoMap = new HashMap<>();
        infoMap.put(Def.Key.USER, mUsers.get(pos));
        infoMap.put(Def.Key.CHAT, mChats.get(pos));
        infoMap.put(Def.Key.CHAT_DETAIL, mLastChatDetails.get(pos));
        infoMap.put(Def.Key.UNREAD_COUNT, mUnreadCounts.get(pos));
        return infoMap;
    }

    public void add(HashMap<String, Object> infoMap) {
        User user = (User) infoMap.get(Def.Key.USER);
        Chat chat = (Chat) infoMap.get(Def.Key.CHAT);
        ChatDetail chatDetail = (ChatDetail) infoMap.get(Def.Key.CHAT_DETAIL);
        int unreadCount = (int) infoMap.get(Def.Key.UNREAD_COUNT);
        mUsers.add(0, user);
        mChats.add(0, chat);
        mLastChatDetails.add(0, chatDetail);
        mUnreadCounts.add(0, unreadCount);
        notifyItemInserted(0);
    }

    public void remove(String userId) {
        int pos = getInfoPosition(userId);
        mUsers.remove(pos);
        mChats.remove(pos);
        mLastChatDetails.remove(pos);
        mUnreadCounts.remove(pos);
        notifyItemRemoved(pos);
    }

    @Override
    public ChatHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ChatHolder(mInflater.inflate(R.layout.rv_chat_list, parent, false));
    }

    @Override
    public void onBindViewHolder(ChatHolder holder, int position) {
        User user = mUsers.get(position);
        holder.ivAvatar.setImageDrawable(user.getAvatarDrawable());
        holder.tvName.setText(user.getName());

        ChatDetail chatDetail = mLastChatDetails.get(position);
        holder.tvContent.setText(chatDetail.getContent());
        holder.tvTime.setText(DateTimeUtil.getShortDateTimeString(chatDetail.getTime()));

        Integer unread = mUnreadCounts.get(position);
        if (unread == 0) {
            holder.tvUnread.setText("");
        } else {
            holder.tvUnread.setText(unread.toString());
        }

        if (position != getItemCount() - 1) {
            holder.separator.setVisibility(View.VISIBLE);
        } else {
            holder.separator.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return mChats.size();
    }

    class ChatHolder extends BaseViewHolder {

        ImageView ivAvatar;
        TextView  tvUnread;
        TextView  tvName;
        TextView  tvContent;
        TextView  tvTime;
        View      separator;

        public ChatHolder(View itemView) {
            super(itemView);

            ivAvatar  = f(R.id.iv_avatar_chat_list);
            tvUnread  = f(R.id.tv_unread_chat_list);
            tvName    = f(R.id.tv_name_chat_list);
            tvContent = f(R.id.tv_content_chat_list);
            tvTime    = f(R.id.tv_time_chat_list);
            separator = f(R.id.view_separator);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int pos = getAdapterPosition();
                    mUnreadCounts.set(pos, 0);
                    notifyItemChanged(pos);

                    EventBus.getDefault().post(new Event.StartChat(mUsers.get(pos)));
                }
            });

            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    EventBus.getDefault().post(
                            new Event.ChatListLongClick(
                                    mChats.get(getAdapterPosition())));
                    return true;
                }
            });
        }
    }

}
