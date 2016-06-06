package com.room517.chitchat.ui.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.orhanobut.logger.Logger;
import com.room517.chitchat.App;
import com.room517.chitchat.BuildConfig;
import com.room517.chitchat.Def;
import com.room517.chitchat.Event;
import com.room517.chitchat.R;
import com.room517.chitchat.db.ChatDao;
import com.room517.chitchat.db.UserDao;
import com.room517.chitchat.helpers.NotificationHelper;
import com.room517.chitchat.helpers.RetrofitHelper;
import com.room517.chitchat.helpers.RxHelper;
import com.room517.chitchat.io.SimpleObserver;
import com.room517.chitchat.io.network.MainService;
import com.room517.chitchat.io.network.UserService;
import com.room517.chitchat.model.Chat;
import com.room517.chitchat.model.ChatDetail;
import com.room517.chitchat.model.User;
import com.room517.chitchat.ui.fragments.ChatDetailsFragment;
import com.room517.chitchat.ui.fragments.ChatListFragment;
import com.room517.chitchat.ui.fragments.ExploreListFragment;
import com.room517.chitchat.ui.fragments.NearbyPeopleFragment;
import com.room517.chitchat.ui.views.FloatingActionButton;
import com.room517.chitchat.utils.JsonUtil;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;

import io.rong.imlib.RongIMClient;
import io.rong.imlib.model.Message;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;

/**
 * Created by ywwynm on 2016/5/13.
 * 打开应用后的第一个Activity，显示最近会话列表、朋友圈等
 */
public class MainActivity extends BaseActivity {

    private Toolbar              mActionBar;
    private FloatingActionButton mFab;

    private ViewPager           mViewPager;
    private TabLayout           mTabLayout;
    private ChatListFragment    mChatListFragment;
    private ExploreListFragment mExploreListFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EventBus.getDefault().register(this);

