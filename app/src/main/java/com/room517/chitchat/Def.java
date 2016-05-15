package com.room517.chitchat;

/**
 * Created by ywwynm on 2016/5/14.
 * "侃侃"应用里的定义、常量
 */
public class Def {

    private Def() {}

    public static class Meta {

        public static final String PREFERENCE_META    = "chitchat_meta";
        public static final String PREFERENCE_USER_ME = "user_me";

    }

    public static class KEY {

        private static final String K = BuildConfig.APPLICATION_ID + ".key";

        public static class PrefMeta {

            public static final String FIRST_LAUNCH = "first_launch";

        }

        public static class PrefUserMe {

            public static final String ID          = "id";
            public static final String NAME        = "name";
            public static final String SEX         = "sex";
            public static final String TAG         = "tag";
            public static final String LOCATION    = "location";
            public static final String CREATE_TIME = "create_time";

        }

    }

    public static class DB {

    }

}