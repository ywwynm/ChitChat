package com.room517.chitchat;

import com.room517.chitchat.model.Chat;
import com.room517.chitchat.model.ChatDetail;
import com.room517.chitchat.model.User;

/**
 * Created by ywwynm on 2016/6/5.
 * EventBus的事件类
 */
public class Event {

    public static class StartChat {

        public User user;

        public StartChat(User user) {
            this.user = user;
        }
    }

    // ----------------------------------------------------------

    public static class ReceiveMessage {

        public ChatDetail chatDetail;

        public ReceiveMessage(ChatDetail chatDetail) {
            this.chatDetail = chatDetail;
        }
    }

    // ----------------------------------------------------------

    public static class SendMessage {

        public ChatDetail chatDetail;

        public SendMessage(ChatDetail chatDetail) {
            this.chatDetail = chatDetail;
        }
    }

    // ----------------------------------------------------------

    public static class PrepareForFragment {

    }

    // ----------------------------------------------------------

    public static class BackFromFragment {

    }

    // ----------------------------------------------------------

    public static class ClearUnread {

        public User other;

        public ClearUnread(User other) {
            this.other = other;
        }
    }

    // ----------------------------------------------------------

    public static class ChatListLongClick {

        public Chat chat;

        public ChatListLongClick(Chat chat) {
            this.chat = chat;
        }
    }

    // ----------------------------------------------------------

    public static class ChatDetailLongClick {

        public ChatDetail chatDetail;

        public ChatDetailLongClick(ChatDetail chatDetail) {
            this.chatDetail = chatDetail;
        }
    }

    // ----------------------------------------------------------

}