        // 如果应用是安装后第一次打开，跳转到引导、"注册"页面
        // TODO: 2016/5/24 在正式版本中加上这些代码
        SharedPreferences sp = getSharedPreferences(
                Def.Meta.PREFERENCE_USER_ME, MODE_PRIVATE);
        if (!sp.contains(Def.Key.PrefUserMe.ID)) {
            Intent intent = new Intent(this, WelcomeActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        prepareConnectRongServer();

        super.init();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Logger.i("MainActivity onNewIntent");

        if (intent == null) {
            return;
        }

        User user = intent.getParcelableExtra(Def.Key.USER);
        if (user != null) {
            startChat(new Event.StartChat(user));
            intent.removeExtra(Def.Key.USER);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    private void prepareConnectRongServer() {
        User   me     = App.getMe();
        String userId = me.getId();
        String name   = me.getName();
        String avatar = me.getAvatar();

        Retrofit retrofit = RetrofitHelper.getBaseUrlRetrofit();
        MainService service = retrofit.create(MainService.class);
        RxHelper.ioMain(service.getRongToken(userId, name, avatar),
                new SimpleObserver<ResponseBody>() {

                    @Override
                    public void onError(Throwable throwable) {
                        throwable.printStackTrace();
                        showLongToast(R.string.error_network_disconnected);
                    }

                    @Override
                    public void onNext(ResponseBody responseBody) {
                        try {
                            String body  = responseBody.string();
                            Logger.json(body);

                            String token = JsonUtil.getParam(body, "token").getAsString();
                            setupRongListeners();
                            connectRongServer(token);
                        } catch (IOException e) {
                            e.printStackTrace();
                            showLongToast(R.string.error_unknown);
                        }
                    }
                });
    }

    private void setupRongListeners() {
        RongIMClient.setOnReceiveMessageListener(new RongIMClient.OnReceiveMessageListener() {
            @Override
            public boolean onReceived(Message message, int leftCount) {
                final ChatDetail chatDetail = new ChatDetail(message);
                String fromId = chatDetail.getFromId();
                final UserDao userDao = UserDao.getInstance();
                if (userDao.getUserById(fromId) == null) {
                    /*
                        数据库中还没有该User，插入新的Chat或ChatDetail都会失败（因为外键的缘故），
                        所以需要先从服务器获取该User的完整信息并插入到本地数据库
                     */
                    Retrofit retrofit = RetrofitHelper.getBaseUrlRetrofit();
                    UserService service = retrofit.create(UserService.class);
                    RxHelper.ioMain(service.getUserById(fromId), new SimpleObserver<User>() {
                        @Override
                        public void onError(Throwable throwable) {
                            // TODO: 2016/5/31 handle error situation here
                            Logger.e(throwable.getMessage());
                        }

                        @Override
                        public void onNext(User user) {
                            userDao.insert(user);
                            insertChatDetailAndPostEvent(chatDetail);
                        }
                    });
                } else {
                    insertChatDetailAndPostEvent(chatDetail);
                }
                return true;
            }
        });
    }

    public void insertChatDetailAndPostEvent(ChatDetail chatDetail) {
        String fromId = chatDetail.getFromId();
        ChatDao chatDao = ChatDao.getInstance();
        if (chatDao.getChat(fromId, false) == null) {
            Chat chat = new Chat(fromId, Chat.TYPE_NORMAL);
            chatDao.insertChat(chat);
        }
        chatDao.insertChatDetail(chatDetail);

        if (App.shouldNotifyMessage(fromId)) {
            NotificationHelper.notifyMessage(this, fromId, chatDetail.getContent());
        }

        EventBus.getDefault().post(new Event.ReceiveMessage(chatDetail));
    }

    private void connectRongServer(String token) {
        RongIMClient.connect(token, new RongIMClient.ConnectCallback() {
            @Override
            public void onTokenIncorrect() {
                prepareConnectRongServer();
            }

            @Override
            public void onSuccess(String s) {
                if (BuildConfig.DEBUG) {
                    Logger.i("Connect Rong successfully!\ntoken: " + s);
                }
            }

            @Override
            public void onError(RongIMClient.ErrorCode errorCode) {
                if (BuildConfig.DEBUG) {
                    Logger.e("Connect Rong failed\nerror: " + errorCode.getMessage());
                }
            }
        });
    }

    @Override
    protected void initMember() {
        mChatListFragment    = ChatListFragment.newInstance(null);
        mExploreListFragment = ExploreListFragment.newInstance(null);
    }

    @Override
    protected void findViews() {
        mActionBar = f(R.id.actionbar);
        mFab       = f(R.id.fab_main);

        mViewPager = f(R.id.vp_main);
        mTabLayout = f(R.id.tab_layout);
    }

    @Override
    protected void initUI() {
        initActionBar();

        mFab.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.app_orange)));
        mFab.setRippleColor(ContextCompat.getColor(this, R.color.fab_ripple_white));

        MainFragmentPagerAdapter adapter = new MainFragmentPagerAdapter(getSupportFragmentManager());
        mViewPager.setAdapter(adapter);

        mTabLayout.setupWithViewPager(mViewPager);
        TabLayout.Tab tab0 = mTabLayout.getTabAt(0);
        if (tab0 != null) {
            tab0.setIcon(R.drawable.ic_chat);
        }
        TabLayout.Tab tab1 = mTabLayout.getTabAt(1);
        if (tab1 != null) {
            tab1.setIcon(R.drawable.ic_explore);
        }
    }

    private void initActionBar() {
        setSupportActionBar(mActionBar);
        setActionBarAppearance();
    }

    private void setActionBarAppearance() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setTitle(null);
        }
    }

    @Override
    protected void setupEvents() {
        mActionBar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getSupportFragmentManager().popBackStack();
            }
        });
        setupFabEvent();

    }

    private void setupFabEvent() {
        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .add(R.id.container_main, NearbyPeopleFragment.newInstance(null))
                        .addToBackStack(NearbyPeopleFragment.class.getName())
                        .commit();
            }
        });
    }

    @Subscribe
    public void prepareForFragments(Event.PrepareForFragment event) {
        mTabLayout.setVisibility(View.GONE);
        mFab.shrink();
    }

    @Subscribe
    public void backFromFragment(Event.BackFromFragment event) {
        setActionBarAppearance();
        mTabLayout.setVisibility(View.VISIBLE);
        mFab.spread();
    }

    @Subscribe
    public void startChat(Event.StartChat startChat) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.popBackStack();
        // TODO: 2016/6/6 bugs here

        Bundle args = new Bundle();
        args.putParcelable(Def.Key.USER, startChat.user);
        fragmentManager
                .beginTransaction()
                .add(R.id.container_main, ChatDetailsFragment.newInstance(args))
                .addToBackStack(ChatDetailsFragment.class.getName())
                .commit();
    }

    class MainFragmentPagerAdapter extends FragmentPagerAdapter {

        public MainFragmentPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            if (position == 0) {
                return mChatListFragment;
            } else if (position == 1) {
                return mExploreListFragment;
            }
            return null;
        }

        @Override
        public int getCount() {
            return 2;
        }
    }
}
