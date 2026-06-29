package com.runlog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.runlog.capture.CaptureParseResult;
import com.runlog.capture.PcapTextParser;
import com.runlog.data.AppConfig;
import com.runlog.data.CandidateConfig;
import com.runlog.data.ConfigStore;
import com.runlog.data.ConfigValidationResult;
import com.runlog.data.ConfigValidator;
import com.runlog.log.FileLog;
import com.runlog.network.LoginClient;
import com.runlog.network.LoginResult;
import com.runlog.network.HistoryClient;
import com.runlog.network.HistoryExtractionResult;
import com.runlog.network.NoticeClient;
import com.runlog.network.NoticeInfo;
import com.runlog.network.RunClient;
import com.runlog.task.TaskRepository;
import com.runlog.task.TaskSummary;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int REQUEST_IMPORT_CAPTURE = 2101;
    private static final int REQUEST_IMPORT_HISTORY_TASKS = 2102;
    private static final int TAB_HOME = 0;
    private static final int TAB_DATA = 1;
    private static final int TAB_PROFILE = 2;
    private static final int TAB_NOTICE = 3;

    private static final int COLOR_INK = 0xFF142421;
    private static final int COLOR_MUTED = 0xFF64756F;
    private static final int COLOR_LINE = 0xFFD9E2DF;
    private static final int COLOR_PAPER = 0xFFF7FAF8;
    private static final int COLOR_CARD = 0xFFFFFFFF;
    private static final int COLOR_PRIMARY = 0xFF008F7A;
    private static final int COLOR_PRIMARY_DEEP = 0xFF06483D;
    private static final int COLOR_PRIMARY_SOFT = 0xFFE5F6F1;
    private static final int COLOR_LIME = 0xFFB9DF6A;
    private static final int COLOR_AMBER = 0xFFF4B942;
    private static final int COLOR_CORAL = 0xFFE8664D;
    private static final int COLOR_CYAN = 0xFF3DB7C6;

    private LinearLayout content;
    private TextView status;
    private TextView appSubtitle;
    private Button homeNavButton;
    private Button dataNavButton;
    private Button profileNavButton;
    private ConfigStore configStore;
    private FileLog log;
    private TaskRepository taskRepository;
    private AppConfig config;
    private List<TaskSummary> tasks;
    private boolean runningForTest = false;
    private boolean extractingHistory = false;
    private volatile boolean runStopRequested = false;
    private int activeTab = TAB_HOME;
    private final List<String> dataVisibleNames = new ArrayList<>();
    private String runProgressText = "等待开始";
    private String historyProgressText = "等待提取";
    private TextView homeProgressRing;
    private TextView homeProgressCopy;
    private ProgressBar homeProgressBar;
    private TextView historyProgressCopy;
    private ProgressBar historyProgressBar;
    private volatile boolean dataRefreshRunning = false;
    private volatile boolean profileRefreshRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configStore = new ConfigStore(this);
        log = new FileLog(this);
        taskRepository = new TaskRepository(this);
        reload();
        log.append("App 启动，已加载任务 " + tasks.size() + " 条");
        buildShell();
        showHome();
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_PAPER);

        LinearLayout appbar = new LinearLayout(this);
        appbar.setOrientation(LinearLayout.HORIZONTAL);
        appbar.setGravity(Gravity.CENTER_VERTICAL);
        final int appbarPadLeft = dp(18);
        final int appbarPadTop = dp(14);
        final int appbarPadRight = dp(18);
        final int appbarPadBottom = dp(14);
        appbar.setPadding(appbarPadLeft, appbarPadTop, appbarPadRight, appbarPadBottom);
        appbar.setBackgroundColor(COLOR_PRIMARY_DEEP);
        appbar.setElevation(dp(6));

        TextView brandMark = new TextView(this);
        brandMark.setText("R");
        brandMark.setTextSize(18);
        brandMark.setTypeface(Typeface.DEFAULT_BOLD);
        brandMark.setTextColor(COLOR_PRIMARY_DEEP);
        brandMark.setGravity(Gravity.CENTER);
        brandMark.setBackground(roundedGradient(COLOR_LIME, COLOR_CYAN, dp(9)));
        LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        markLp.setMargins(0, 0, dp(10), 0);
        appbar.addView(brandMark, markLp);

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("RunLog");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFFFFFFF);
        title.setIncludeFontPadding(false);
        appSubtitle = new TextView(this);
        appSubtitle.setText("跑步任务控制台");
        appSubtitle.setTextSize(12);
        appSubtitle.setTextColor(0xB8FFFFFF);
        brandText.addView(title);
        brandText.addView(appSubtitle);
        appbar.addView(brandText, new LinearLayout.LayoutParams(0, -2, 1));

        TextView appState = new TextView(this);
        appState.setText("5G  89%");
        appState.setTextSize(12);
        appState.setTypeface(Typeface.DEFAULT_BOLD);
        appState.setTextColor(0xFFFFFFFF);
        appState.setGravity(Gravity.CENTER);
        appState.setBackground(rounded(0x22FFFFFF, 0, dp(8)));
        appState.setPadding(dp(10), dp(8), dp(10), dp(8));
        appbar.addView(appState);
        root.addView(appbar, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setText("配置、登录、导入、日志和公告已启用");
        status.setTextSize(14);
        status.setTextColor(COLOR_PRIMARY_DEEP);
        status.setPadding(dp(14), dp(9), dp(14), dp(9));
        status.setBackgroundColor(0xFFEAF4F1);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(10));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        final int navPadLeft = dp(10);
        final int navPadTop = dp(8);
        final int navPadRight = dp(10);
        final int navPadBottom = dp(10);
        nav.setPadding(navPadLeft, navPadTop, navPadRight, navPadBottom);
        nav.setBackgroundColor(COLOR_CARD);
        nav.setElevation(dp(8));
        homeNavButton = navButton("首页", v -> switchTab(TAB_HOME));
        dataNavButton = navButton("数据", v -> switchTab(TAB_DATA));
        profileNavButton = navButton("我的", v -> switchTab(TAB_PROFILE));
        nav.addView(homeNavButton, new LinearLayout.LayoutParams(0, dp(50), 1));
        nav.addView(dataNavButton, new LinearLayout.LayoutParams(0, dp(50), 1));
        nav.addView(profileNavButton, new LinearLayout.LayoutParams(0, dp(50), 1));
        root.addView(nav, new LinearLayout.LayoutParams(-1, -2));

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            appbar.setPadding(appbarPadLeft, appbarPadTop + insets.getSystemWindowInsetTop(), appbarPadRight, appbarPadBottom);
            nav.setPadding(navPadLeft, navPadTop, navPadRight, navPadBottom + insets.getSystemWindowInsetBottom());
            return insets;
        });

        setContentView(root);
        root.requestApplyInsets();
        updateNav();
    }

    private void showHome() {
        activeTab = TAB_HOME;
        updateNav();
        setSubtitle("跑步任务控制台");
        content.removeAllViews();
        clearPageBindings();
        TaskSummary selected = selectedTask();
        content.addView(pageHeader("开始跑步", "主操作聚焦", "开源免费"));

        LinearLayout hero = card();
        LinearLayout heroRow = new LinearLayout(this);
        heroRow.setOrientation(LinearLayout.HORIZONTAL);
        heroRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        heroText.addView(cardTitle(selected == null ? "任务待选择，请先准备跑步数据" : "任务已就绪，可以开始执行"));
        heroText.addView(cardCopy(selected == null ? "未指定数据时，将按当前规则从已导入/提取的数据中选择。" : "已选择历史路线，运行管线会先通过本机配置校验。"));
        heroRow.addView(heroText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView ring = new TextView(this);
        ring.setText(runningForTest ? progressPercent(runProgressText) + "%" : "72%");
        ring.setGravity(Gravity.CENTER);
        ring.setTextSize(15);
        ring.setTypeface(Typeface.DEFAULT_BOLD);
        ring.setTextColor(COLOR_PRIMARY_DEEP);
        ring.setBackground(rounded(COLOR_PRIMARY_SOFT, COLOR_PRIMARY, dp(28)));
        heroRow.addView(ring, new LinearLayout.LayoutParams(dp(58), dp(58)));
        homeProgressRing = ring;
        hero.addView(heroRow);
        content.addView(hero);

        GridLayout stats = statGrid(3);
        stats.addView(statCell("长度", selected == null ? "-" : selected.distanceText()));
        stats.addView(statCell("时间", selected == null ? "-" : selected.durationText()));
        stats.addView(statCell("配速", selected == null ? "-" : selected.paceText()));
        content.addView(stats);

        content.addView(info("跑步数据", selected == null ? "未指定，将从已导入/提取数据中随机选择" : selected.fileName));
        content.addView(chipCard("数据选择", modeText()));
        content.addView(phaseCard("当前阶段", "开始跑步会执行 Kotlin / Android 原生迁移后的运行管线，并实时显示进度。"));
        content.addView(primaryButton("开始跑步", v -> startRunFlow()));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(secondaryButton("检测配置", v -> checkCurrentConfig()), new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(neutralButton("停止", v -> stopRunFlow()), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(actions);
        content.addView(progressCard("进度", runProgressText, Math.max(8, progressPercent(runProgressText)), true, false));
        updateHomeProgressViews();
    }

    private void showTasks() {
        showTasks(true);
    }

    private void showTasks(boolean scheduleRefresh) {
        activeTab = TAB_DATA;
        updateNav();
        setSubtitle("历史数据管理");
        content.removeAllViews();
        clearPageBindings();
        List<TaskSummary> visibleTasks = visibleTasksForData();
        content.addView(pageHeader("跑步数据", "本地与在线数据", "显示 " + visibleTasks.size() + " / " + tasks.size() + " 条"));

        LinearLayout source = card();
        LinearLayout sourceRow = new LinearLayout(this);
        sourceRow.setOrientation(LinearLayout.HORIZONTAL);
        sourceRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout sourceText = new LinearLayout(this);
        sourceText.setOrientation(LinearLayout.VERTICAL);
        sourceText.addView(metaLabel("数据来源"));
        sourceText.addView(strongText("本地导入 / 在线提取"));
        sourceText.addView(chipRow(new String[]{"去重后 " + tasks.size() + " 条", "出现 " + totalOccurrences() + " 次", "当前显示 " + visibleTasks.size() + " 条"}));
        sourceRow.addView(sourceText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView count = new TextView(this);
        count.setText(String.valueOf(tasks.size()));
        count.setTextSize(20);
        count.setTypeface(Typeface.DEFAULT_BOLD);
        count.setGravity(Gravity.CENTER);
        count.setTextColor(COLOR_PRIMARY_DEEP);
        count.setBackground(rounded(0xFFF3F8E4, 0, dp(8)));
        sourceRow.addView(count, new LinearLayout.LayoutParams(dp(56), dp(56)));
        source.addView(sourceRow);
        content.addView(source);

        GridLayout tools = toolGrid();
        tools.addView(toolButton("导入历史", "JSON / 轨迹包", v -> pickHistoryTaskFile()));
        tools.addView(toolButton("在线提取", "同步任务列表", v -> extractHistoryOnline()));
        tools.addView(toolButton("刷新数据", "重算统计信息", v -> {
            reload();
            setStatus("跑步数据已刷新，共 " + tasks.size() + " 条");
            showTasks();
        }));
        tools.addView(toolButton("随机模式", "按规则选择", v -> {
            config.pickMode = "RANDOM";
            saveConfig("切换为随机跑步数据");
            showTasks();
        }));
        tools.addView(toolButton("换一组", "随机显示 5 条", v -> {
            chooseRandomDataGroup();
            showTasks();
        }));
        content.addView(tools);
        content.addView(progressCard("在线提取进度", historyProgressText, progressPercent(historyProgressText), false, true));
        updateHistoryProgressViews();
        for (TaskSummary task : visibleTasks) {
            content.addView(taskRow(task));
        }
        if (tasks.size() > visibleTasks.size()) {
            content.addView(info("更多", "还有 " + (tasks.size() - visibleTasks.size()) + " 条未显示，可点击“换一组”随机查看。"));
        }
        if (scheduleRefresh) refreshTasksAsync();
    }

    private void showProfile() {
        showProfile(true);
    }

    private void showProfile(boolean scheduleRefresh) {
        activeTab = TAB_PROFILE;
        updateNav();
        setSubtitle("配置与日志");
        content.removeAllViews();
        clearPageBindings();
        content.addView(pageHeader("我的中心", "配置与运行日志", "开源免费"));

        LinearLayout account = card();
        LinearLayout accountRow = new LinearLayout(this);
        accountRow.setOrientation(LinearLayout.HORIZONTAL);
        accountRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView avatar = new TextView(this);
        avatar.setText("RL");
        avatar.setTextSize(18);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setTextColor(COLOR_LIME);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(rounded(0xFF132D27, 0, dp(8)));
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        avatarLp.setMargins(0, 0, dp(12), 0);
        accountRow.addView(avatar, avatarLp);
        LinearLayout accountText = new LinearLayout(this);
        accountText.setOrientation(LinearLayout.VERTICAL);
        accountText.addView(metaLabel("开源状态"));
        accountText.addView(strongText("本软件完全免费，请勿付费购买"));
        accountText.addView(chipRow(new String[]{config.platform, "v" + config.appEdition, runModeText()}));
        accountRow.addView(accountText, new LinearLayout.LayoutParams(0, -2, 1));
        account.addView(accountRow);
        content.addView(account);

        LinearLayout configCard = card();
        configCard.addView(configRow("deviceName", blank(config.deviceName) ? "-" : config.deviceName));
        configCard.addView(configRow("token", shorten(config.token)));
        configCard.addView(configRow("deviceId", shorten(config.deviceId)));
        configCard.addView(configRow("数据选择", pickModeText() + " · 草稿 " + (config.draft ? "是" : "否")));
        content.addView(configCard);

        GridLayout tools = toolGrid();
        tools.addView(toolButton("检测配置", "快速校验", v -> checkCurrentConfig()));
        tools.addView(toolButton("刷新 TOKEN", "登录同步", v -> showLoginDialog()));
        tools.addView(toolButton("导入配置", "抓包文件", v -> pickCaptureFile()));
        tools.addView(toolButton("公告", "开源声明", v -> showNotice()));
        tools.addView(toolButton("选择模式", "随机 / 指定", v -> {
            config.pickMode = "RANDOM".equals(config.pickMode) ? "SPECIFIED" : "RANDOM";
            saveConfig("切换数据选择模式: " + config.pickMode);
            showProfile();
        }));
        content.addView(tools);

        CheckBox drift = new CheckBox(this);
        drift.setText("添加漂移");
        drift.setChecked(config.driftEnabled);
        drift.setTextColor(COLOR_INK);
        drift.setPadding(dp(12), dp(8), dp(12), dp(8));
        drift.setBackground(rounded(COLOR_CARD, COLOR_LINE, dp(8)));
        drift.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.driftEnabled = isChecked;
            saveConfig("漂移开关: " + isChecked);
        });
        content.addView(drift);
        content.addView(progressCard("配置健康度", configHealthText(), configHealthPercent()));
        content.addView(dangerCard("历史数据", "可清除本地缓存与导入记录", v -> confirmClearHistory()));
        content.addView(info("日志文件", log.file().getAbsolutePath()));
        content.addView(info("最近日志", log.tail().isEmpty() ? "暂无日志" : log.tail()));
        if (scheduleRefresh) refreshProfileAsync();
    }

    private void switchTab(int tab) {
        if (tab == activeTab) return;
        if (tab == TAB_HOME) {
            showHome();
        } else if (tab == TAB_DATA) {
            showTasks();
        } else if (tab == TAB_NOTICE) {
            showNotice();
        } else {
            showProfile();
        }
    }

    private void showNotice() {
        activeTab = TAB_NOTICE;
        updateNav();
        setSubtitle("开源公告");
        content.removeAllViews();
        clearPageBindings();
        renderNotice(new NoticeClient().fetch());
    }

    private void renderNotice(NoticeInfo notice) {
        content.removeAllViews();
        content.addView(pageHeader("公告", "本地开源声明", notice != null && notice.enabled ? "已发布" : "暂无"));
        LinearLayout box = card();
        if (notice == null || !notice.enabled) {
            box.addView(cardTitle("暂无公告"));
            box.addView(cardCopy("当前没有新的公告。"));
        } else {
            box.addView(cardTitle(blank(notice.title) ? "公告" : notice.title));
            box.addView(cardCopy(blank(notice.content) ? "-" : notice.content));
            box.addView(chipRow(new String[]{"v" + notice.version, formatTimestamp(notice.updatedAt)}));
        }
        content.addView(box);
        content.addView(secondaryButton("重新加载", v -> showNotice()));
        content.addView(secondaryButton("返回我的", v -> showProfile()));
        setStatus(notice != null && notice.enabled ? "公告已加载" : "暂无公告");
    }

    private void refreshTasksAsync() {
        if (dataRefreshRunning) return;
        dataRefreshRunning = true;
        new Thread(() -> {
            try {
                reload();
                runOnUiThread(() -> {
                    if (activeTab == TAB_DATA) showTasks(false);
                });
            } finally {
                dataRefreshRunning = false;
            }
        }).start();
    }

    private void refreshProfileAsync() {
        if (profileRefreshRunning) return;
        profileRefreshRunning = true;
        new Thread(() -> {
            try {
                reload();
                runOnUiThread(() -> {
                    if (activeTab == TAB_PROFILE) showProfile(false);
                });
            } finally {
                profileRefreshRunning = false;
            }
        }).start();
    }

    private View taskRow(TaskSummary task) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.setBackground(rounded(COLOR_CARD, COLOR_LINE, dp(8)));
        box.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(5), 0, dp(7));
        box.setLayoutParams(lp);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView text = new TextView(this);
        text.setText(task.fileName);
        text.setTextSize(14);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextColor(COLOR_INK);
        head.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(pill("本地", COLOR_PRIMARY_SOFT, COLOR_PRIMARY_DEEP));
        box.addView(head);

        GridLayout mini = statGrid(3);
        mini.addView(statCell("长度", task.distanceText()));
        mini.addView(statCell("时间", task.durationText()));
        mini.addView(statCell("使用", task.useCount + " 次"));
        box.addView(mini);

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        TextView detail = new TextView(this);
        detail.setText("出现 " + task.duplicateCount + " 次 · 轨迹点 " + task.pointsCount + " · 打卡点 " + task.manageCount + " · 配速 " + task.paceText());
        detail.setTextSize(12);
        detail.setTextColor(COLOR_MUTED);
        foot.addView(detail, new LinearLayout.LayoutParams(0, -2, 1));

        Button choose = new Button(this);
        choose.setText("指定使用");
        choose.setAllCaps(false);
        choose.setTextColor(0xFFFFFFFF);
        choose.setTypeface(Typeface.DEFAULT_BOLD);
        choose.setBackground(rounded(COLOR_PRIMARY, 0, dp(8)));
        choose.setOnClickListener(v -> {
            config.pickMode = "SPECIFIED";
            config.selectedTask = task.fileName;
            saveConfig("指定任务: " + task.fileName);
            showHome();
        });
        foot.addView(choose, new LinearLayout.LayoutParams(dp(104), dp(42)));
        box.addView(foot);
        return box;
    }

    private void showLoginDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(4));
        EditText schoolName = input("学校名称", config.schoolName, false);
        EditText username = input("账号", config.username, false);
        EditText password = input("密码", config.password, true);
        form.addView(schoolName);
        form.addView(username);
        form.addView(password);

        new AlertDialog.Builder(this)
                .setTitle("登录并刷新 Token")
                .setMessage("只需要输入学校名称、账号和密码。App 会自动查询学校 ID；该操作可能导致手机客户端登录失效。")
                .setView(form)
                .setPositiveButton("登录", (dialog, which) -> {
                    config.schoolName = schoolName.getText().toString().trim();
                    config.schoolId = "";
                    config.username = username.getText().toString().trim();
                    config.password = password.getText().toString();
                    config.platform = "android";
                    config.appEdition = AppConfig.DEFAULT_APP_EDITION;
                    config.schoolHost = AppConfig.DEFAULT_SCHOOL_HOST;
                    ensureProtocolDefaults(config);
                    if (blank(config.deviceId)) config.deviceId = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
                    if (blank(config.uuid)) config.uuid = UUID.randomUUID().toString().toUpperCase(Locale.US);
                    if (blank(config.deviceName)) config.deviceName = android.os.Build.MANUFACTURER + "(" + android.os.Build.MODEL + ")";
                    saveConfig("保存登录参数，准备登录");
                    performLogin();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void performLogin() {
        ensureProtocolDefaults(config);
        setStatus("正在登录，请稍候...");
        log.append("开始账号密码登录，schoolName=" + config.schoolName);
        final AppConfig loginConfig = config.copy();
        new Thread(() -> {
            try {
                LoginResult result = new LoginClient().login(loginConfig);
                runOnUiThread(() -> {
                    config = configStore.load();
                    config.token = result.token;
                    config.deviceId = result.deviceId;
                    config.deviceName = result.deviceName;
                    config.uuid = result.uuid;
                    config.schoolId = result.schoolId;
                    config.schoolName = result.schoolName;
                    config.schoolHost = result.schoolHost;
                    config.platform = "android";
                    config.appEdition = AppConfig.DEFAULT_APP_EDITION;
                    config.draft = !ConfigValidator.validate(config).valid;
                    saveConfig("登录成功并刷新 Token");
                    setStatus("登录成功，Token 已刷新");
                    showProfile();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    String message = errorMessage(e);
                    setStatus("登录失败: " + message);
                    log.append("登录失败: " + message);
                    showProfile();
                });
            }
        }).start();
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
                .setTitle("清除历史数据")
                .setMessage("将清除本机保存的配置、登录参数、Token、配置备份、日志和提取出来的历史跑步 JSON。APK 不再内置原始跑步 JSON。")
                .setPositiveButton("清除", (dialog, which) -> {
                    configStore.clearAll();
                    taskRepository.clearExtracted();
                    log.clear();
                    runningForTest = false;
                    reload();
                    log.append("已清除历史数据");
                    setStatus("历史数据已清除");
                    showProfile();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private EditText input(String hint, String value, boolean password) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setText(value == null ? "" : value);
        edit.setInputType(password ? (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD) : InputType.TYPE_CLASS_TEXT);
        return edit;
    }

    private void pickCaptureFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        try {
            startActivityForResult(intent, REQUEST_IMPORT_CAPTURE);
        } catch (Exception e) {
            setStatus("无法打开文件选择器: " + e.getMessage());
            log.append("打开抓包文件选择器失败: " + e);
        }
    }

    private void pickHistoryTaskFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_IMPORT_HISTORY_TASKS);
        } catch (Exception e) {
            setStatus("无法打开文件选择器: " + e.getMessage());
            log.append("打开历史跑步数据文件选择器失败: " + e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_CAPTURE && resultCode == RESULT_OK && data != null) {
            importCapture(data.getData());
        } else if (requestCode == REQUEST_IMPORT_HISTORY_TASKS && resultCode == RESULT_OK && data != null) {
            importHistoryTasks(data.getData());
        }
    }

    private void importHistoryTasks(Uri uri) {
        try {
            String text = readUri(uri);
            TaskRepository.ImportResult result = taskRepository.importHistoryText(displayName(uri), text);
            reload();
            String preview = result.message
                    + "\n候选数据: " + result.candidates
                    + "\n保存成功: " + result.saved
                    + "\n保存目录: " + result.directory
                    + "\n当前可用跑步数据: " + tasks.size();
            new AlertDialog.Builder(this)
                    .setTitle("历史跑步数据导入结果")
                    .setMessage(preview)
                    .setPositiveButton("查看数据", (dialog, which) -> showTasks())
                    .setNegativeButton("关闭", null)
                    .show();
            setStatus(result.saved > 0 ? "历史跑步数据已导入" : "未导入有效历史跑步数据");
            log.append("导入历史跑步数据: " + preview.replace('\n', ' '));
        } catch (Exception e) {
            setStatus("导入历史跑步数据失败: " + e.getMessage());
            log.append("导入历史跑步数据失败: " + e);
        }
    }

    private void importCapture(Uri uri) {
        try {
            String text = readUri(uri);
            CaptureParseResult result = PcapTextParser.parse(text);
            CandidateConfig candidate = result.best;
            ConfigValidationResult validation = ConfigValidator.validate(candidateToConfig(candidate, config));
            String preview = "文件: " + displayName(uri)
                    + "\n候选请求: " + result.candidates.size()
                    + "\n最佳请求: " + candidate.requestLine
                    + "\nHost: " + candidate.requestHost
                    + "\nschoolHost: " + candidate.schoolHost
                    + "\ntoken: " + com.runlog.data.Masking.value(candidate.token)
                    + "\ndeviceId: " + com.runlog.data.Masking.value(candidate.deviceId)
                    + "\ndeviceName: " + candidate.deviceName
                    + "\nuuid: " + com.runlog.data.Masking.value(candidate.uuid)
                    + "\n\n" + validation.summary();
            new AlertDialog.Builder(this)
                    .setTitle("抓包配置解析结果")
                    .setMessage(preview)
                    .setPositiveButton(validation.valid ? "保存为当前配置" : "保存为草稿", (dialog, which) -> {
                        config.merge(candidate);
                        config.draft = !validation.valid;
                        saveConfig("导入抓包配置，valid=" + validation.valid + "，candidateCount=" + result.candidates.size());
                        setStatus(validation.valid ? "抓包配置已保存" : "字段不完整，已保存为草稿");
                        showProfile();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Exception e) {
            setStatus("导入失败: " + e.getMessage());
            log.append("导入抓包失败: " + e);
        }
    }

    private void extractHistoryOnline() {
        if (extractingHistory) {
            setStatus("历史数据正在提取");
            return;
        }
        reload();
        ConfigValidationResult validation = ConfigValidator.validate(config);
        if (!validation.valid || config.draft) {
            historyProgressText = "配置校验失败\n" + validation.summary();
            setStatus("配置未通过，不能在线提取历史数据");
            log.append("拒绝在线提取历史数据: " + validation.summary().replace('\n', ' '));
            showTasks();
            return;
        }
        extractingHistory = true;
        historyProgressText = "准备连接历史接口";
        setStatus("正在在线提取历史数据");
        log.append("开始在线提取历史数据");
        showTasks();
        final AppConfig snapshot = config.copy();
        new Thread(() -> {
            try {
                HistoryExtractionResult extracted = new HistoryClient(snapshot).extract(message -> {
                    historyProgressText = message;
                    log.append("历史提取进度: " + message);
                    runOnUiThread(this::updateHistoryProgressViews);
                });
                TaskRepository.ImportResult saved = taskRepository.saveExtractedTasks("在线历史接口", extracted.taskJsons);
                runOnUiThread(() -> {
                    reload();
                    historyProgressText = "提取完成：学期 " + extracted.terms
                            + "，列表记录 " + extracted.listRecords
                            + "，详情 " + extracted.detailRecords
                            + "，保存 " + saved.saved;
                    setStatus(saved.saved > 0 ? "在线历史数据已提取" : "未保存到有效历史跑步数据");
                    log.append("在线历史数据提取完成: " + historyProgressText + "，目录=" + saved.directory);
                    new AlertDialog.Builder(this)
                            .setTitle("在线历史数据提取结果")
                            .setMessage(historyProgressText + "\n" + saved.message + "\n保存目录: " + saved.directory)
                            .setPositiveButton("查看数据", (dialog, which) -> showTasks())
                            .setNegativeButton("关闭", null)
                            .show();
                    showTasks();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    String message = errorMessage(e);
                    historyProgressText = "提取失败: " + message;
                    setStatus("在线提取历史数据失败: " + message);
                    log.append("在线提取历史数据失败: " + message);
                    showTasks();
                });
            } finally {
                extractingHistory = false;
            }
        }).start();
    }

    private AppConfig candidateToConfig(CandidateConfig candidate, AppConfig base) {
        AppConfig merged = base.copy();
        merged.merge(candidate);
        return merged;
    }

    private void checkCurrentConfig() {
        reload();
        ConfigValidationResult validation = ConfigValidator.validate(config);
        setStatus(validation.summary());
        log.append("配置检测: " + validation.summary().replace('\n', ' '));
        if (validation.valid) {
            config.draft = false;
            saveConfig("基础配置检测通过");
        }
    }

    private void startRunFlow() {
        if (runningForTest) {
            setStatus("跑步流程正在执行");
            return;
        }
        new Thread(() -> {
            try {
                reload();
                ConfigValidationResult validation = ConfigValidator.validate(config);
                if (!validation.valid || config.draft) {
                    runOnUiThread(() -> {
                        setStatus("Run blocked: config is invalid or still draft\n" + validation.summary());
                        log.append("拒绝开始跑步: " + validation.summary().replace('\n', ' '));
                        runProgressText = "Config validation failed\n" + validation.summary();
                        showHome();
                    });
                    return;
                }
                TaskSummary task = taskForRun();
                if (task == null) {
                    runOnUiThread(() -> {
                        setStatus("Run blocked: 需要先导入或提取跑步数据");
                        runProgressText = "No task JSON available. Import history data first.";
                        log.append("拒绝开始跑步：没有可用 task JSON");
                        showHome();
                    });
                    return;
                }
                AppConfig snapshot = config.copy();
                TaskSummary selectedForRun = task;
                runningForTest = true;
                runStopRequested = false;
                runProgressText = "0% run requested";
                runOnUiThread(() -> {
                    setStatus("Run started");
                    log.append("开始 Kotlin 原生运行流程，选择=" + snapshot.pickMode + "，任务=" + selectedForRun.fileName);
                    showHome();
                });
                RunClient client = new RunClient(snapshot);
                progress(5, "loading task " + selectedForRun.fileName);
                String taskJson = taskRepository.readTaskJson(selectedForRun);
                RunClient.RunResult result = client.runTable(taskJson, this::progress, () -> runStopRequested);
                taskRepository.markUsed(selectedForRun);
                progress(100, "Run submitted. recordId=" + result.recordId + ", splits=" + result.splitCount + ", response=" + result.responsePreview);
                log.append("Run submitted: recordId=" + result.recordId + ", splits=" + result.splitCount + ", response=" + result.responsePreview);
            } catch (Exception e) {
                progress(0, "Run failed: " + errorMessage(e));
                log.append("Run failed: " + errorMessage(e));
            } finally {
                runningForTest = false;
                runStopRequested = false;
                runOnUiThread(() -> {
                    setStatus("Run flow finished");
                    updateHomeProgressViews();
                });
            }
        }).start();
    }

    private void stopRunFlow() {
        if (!runningForTest) {
            setStatus("当前没有运行中的跑步流程");
            log.append("停止跑步：当前无运行流程");
            return;
        }
        runStopRequested = true;
        runProgressText = runProgressText + "\n已请求停止...";
        setStatus("正在停止跑步流程");
        log.append("停止跑步：已请求停止");
        updateHomeProgressViews();
    }

    private boolean shouldStop() {
        if (!runStopRequested) return false;
        progress(0, "流程已停止，未提交真实跑步数据");
        log.append("跑步流程已停止");
        runningForTest = false;
        return true;
    }

    private void progress(int percent, String message) {
        runProgressText = percent + "% " + message;
        log.append("跑步进度: " + runProgressText.replace('\n', ' '));
        runOnUiThread(this::updateHomeProgressViews);
    }

    private void sleepStep() throws InterruptedException {
        Thread.sleep(450);
    }

    private void saveConfig(String event) {
        try {
            File backup = configStore.save(config, true);
            log.append(event + "，备份=" + (backup == null ? "-" : backup.getName()));
        } catch (Exception e) {
            setStatus("保存配置失败: " + e.getMessage());
            log.append("保存配置失败: " + e);
        }
    }

    private void reload() {
        config = configStore.load();
        ensureProtocolDefaults(config);
        tasks = taskRepository.loadAll();
    }

    private void ensureProtocolDefaults(AppConfig target) {
        if (blank(target.publicKey)) target.publicKey = AppConfig.DEFAULT_PUBLIC_KEY;
        if (blank(target.cipherKey)) target.cipherKey = AppConfig.DEFAULT_CIPHER_KEY;
        if (blank(target.cipherKeyEncrypted)) target.cipherKeyEncrypted = AppConfig.DEFAULT_CIPHER_KEY_ENCRYPTED;
        if (blank(target.md5Key)) target.md5Key = AppConfig.DEFAULT_MD5_KEY;
    }

    private TaskSummary selectedTask() {
        if (!"SPECIFIED".equals(config.pickMode) || blank(config.selectedTask)) return null;
        for (TaskSummary task : tasks) {
            if (task.fileName.equals(config.selectedTask)) return task;
        }
        return null;
    }

    private TaskSummary taskForRun() {
        TaskSummary selected = selectedTask();
        if (selected != null) return selected;
        if (tasks == null || tasks.isEmpty()) return null;
        if ("SPECIFIED".equals(config.pickMode)) return null;
        if ("RANDOM".equals(config.pickMode)) {
            return tasks.get(new java.util.Random().nextInt(tasks.size()));
        }
        return tasks.get(0);
    }

    private String modeText() {
        return pickModeText()
                + (config.driftEnabled ? " / 漂移开启" : " / 漂移关闭");
    }

    private String runModeText() {
        return "历史数据模式";
    }

    private String pickModeText() {
        return "SPECIFIED".equals(config.pickMode) ? "指定数据" : "随机数据";
    }

    private int totalOccurrences() {
        int total = 0;
        if (tasks == null) return 0;
        for (TaskSummary task : tasks) {
            total += Math.max(1, task.duplicateCount);
        }
        return total;
    }

    private List<TaskSummary> visibleTasksForData() {
        if (tasks == null || tasks.isEmpty()) {
            dataVisibleNames.clear();
            return new ArrayList<>();
        }
        if (tasks.size() <= 5) {
            dataVisibleNames.clear();
            return new ArrayList<>(tasks);
        }
        if (!isVisibleGroupValid()) {
            chooseRandomDataGroup();
        }
        List<TaskSummary> out = new ArrayList<>();
        for (String name : dataVisibleNames) {
            for (TaskSummary task : tasks) {
                if (task.fileName.equals(name)) {
                    out.add(task);
                    break;
                }
            }
        }
        if (out.isEmpty()) {
            chooseRandomDataGroup();
            return visibleTasksForData();
        }
        return out;
    }

    private boolean isVisibleGroupValid() {
        if (dataVisibleNames.isEmpty()) return false;
        int valid = 0;
        for (String name : dataVisibleNames) {
            for (TaskSummary task : tasks) {
                if (task.fileName.equals(name)) {
                    valid++;
                    break;
                }
            }
        }
        return valid > 0;
    }

    private void chooseRandomDataGroup() {
        dataVisibleNames.clear();
        if (tasks == null || tasks.isEmpty()) return;
        List<TaskSummary> shuffled = new ArrayList<>(tasks);
        Collections.shuffle(shuffled);
        int limit = Math.min(5, shuffled.size());
        for (int i = 0; i < limit; i++) {
            dataVisibleNames.add(shuffled.get(i).fileName);
        }
    }

    private String readUri(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalArgumentException("无法读取文件");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private String displayName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        }
        return Objects.toString(uri, "");
    }

    private TextView header(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(21);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(COLOR_INK);
        view.setPadding(dp(2), dp(12), dp(2), dp(6));
        return view;
    }

    private TextView info(String label, String value) {
        TextView view = new TextView(this);
        view.setText(label + "\n" + value);
        view.setTextSize(14);
        view.setTextColor(COLOR_INK);
        view.setPadding(dp(12), dp(11), dp(12), dp(11));
        view.setBackground(rounded(COLOR_CARD, COLOR_LINE, dp(8)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(5), 0, dp(7));
        view.setLayoutParams(lp);
        return view;
    }

    private Button primaryButton(String text, View.OnClickListener listener) {
        return actionButton("▷  " + text, COLOR_PRIMARY, 0xFFFFFFFF, listener);
    }

    private Button secondaryButton(String text, View.OnClickListener listener) {
        return actionButton("✓  " + text, COLOR_PRIMARY_SOFT, COLOR_PRIMARY_DEEP, listener);
    }

    private Button neutralButton(String text, View.OnClickListener listener) {
        return actionButton("□  " + text, 0xFFF0F4F2, 0xFF3C5952, listener);
    }

    private Button dangerButton(String text, View.OnClickListener listener) {
        return actionButton(text, COLOR_CORAL, 0xFFFFFFFF, listener);
    }

    private Button actionButton(String text, int bgColor, int fgColor, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(fgColor);
        button.setBackground(rounded(bgColor, 0, dp(8)));
        button.setMinHeight(dp(46));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.setMargins(dp(3), dp(6), dp(3), dp(6));
        button.setLayoutParams(lp);
        return button;
    }

    private Button navButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setOnClickListener(listener);
        return button;
    }

    private void disableNav(boolean disabled) {
        if (homeNavButton != null) homeNavButton.setEnabled(!disabled);
        if (dataNavButton != null) dataNavButton.setEnabled(!disabled);
        if (profileNavButton != null) profileNavButton.setEnabled(!disabled);
    }

    private void updateNav() {
        styleNav(homeNavButton, activeTab == TAB_HOME);
        styleNav(dataNavButton, activeTab == TAB_DATA);
        styleNav(profileNavButton, activeTab == TAB_PROFILE);
    }

    private void styleNav(Button button, boolean active) {
        if (button == null) return;
        button.setTextColor(active ? COLOR_PRIMARY_DEEP : 0xFF60766F);
        button.setBackground(rounded(active ? COLOR_PRIMARY_SOFT : 0x00000000, 0, dp(9)));
    }

    private void setSubtitle(String text) {
        if (appSubtitle != null) appSubtitle.setText(text);
    }

    private LinearLayout pageHeader(String title, String caption, String state) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(4), dp(2), dp(8));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(22);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(COLOR_INK);
        titleView.setIncludeFontPadding(false);
        TextView captionView = new TextView(this);
        captionView.setText(caption);
        captionView.setTextSize(12);
        captionView.setTextColor(COLOR_MUTED);
        left.addView(titleView);
        left.addView(captionView);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(pill(state, COLOR_PRIMARY_SOFT, COLOR_PRIMARY_DEEP));
        return row;
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.setBackground(rounded(COLOR_CARD, COLOR_LINE, dp(8)));
        box.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(5), 0, dp(7));
        box.setLayoutParams(lp);
        return box;
    }

    private TextView cardTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(16);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(COLOR_INK);
        return view;
    }

    private TextView cardCopy(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(COLOR_MUTED);
        view.setPadding(0, dp(5), 0, 0);
        return view;
    }

    private TextView metaLabel(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(12);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(COLOR_MUTED);
        return view;
    }

    private TextView strongText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(COLOR_INK);
        return view;
    }

    private LinearLayout chipRow(String[] chips) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(7), 0, 0);
        for (String chip : chips) {
            row.addView(pill(chip, COLOR_PRIMARY_SOFT, COLOR_PRIMARY_DEEP));
        }
        return row;
    }

    private TextView pill(String text, int bgColor, int fgColor) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(11);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(fgColor);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), dp(4), dp(8), dp(4));
        view.setBackground(rounded(bgColor, 0, dp(8)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, dp(6), 0);
        view.setLayoutParams(lp);
        return view;
    }

    private GridLayout statGrid(int columns) {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(columns);
        grid.setPadding(0, dp(2), 0, dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(4), 0, dp(5));
        grid.setLayoutParams(lp);
        return grid;
    }

    private LinearLayout statCell(String label, String value) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(10), dp(9), dp(10), dp(9));
        cell.setBackground(rounded(0xFFFAFDFC, COLOR_LINE, dp(7)));
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = -2;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        cell.setLayoutParams(lp);
        cell.addView(metaLabel(label));
        cell.addView(strongText(value));
        return cell;
    }

    private LinearLayout chipCard(String label, String value) {
        LinearLayout box = card();
        box.addView(metaLabel(label));
        box.addView(chipRow(value.split(" / ")));
        return box;
    }

    private LinearLayout phaseCard(String title, String copy) {
        LinearLayout box = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = new TextView(this);
        icon.setText("▤");
        icon.setTextSize(20);
        icon.setTextColor(0xFF6B4A10);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(0xFFFFF2CC, 0, dp(8)));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        iconLp.setMargins(0, 0, dp(10), 0);
        row.addView(icon, iconLp);
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(cardTitle(title));
        text.addView(cardCopy(copy));
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(row);
        return box;
    }

    private GridLayout toolGrid() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(4), 0, dp(5));
        grid.setLayoutParams(lp);
        return grid;
    }

    private Button toolButton(String title, String sub, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(title + "\n" + sub);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(COLOR_INK);
        button.setBackground(rounded(COLOR_CARD, COLOR_LINE, dp(8)));
        button.setOnClickListener(listener);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = dp(68);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        button.setLayoutParams(lp);
        return button;
    }

    private LinearLayout progressCard(String title, String copy, int progress) {
        return progressCard(title, copy, progress, false, false);
    }

    private LinearLayout progressCard(String title, String copy, int progress, boolean bindRun, boolean bindHistory) {
        LinearLayout box = card();
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.HORIZONTAL);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.addView(cardTitle(title), new LinearLayout.LayoutParams(0, -2, 1));
        TextView pct = pill(Math.max(0, Math.min(100, progress)) + "%", COLOR_PRIMARY_SOFT, COLOR_PRIMARY_DEEP);
        text.addView(pct);
        box.addView(text);
        TextView copyView = cardCopy(copy);
        box.addView(copyView);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(Math.max(0, Math.min(100, progress)));
        bar.setProgressTintList(ColorStateList.valueOf(COLOR_PRIMARY));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFFDDE8E4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(8));
        lp.setMargins(0, dp(10), 0, 0);
        box.addView(bar, lp);
        if (bindRun) {
            homeProgressCopy = copyView;
            homeProgressBar = bar;
        }
        if (bindHistory) {
            historyProgressCopy = copyView;
            historyProgressBar = bar;
        }
        return box;
    }

    private void clearPageBindings() {
        homeProgressRing = null;
        homeProgressCopy = null;
        homeProgressBar = null;
        historyProgressCopy = null;
        historyProgressBar = null;
    }

    private void updateHomeProgressViews() {
        int percent = Math.max(0, Math.min(100, progressPercent(runProgressText)));
        if (homeProgressRing != null) homeProgressRing.setText(runningForTest ? percent + "%" : "72%");
        if (homeProgressCopy != null) homeProgressCopy.setText(runProgressText);
        if (homeProgressBar != null) homeProgressBar.setProgress(Math.max(8, percent));
    }

    private void updateHistoryProgressViews() {
        int percent = Math.max(0, Math.min(100, progressPercent(historyProgressText)));
        if (historyProgressCopy != null) historyProgressCopy.setText(historyProgressText);
        if (historyProgressBar != null) historyProgressBar.setProgress(percent);
    }

    private View configRow(String key, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));
        TextView k = metaLabel(key);
        TextView v = strongText(value);
        v.setTextSize(13);
        row.addView(k, new LinearLayout.LayoutParams(dp(98), -2));
        row.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private LinearLayout dangerCard(String title, String copy, View.OnClickListener listener) {
        LinearLayout box = card();
        box.setBackground(rounded(0xFFFFFAF9, 0xFFF4C8BF, dp(8)));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView t = cardTitle(title);
        t.setTextColor(0xFF8A2F20);
        text.addView(t);
        TextView c = cardCopy(copy);
        c.setTextColor(0xFF8A2F20);
        text.addView(c);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(dangerButton("清除", listener), new LinearLayout.LayoutParams(dp(92), dp(46)));
        box.addView(row);
        return box;
    }

    private GradientDrawable rounded(int color, int strokeColor, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private GradientDrawable roundedGradient(int startColor, int endColor, int radius) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{startColor, endColor});
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private void setStatus(String message) {
        status.setText(message);
    }

    private static String errorMessage(Exception e) {
        if (e == null) return "未知错误";
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return e.getClass().getName();
        }
        return e.getClass().getSimpleName() + ": " + message;
    }

    private int progressPercent(String text) {
        if (text == null) return 0;
        int percentIndex = text.indexOf('%');
        if (percentIndex <= 0) return 0;
        int start = percentIndex - 1;
        while (start >= 0 && Character.isDigit(text.charAt(start))) {
            start--;
        }
        try {
            return Integer.parseInt(text.substring(start + 1, percentIndex));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String shortProgress(String text) {
        if (blank(text)) return "等待";
        String oneLine = text.replace('\n', ' ').trim();
        return oneLine.length() <= 14 ? oneLine : oneLine.substring(0, 14) + "...";
    }

    private String formatTimestamp(long value) {
        if (value <= 0) return "未更新时间";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(value));
    }

    private int configHealthPercent() {
        int score = 0;
        if (!blank(config.schoolHost)) score += 20;
        if (!blank(config.token)) score += 25;
        if (!blank(config.deviceId)) score += 20;
        if (!config.draft) score += 20;
        if (!blank(config.cipherKey)) score += 15;
        return Math.min(100, score);
    }

    private String configHealthText() {
        int score = configHealthPercent();
        if (score >= 80) return "设备、Token、网络配置状态良好";
        if (score >= 50) return "配置基本可用，建议执行一次检测";
        return "配置仍不完整，建议登录或导入抓包配置";
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String shorten(String value) {
        if (value == null || value.trim().isEmpty()) return "(empty)";
        return value.length() <= 16 ? value : value.substring(0, 16) + "...";
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
