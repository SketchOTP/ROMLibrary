package com.animus.romshelf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.content.ActivityNotFoundException;
import android.text.InputType;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.TimeZone;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = 0xff101118, CARD = 0xff1d202b, TEXT = 0xfff5f3ff;
    private static final int SECONDARY = 0xffaaa5bb, PURPLE = 0xff9c75ef, GREEN = 0xff66d9a3;
    private static final int PICK_ROMS = 210, PICK_SHARED = 211, PICK_COVER = 213, EXPORT_SAVE = 214, IMPORT_SAVE = 215;
    private static final int PICK_ART_FOLDER = 216, PICK_ROM_FILES = 217, PICK_ART_FILES = 218;
    private static final int ART_ACTION_NONE = 0, ART_ACTION_IMPORT = 1, ART_ACTION_CHANGE_COVER = 2;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean scanning = new AtomicBoolean(false);
    private final List<Game> games = new ArrayList<>();
    private FileStore store;
    private CoverLoader covers;
    private SharedPreferences appPrefs;
    private boolean newestFirst = true;
    private Game chosenCoverGame;
    private int artActionAfterFolder = ART_ACTION_NONE;
    private boolean addRomsAfterFolderGrant;
    private Game pendingSaveGame;
    private String pendingSaveSourceKey = FileStore.SHARED;
    private String pendingSaveDestinationKey = FileStore.SHARED;
    private GridLayout grid;
    private TextView summary, folderStatus;
    private EditText search;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        clearUnusedAiCredentials();
        clearLegacyLauncherChoices();
        store = new FileStore(this);
        covers = new CoverLoader(this);
        covers.clearRetiredFeatureState();
        appPrefs = getSharedPreferences("rom_shelf_launcher", MODE_PRIVATE);
        migratePendingPizzaSession();
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        buildUi();
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        recordPendingPizzaSession();
        if (store != null && grid != null) {
            if (games.isEmpty()) refresh();
            else refreshSaveState();
        }
    }

    private void migratePendingPizzaSession() {
        String oldGame = appPrefs.getString("pending_screenshot_game", "");
        long oldLaunchTime = appPrefs.getLong("pending_screenshot_after", 0);
        if (!oldGame.isEmpty() && oldLaunchTime > 0 && !appPrefs.contains("pending_pizza_game")) {
            appPrefs.edit().putString("pending_pizza_game", oldGame)
                .putLong("pending_pizza_launched_at", oldLaunchTime)
                .remove("pending_screenshot_game").remove("pending_screenshot_after")
                .remove("pending_pizza_session_recorded").apply();
        } else {
            appPrefs.edit().remove("pending_screenshot_game").remove("pending_screenshot_after")
                .remove("pending_pizza_session_recorded").apply();
        }
    }

    private void recordPendingPizzaSession() {
        if (appPrefs == null) return;
        String gameKey = appPrefs.getString("pending_pizza_game", "");
        long launchedAt = appPrefs.getLong("pending_pizza_launched_at", 0);
        if (gameKey.isEmpty() || launchedAt <= 0) return;
        long returnedAt = Math.max(System.currentTimeMillis(), launchedAt);
        appPrefs.edit().putLong(pizzaSessionKey(gameKey), returnedAt)
            .putLong(pizzaQuickStateKey(gameKey), returnedAt)
            .remove("pending_pizza_game").remove("pending_pizza_launched_at").apply();
        for (Game candidate : games) {
            if (candidate.key.equals(gameKey)) {
                candidate.pizzaSessionStamp = returnedAt;
                candidate.pizzaQuickStateStamp = returnedAt;
            }
        }
    }

    private void buildUi() {
        LinearLayout base = new LinearLayout(this);
        base.setOrientation(LinearLayout.VERTICAL);
        base.setPadding(dp(16), dp(14), dp(16), 0);
        base.setBackgroundColor(BACKGROUND);
        // Android 15+ enforces edge-to-edge for targetSdk 35. Keep the app's
        // controls out of the system bars so Settings and the bottom grid remain
        // reachable on current Pixel builds.
        base.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(16), dp(14) + insets.getSystemWindowInsetTop(), dp(16),
                insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        setContentView(base);
        base.requestApplyInsets();

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        base.addView(top, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView brand = text("ROM Shelf", 24, TEXT, true);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView sorter = pill("Recent ▾", CARD, SECONDARY);
        sorter.setPadding(dp(10), dp(10), dp(10), dp(10));
        sorter.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Sort library")
            .setItems(new String[] {"Recent saves (battery save + Pizza quickstate)", "Title A–Z"}, (d, which) -> {
                newestFirst = which == 0;
                sorter.setText(newestFirst ? "Recent ▾" : "A–Z ▾");
                render();
            }).show());
        top.addView(sorter);
        TextView settings = pill("⚙", CARD, SECONDARY);
        settings.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(-2, -2);
        settingsLp.leftMargin = dp(8);
        settings.setOnClickListener(v -> openSettings());
        TextView add = pill("+", PURPLE, Color.WHITE);
        add.setContentDescription("Add ROMs or box art");
        add.setPadding(dp(13), dp(10), dp(13), dp(10));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(-2, -2);
        addLp.leftMargin = dp(8);
        add.setOnClickListener(v -> showAddMenu(add));
        top.addView(add, addLp);
        top.addView(settings, settingsLp);

        summary = text("Choose your ROM folder to start", 13, SECONDARY, false);
        LinearLayout.LayoutParams summaryLp = new LinearLayout.LayoutParams(-1, -2);
        summaryLp.setMargins(0, dp(2), 0, dp(16));
        base.addView(summary, summaryLp);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(TEXT);
        search.setHintTextColor(SECONDARY);
        search.setHint("Search games");
        search.setTextSize(15);
        search.setPadding(dp(16), dp(10), dp(16), dp(10));
        search.setBackground(shape(CARD, dp(15)));
        base.addView(search, new LinearLayout.LayoutParams(-1, dp(48)));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
            public void afterTextChanged(Editable e) { }
        });

        folderStatus = text("ROMs · Shared saves", 12, SECONDARY, false);
        LinearLayout.LayoutParams folderLp = new LinearLayout.LayoutParams(-1, -2);
        folderLp.setMargins(0, dp(14), 0, dp(10));
        base.addView(folderStatus, folderLp);
        folderStatus.setOnClickListener(v -> openSettings());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        base.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        grid = new GridLayout(this);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        grid.setColumnOrderPreserved(false);
        grid.setPadding(0, 0, 0, dp(24));
        scroll.addView(grid, new ScrollView.LayoutParams(-1, -2));
    }

    private void refresh() {
        if (!scanning.compareAndSet(false, true)) return;
        if (summary != null) summary.setText("Scanning your library…");
        io.execute(() -> {
            List<Game> found = null;
            String err = null;
            try {
                found = store.scan();
                for (Game game : found) migrateGamePreferences(game);
            } catch (Exception e) { err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
            final List<Game> done = found;
            final String error = err;
            main.post(() -> {
                scanning.set(false);
                if (error != null) {
                    summary.setText("Could not scan: " + error);
                    toast("Check folder permissions in Folders");
                } else {
                    games.clear();
                    games.addAll(done);
                    render();
                }
            });
        });
    }

    private void refreshSaveState() {
        if (!scanning.compareAndSet(false, true)) return;
        List<Game> updated = new ArrayList<>();
        for (Game game : games) updated.add(game.copyForSaveRefresh());
        io.execute(() -> {
            String error = null;
            try { store.refreshSaveState(updated); }
            catch (Exception e) { error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
            final String failure = error;
            main.post(() -> {
                scanning.set(false);
                if (failure != null) { summary.setText("Could not refresh saves: " + failure); return; }
                games.clear(); games.addAll(updated);
                render();
            });
        });
    }

    private void migrateGamePreferences(Game game) {
        covers.migrateIdentity(game);
        game.pizzaSessionStamp = appPrefs.getLong(pizzaSessionKey(game.key), 0);
        game.pizzaQuickStateStamp = appPrefs.getLong(pizzaQuickStateKey(game.key), 0);
    }

    private String pizzaSessionKey(String gameKey) { return "pizza_session_" + gameKey; }
    private String pizzaQuickStateKey(String gameKey) { return "pizza_quickstate_estimate_" + gameKey; }

    private void clearLegacyLauncherChoices() {
        SharedPreferences preferences = getSharedPreferences("rom_shelf_launcher", MODE_PRIVATE);
        SharedPreferences.Editor edit = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith("game_launcher_")) edit.remove(key);
        }
        String selected = preferences.getString("default_launcher", "");
        boolean supported = false;
        for (String packageId : PACKAGES) if (packageId.equals(selected)) supported = true;
        if (!supported) edit.remove("default_launcher");
        edit.apply();
    }

    private void clearUnusedAiCredentials() {
        getSharedPreferences("chatgpt_connection", MODE_PRIVATE).edit().clear().apply();
        getSharedPreferences("rom_shelf_ai", MODE_PRIVATE).edit().clear().apply();
        try {
            java.security.KeyStore keys = java.security.KeyStore.getInstance("AndroidKeyStore");
            keys.load(null);
            if (keys.containsAlias("rom_shelf_chatgpt_oauth_v03")) keys.deleteEntry("rom_shelf_chatgpt_oauth_v03");
        } catch (Exception ignored) { }
    }

    private void render() {
        if (grid == null || store == null) return;
        grid.removeAllViews();
        int cols = Math.max(2, Math.min(6, (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density / 168f)));
        grid.setColumnCount(cols);
        int widthDp = (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
        int cardWidth = (widthDp - 32 - (cols - 1) * 12) / cols;
        String query = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int shown = 0, saved = 0;
        boolean savesLinked = store.getUri(FileStore.SHARED) != null;
        List<Game> ordered = new ArrayList<>(games);
        if (newestFirst) Collections.sort(ordered, (a,b) -> {
            int t = Long.compare(b.lastPlayedEstimate(), a.lastPlayedEstimate());
            return t != 0 ? t : a.title.compareToIgnoreCase(b.title);
        });
        else Collections.sort(ordered, Comparator.comparing(g -> g.title.toLowerCase(Locale.ROOT)));
        for (Game g : ordered) {
            if ("SAVED".equals(g.saveStatus())) saved++;
            if (!g.title.toLowerCase(Locale.ROOT).contains(query) && !g.stem.toLowerCase(Locale.ROOT).contains(query)) continue;
            addGameCard(g, cardWidth, cols, shown++, savesLinked);
        }
        folderStatus.setText("ROMs " + mark(FileStore.ROMS) + "   Shared saves " + mark(FileStore.SHARED) +
            "   ·   Tap to play in Pizza Boy · Hold to edit");
        if (store.getUri(FileStore.ROMS) == null) {
            summary.setText("Choose a ROM folder to build your library");
            TextView select = pill("Choose ROM folder", PURPLE, Color.WHITE);
            select.setOnClickListener(v -> pickFolder(PICK_ROMS));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.columnSpec = GridLayout.spec(0, cols);
            lp.topMargin = dp(25);
            grid.addView(select, lp);
        } else {
            summary.setText(games.size() + " games · " + saved + " with saves" +
                (store.duplicateCount() > 0 ? " · " + store.duplicateCount() + " duplicate" + (store.duplicateCount() == 1 ? "" : "s") + " hidden" : ""));
            if (shown == 0) {
                TextView empty = text(games.isEmpty() ? "No .gba or .zip games found" : "No games match your search", 15, SECONDARY, false);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.columnSpec = GridLayout.spec(0, cols);
                lp.topMargin = dp(24);
                grid.addView(empty, lp);
            }
        }
    }

    private String mark(String key) { return store.getUri(key) == null ? "○" : "●"; }

    private void addGameCard(Game game, int widthDp, int columns, int index, boolean savesLinked) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setBackground(shape(CARD, dp(14)));
        tile.setClipToOutline(true);
        tile.setOnClickListener(v -> launchGame(game));
        tile.setOnLongClickListener(v -> { openGame(game); return true; });
        int width = dp(widthDp);
        int coverHeight = (int) (width * 1.27f);
        FrameLayout art = new FrameLayout(this);
        art.setBackground(shape(0xff302840, dp(14)));
        tile.addView(art, new LinearLayout.LayoutParams(-1, coverHeight));

        TextView placeholder = text("GBA", 29, 0xff78639b, true);
        placeholder.setGravity(Gravity.CENTER);
        art.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setContentDescription("Cover art for " + game.title);
        art.addView(image, new FrameLayout.LayoutParams(-1, -1));
        covers.show(game, image);

        String saveLabel = game.saveStatus();
        if (!"SAVED".equals(saveLabel) && !savesLinked) saveLabel = "NOT CHECKED";
        TextView badge = text(saveLabel, 9, ("SAVED".equals(saveLabel)) ? BACKGROUND : TEXT, true);
        badge.setPadding(dp(7), dp(4), dp(7), dp(4));
        badge.setBackground(shape(("SAVED".equals(saveLabel)) ? GREEN : 0xb0181820, dp(7)));
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        badgeLp.setMargins(dp(7), dp(8), dp(7), 0);
        art.addView(badge, badgeLp);

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setPadding(dp(9), dp(10), dp(9), dp(10));
        tile.addView(textBox, new LinearLayout.LayoutParams(-1, dp(108)));
        TextView title = text(game.title, 13, TEXT, true);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textBox.addView(title);
        TextView version = text(game.version, 11, SECONDARY, false);
        version.setMaxLines(1);
        version.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(-1, -2);
        vLp.setMargins(0, dp(4), 0, 0);
        textBox.addView(version, vLp);
        TextView recent = text(game.lastPlayedEstimate() > 0 ? activityDate(game) :
            (("SAVED".equals(game.saveStatus())) ? "Battery save date unknown" : "Not played yet"), 10, game.lastPlayedEstimate() > 0 ? GREEN : SECONDARY, false);
        recent.setMaxLines(2);
        recent.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams recentLp = new LinearLayout.LayoutParams(-1, -2);
        recentLp.topMargin = dp(5);
        textBox.addView(recent, recentLp);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = width;
        lp.height = -2;
        lp.columnSpec = GridLayout.spec(index % columns);
        lp.rowSpec = GridLayout.spec(index / columns);
        lp.setMargins(0, 0, dp(index % columns == columns - 1 ? 0 : 12), dp(12));
        grid.addView(tile, lp);
    }

    private String activityDate(Game game) {
        long millis = game.lastPlayedEstimate();
        String source = game.activitySource();
        return easternDateFormat("'" + source + "' MMM d, yyyy · h:mm a z").format(new Date(millis));
    }
    private String fullDate(long millis) {
        return millis <= 0 ? "Unknown (folder did not provide timestamp)" :
            easternDateFormat("MMM d, yyyy h:mm a z").format(new Date(millis));
    }
    private SimpleDateFormat easternDateFormat(String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("America/New_York"));
        return format;
    }

    private static final String[] EMULATORS = {"Pizza Boy GBA Pro", "Pizza Boy GBA", "Pizza Boy GBA Free"};
    private static final String[] PACKAGES = {"it.dbtecno.pizzaboygbapro", "it.dbtecno.pizzaboygba", "it.dbtecno.pizzaboygbafree"};

    private String launcherFor(Game game) {
        return resolvedDefaultLauncher();
    }
    private String resolvedDefaultLauncher() {
        String current = appPrefs.getString("default_launcher", "");
        if (isEmulatorInstalled(current)) return current;
        // Old installs can retain a package choice that is no longer installed.
        // Pick the first installed supported emulator (Pizza Boy Pro has priority)
        // and persist it so the next launch uses the same working route.
        for (String candidate : PACKAGES) {
            if (isEmulatorInstalled(candidate)) {
                appPrefs.edit().putString("default_launcher", candidate).apply();
                return candidate;
            }
        }
        return "";
    }
    private boolean isEmulatorInstalled(String packageId) {
        if (packageId == null || packageId.isEmpty()) return false;
        boolean supported = false;
        for (String candidate : PACKAGES) if (candidate.equals(packageId)) { supported = true; break; }
        if (!supported) return false;
        try {
            getPackageManager().getApplicationInfo(packageId, 0);
            return true;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { return false; }
    }
    private List<Integer> installedEmulatorIndexes() {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < PACKAGES.length; i++) if (isEmulatorInstalled(PACKAGES[i])) indexes.add(i);
        return indexes;
    }
    private String launcherLabel(String packageId) {
        for (int i=0;i<PACKAGES.length;i++) if (PACKAGES[i].equals(packageId)) return EMULATORS[i];
        return "Pizza Boy";
    }
    private void chooseEmulator(Game game, boolean launchAfterSelection) {
        String current = game == null ? resolvedDefaultLauncher() : launcherFor(game);
        List<Integer> installed = installedEmulatorIndexes();
        if (installed.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("No supported emulator found")
                .setMessage("Install Pizza Boy GBA, then choose the installed Pizza Boy version here.")
                .setPositiveButton("OK", null).show();
            return;
        }
        String[] labels = new String[installed.size()];
        int selected = -1;
        for (int row = 0; row < installed.size(); row++) {
            int index = installed.get(row);
            labels[row] = EMULATORS[index];
            if (PACKAGES[index].equals(current)) selected = row;
        }
        new AlertDialog.Builder(this)
            .setTitle("Pizza Boy version")
            .setSingleChoiceItems(labels, selected, (dialog, row) -> {
                String pkg = PACKAGES[installed.get(row)];
                appPrefs.edit().putString("default_launcher",pkg).apply();
                dialog.dismiss();
                if (launchAfterSelection && game != null) launchGame(game);
                else toast("Pizza Boy version: " + launcherLabel(pkg));
            }).setNegativeButton("Cancel", null).show();
    }
    private void launchGame(Game game) {
        String pkg = launcherFor(game);
        if (pkg.isEmpty()) { chooseEmulator(game, true); return; }
        launchRomIntent(game, pkg);
    }
    private void launchRomIntent(Game game, String pkg) {
        Uri rom = game.rom.getUri();
        try {
            // Pizza Boy Pro's exported MainActivity does not declare a data/MIME
            // filter, so address its ROM-loading entry point explicitly.
            String[] mimeTypes = {"application/octet-stream", "application/x-gba-rom", "application/zip", "*/*"};
            Exception lastFailure = null;
            for (String mime : mimeTypes) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(rom, mime);
                intent.setPackage(pkg);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.setClipData(ClipData.newRawUri("GBA ROM", rom));
                if ("it.dbtecno.pizzaboygbapro".equals(pkg)) {
                    intent.setClassName(pkg, pkg + ".MainActivity");
                }
                try {
                    appPrefs.edit().putString("pending_pizza_game", game.key)
                        .putLong("pending_pizza_launched_at", System.currentTimeMillis()).apply();
                    startActivity(intent);
                    return;
                } catch (ActivityNotFoundException e) {
                    clearPendingPizzaSession();
                    lastFailure = e;
                }
            }
            if (lastFailure != null) throw lastFailure;
        } catch (Exception e) {
            clearPendingPizzaSession();
            // Android emulators do not all expose compatible external-ROM VIEW activities.
            new AlertDialog.Builder(this).setTitle("Could not launch ROM")
                .setMessage(launcherLabel(pkg) + " did not accept the external ROM intent. Install/check that emulator or choose a different one. If it opens but doesn't load, import the game through the emulator first. Your ROM and save are unchanged.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Change emulator",(d,w)->chooseEmulator(game,true)).show();
        }
    }
    private void clearPendingPizzaSession() {
        if (appPrefs != null) appPrefs.edit().remove("pending_pizza_game").remove("pending_pizza_launched_at")
            .remove("pending_screenshot_game").remove("pending_screenshot_after")
            .remove("pending_pizza_session_recorded").apply();
    }
    private void openGame(Game game) {
        List<String> choices = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        addAction(choices, actions, "Play with " + launcherLabel(launcherFor(game)), () -> launchGame(game));
        if (game.sharedSave != null)
            addAction(choices, actions, "Export shared battery save (.sav)", () -> exportSave(game, FileStore.SHARED));
        if (store.getUri(FileStore.SHARED) != null)
            addAction(choices, actions, "Import .sav into shared folder", () -> confirmImport(game, FileStore.SHARED));
        if (store.newestBackup(game, FileStore.SHARED) != null)
            addAction(choices, actions, "Restore shared save backup", () -> confirmRestore(game, FileStore.SHARED));
        addAction(choices, actions, "Edit game name", () -> editDisplayTitle(game));
        addAction(choices, actions, "Change box art", () -> chooseCover(game));
        addAction(choices, actions, "Remove custom cover", () -> { covers.removeCustom(game); render(); });
        addAction(choices, actions, "Save and ROM details", () -> new AlertDialog.Builder(this)
            .setTitle(game.title)
            .setMessage("ROM: " + game.fileName + "\nVersion: " + game.version +
                "\nSave: " + (game.sharedSave == null ? "No .sav found" : game.sharedSave.getName() + " (" + game.sharedSave.length() + " bytes)") +
                "\nMost recent activity (" + game.activitySource() + "): " + fullDate(game.lastPlayedEstimate()) +
                "\nShared battery save: " + (game.sharedSave == null ? "Not found" : fullDate(game.sharedStamp)) +
                "\nPizza quickstate activity estimate: " + (game.pizzaQuickStateStamp > 0 ? fullDate(game.pizzaQuickStateStamp) : "No ROM Shelf session recorded") +
                "\nPizza session returned: " + (game.pizzaSessionStamp > 0 ? fullDate(game.pizzaSessionStamp) : "No ROM Shelf session recorded") +
                "\nSelected emulator: " + launcherLabel(launcherFor(game)) +
                "\n\nRecent uses the newest known marker: the shared battery .sav time or the Pizza quickstate activity estimate recorded when you return to ROM Shelf. Keep Auto Load Stat and Auto Save Stat enabled with slot 1 selected in Pizza Boy. Android blocks ROM Shelf from reading Pizza's private .stat timestamp, so the quickstate time is the session-return time, not a direct state-file timestamp. Pizza manages the actual state file; duplicate ROMs are hidden, not deleted.")
            .setPositiveButton("Close", null).show());
        new AlertDialog.Builder(this).setTitle(game.title)
            .setItems(choices.toArray(new String[0]), (d, i) -> actions.get(i).run()).show();
    }
    private void addAction(List<String> labels, List<Runnable> actions, String title, Runnable action) {
        labels.add(title); actions.add(action);
    }
    private void exportSave(Game game) {
        exportSave(game, FileStore.SHARED);
    }
    private void exportSave(Game game, String sourceKey) {
        pendingSaveGame = game;
        pendingSaveSourceKey = sourceKey;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/octet-stream");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE, game.nameForSave());
        startActivityForResult(intent, EXPORT_SAVE);
    }
    private void confirmImport(Game game, String destinationKey) {
        io.execute(() -> {
            boolean existing;
            try { existing = store.findExactSave(game, destinationKey) != null; }
            catch (Exception e) { main.post(() -> toast(e.getMessage())); return; }
            main.post(() -> new AlertDialog.Builder(this)
                .setTitle(existing ? "Replace existing save?" : "Import .sav file?")
                .setMessage("Choose a compatible .sav file for " + game.title + "." +
                    (existing ? " Existing save will be backed up first." : "") +
                    " Destination: " + folderLabel(destinationKey) + "." +
                    "\n\nSave states are emulator-specific and cannot be transferred this way.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Choose .sav", (d,w) -> {
                    pendingSaveGame = game;
                    pendingSaveDestinationKey = destinationKey;
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.setType("*/*"); intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent, IMPORT_SAVE);
                }).show());
        });
    }
    private void confirmRestore(Game game, String destinationKey) {
        new AlertDialog.Builder(this).setTitle("Restore previous save?")
            .setMessage("Restore the latest backup for " + game.title + " to " + folderLabel(destinationKey) + "? The current save will be backed up first.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Restore", (d,w) -> work(() -> store.restore(game, destinationKey))).show();
    }
    private interface Work { String execute() throws Exception; }
    private void work(Work op) {
        io.execute(() -> {
            String result;
            try { result = op.execute(); }
            catch (Exception e) { result = "Save operation failed: " + e.getMessage(); }
            final String msg=result;
            main.post(() -> { toast(msg); refreshSaveState(); });
        });
    }

    private String folderLabel(String key) {
        return "John GBA / Pizza Boy shared saves";
    }

    private void editDisplayTitle(Game g) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setText(g.title);
        new AlertDialog.Builder(this).setTitle("Display title (ROM stays unchanged)")
            .setView(edit).setNegativeButton("Cancel", null)
            .setPositiveButton("Save", (d,w) -> {
                String name = edit.getText().toString().trim();
                if (name.length() < 2 || name.length() > 90) { toast("Enter a title (2–90 characters)"); return; }
                store.setMetadata(g, name, g.version);
                covers.invalidate(g);
                render();
            }).show();
    }

    private void openSettings() {
        String[] options = {
            "ROM folder " + mark(FileStore.ROMS),
            "John GBA / Pizza Boy shared saves " + mark(FileStore.SHARED),
            "Box art folder · " + covers.artworkFolderName(),
            "Add box art images",
            "Pizza Boy version: " + launcherLabel(resolvedDefaultLauncher()),
            "Rescan collection",
            "ROM Shelf help"
        };
        new AlertDialog.Builder(this).setTitle("ROM Shelf settings")
            .setItems(options, (d,which) -> {
                if (which == 0) pickFolder(PICK_ROMS);
                else if (which == 1) pickFolder(PICK_SHARED);
                else if (which == 2) requestArtworkFolder(ART_ACTION_NONE);
                else if (which == 3) addArtworkFiles();
                else if (which == 4) chooseEmulator(null,false);
                else if (which == 5) refresh();
                else showHelp();
            }).setNegativeButton("Close",null).show();
    }

    private void showAddMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Add ROMs and optional .sav files");
        menu.getMenu().add(0, 2, 1, "Add box art images");
        menu.getMenu().add(0, 3, 2, "Rescan Shelf");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) { startAddRoms(); return true; }
            if (item.getItemId() == 2) { addArtworkFiles(); return true; }
            if (item.getItemId() == 3) { refresh(); return true; }
            return false;
        });
        menu.show();
    }

    private void startAddRoms() {
        if (scanning.get()) { toast("Wait for the current Shelf scan to finish"); return; }
        if (store.getUri(FileStore.ROMS) == null) {
            addRomsAfterFolderGrant = true;
            new AlertDialog.Builder(this).setTitle("Choose your Shelf folder")
                .setMessage("ROM Shelf copies selected .gba/.zip files into this folder. Original files stay where they are.")
                .setNegativeButton("Cancel", (d,w) -> addRomsAfterFolderGrant = false)
                .setPositiveButton("Choose folder", (d,w) -> pickFolder(PICK_ROMS)).show();
            return;
        }
        if (resolvedDefaultLauncher().isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Pizza Boy is not installed")
                .setMessage("Install Pizza Boy GBA to launch games from the Shelf. You can still add ROM files now.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (d,w) -> maybeChooseSaveFolderBeforeAdd()).show();
            return;
        }
        maybeChooseSaveFolderBeforeAdd();
    }

    private void maybeChooseSaveFolderBeforeAdd() {
        if (store.getUri(FileStore.SHARED) != null) { startRomFilePicker(); return; }
        new AlertDialog.Builder(this).setTitle("Link shared saves")
            .setMessage("Select the John GBA / Pizza Boy shared .sav folder once so new games can pick up their newest matching battery save automatically.")
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Add without saves", (d,w) -> startRomFilePicker())
            .setPositiveButton("Choose save folder", (d,w) -> {
                addRomsAfterFolderGrant = true;
                pickFolder(PICK_SHARED);
            }).show();
    }

    private void startRomFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_TITLE, "Choose .gba/.zip games and optional matching .sav files");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(intent, PICK_ROM_FILES); }
        catch (Exception e) { toast("File picker unavailable"); }
    }

    private void addArtworkFiles() {
        if (covers.artworkFolderUri() == null) {
            requestArtworkFolder(ART_ACTION_IMPORT);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        putInitialArtFolder(intent);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(intent, PICK_ART_FILES); }
        catch (Exception e) { toast("Image picker unavailable"); }
    }

    private void requestArtworkFolder(int followUpAction) {
        artActionAfterFolder = followUpAction;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        Uri current = covers.artworkFolderUri();
        if (current == null) current = defaultArtFolderDocumentUri();
        if (current != null) intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialDocumentUri(current));
        try { startActivityForResult(intent, PICK_ART_FOLDER); }
        catch (Exception e) {
            artActionAfterFolder = ART_ACTION_NONE;
            toast("Folder picker unavailable");
        }
    }

    private void chooseCover(Game game) {
        chosenCoverGame = game;
        if (covers.artworkFolderUri() == null) {
            requestArtworkFolder(ART_ACTION_CHANGE_COVER);
            return;
        }
        startCoverPicker();
    }

    private void startCoverPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        putInitialArtFolder(intent);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(intent, PICK_COVER); }
        catch (Exception e) { toast("Image picker unavailable"); }
    }

    private void putInitialArtFolder(Intent intent) {
        Uri folder = covers.artworkFolderUri();
        if (folder == null) folder = defaultArtFolderDocumentUri();
        if (folder != null) intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialDocumentUri(folder));
    }

    private Uri defaultArtFolderDocumentUri() {
        return DocumentsContract.buildDocumentUri("com.android.externalstorage.documents",
            "primary:Pictures/Pizza Boy A Screenshots/ROM Shelf Box Art - Re-add");
    }

    private Uri initialDocumentUri(Uri uri) {
        if (!DocumentsContract.isTreeUri(uri)) return uri;
        try {
            return DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
        } catch (Exception ignored) { return uri; }
    }

    private List<Uri> selectedUris(Intent data) {
        List<Uri> selected = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri item = clip.getItemAt(i).getUri();
                if (item != null && !selected.contains(item)) selected.add(item);
            }
        }
        Uri single = data.getData();
        if (single != null && !selected.contains(single)) selected.add(single);
        return selected;
    }

    private void importSelectedFiles(List<Uri> selected) {
        if (selected.isEmpty()) return;
        List<Game> knownGames = new ArrayList<>(games);
        if (summary != null) summary.setText("Adding games and matching saves…");
        io.execute(() -> {
            String message;
            try { message = store.importSelectedFiles(selected, knownGames); }
            catch (Exception e) { message = "Could not add files: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); }
            final String result = message;
            main.post(() -> {
                toast(result + " · tap to play in Pizza Boy; hold to edit name or box art");
                refresh();
            });
        });
    }

    private void importArtworkFiles(List<Uri> selected) {
        if (selected.isEmpty()) return;
        io.execute(() -> {
            String message;
            try { message = covers.importArtworkFiles(selected); }
            catch (Exception e) { message = "Could not add box art: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); }
            final String result = message;
            main.post(() -> toast(result + " · hold a game and choose Change box art to apply"));
        });
    }

    private void showHelp() {
        new AlertDialog.Builder(this).setTitle("How ROM Shelf works")
            .setMessage("Tap + to add .gba/.zip games and optional matching .sav files. ROM Shelf copies selected games into the ROM folder, skips exact ROM duplicates, and keeps the newest matching shared battery save. Tap a game to launch it through the selected Pizza Boy version. Hold a game to edit its display name or change its box art.\n\nIn Settings, choose the box art folder once and use Add box art images to place more images there. Set Pizza Boy's Save folder in Pizza Boy to the same shared folder selected here. Recent sorting uses the newest known battery-save time or Pizza session-return quickstate estimate in Eastern Time. Pizza's private state files are inaccessible to ROM Shelf, so it cannot change Pizza's save-folder or Auto Load/Auto Save Stat settings or compare private state timestamps; keep automatic slot 1 enabled in Pizza Boy. Exact ROM duplicates are hidden, and source ROMs are never deleted.")
            .setPositiveButton("OK",null).show();
    }
    private void pickFolder(int request) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent,request); } catch (Exception e) { toast("Folder picker unavailable"); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) {
            if (request == PICK_ART_FOLDER) artActionAfterFolder = ART_ACTION_NONE;
            if (request == PICK_ROMS || request == PICK_SHARED) addRomsAfterFolderGrant = false;
            return;
        }
        if (request == PICK_ROM_FILES) {
            importSelectedFiles(selectedUris(data));
            return;
        }
        if (request == PICK_ART_FILES) {
            importArtworkFiles(selectedUris(data));
            return;
        }
        Uri uri = data.getData();
        if (request == PICK_ART_FOLDER) {
            if (uri == null) { artActionAfterFolder = ART_ACTION_NONE; return; }
            try {
                int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                int required = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                if ((flags & required) != required) {
                    throw new SecurityException("Choose a folder that allows reading and adding images");
                }
                getContentResolver().takePersistableUriPermission(uri, flags);
                covers.setArtworkFolderUri(uri);
                int followUp = artActionAfterFolder;
                artActionAfterFolder = ART_ACTION_NONE;
                toast("Box art folder set: " + covers.artworkFolderName());
                if (followUp == ART_ACTION_IMPORT) addArtworkFiles();
                else if (followUp == ART_ACTION_CHANGE_COVER && chosenCoverGame != null) startCoverPicker();
            } catch (Exception e) {
                artActionAfterFolder = ART_ACTION_NONE;
                toast("Could not keep box art folder permission: " + e.getMessage());
            }
            return;
        }
        if (uri == null) return;
        if (request == EXPORT_SAVE || request == IMPORT_SAVE) {
            final Game game = pendingSaveGame;
            final String saveFolder = request == EXPORT_SAVE ? pendingSaveSourceKey : pendingSaveDestinationKey;
            pendingSaveGame = null;
            if (game == null) return;
            if (request == EXPORT_SAVE) work(() -> store.exportSave(game, saveFolder, uri));
            else work(() -> store.importSave(game, saveFolder, uri));
            return;
        }
        if (request == PICK_COVER) {
            Game game = chosenCoverGame;
            if (game == null) return;
            io.execute(() -> {
                String message;
                try {
                    covers.importCover(game, uri);
                    message = "Box art updated";
                    List<Uri> collectionCopy = new ArrayList<>();
                    collectionCopy.add(uri);
                    try {
                        String added = covers.importArtworkFiles(collectionCopy);
                        if (added.startsWith("Added 1")) message = "Box art updated and added to your folder";
                    } catch (Exception ignored) { }
                }
                catch (Exception e) { message = e.getMessage(); }
                final String resultText = message;
                main.post(() -> { toast(resultText); render(); });
            });
            return;
        }
        String key = request == PICK_ROMS ? FileStore.ROMS : request == PICK_SHARED ? FileStore.SHARED : null;
        if (key == null) return;
        try {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, flags);
            store.saveUri(key, uri);
            if (FileStore.ROMS.equals(key)) {
                if (addRomsAfterFolderGrant) { addRomsAfterFolderGrant = false; maybeChooseSaveFolderBeforeAdd(); }
                else refresh();
            } else {
                if (addRomsAfterFolderGrant) { addRomsAfterFolderGrant = false; startRomFilePicker(); }
                else refreshSaveState();
            }
        } catch (Exception e) { toast("Could not keep folder permission: " + e.getMessage()); }
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(color);
        v.setTextSize(size);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private TextView pill(String value, int bg, int fg) {
        TextView v = text(value, 13, fg, true);
        v.setPadding(dp(14), dp(11), dp(14), dp(11));
        v.setGravity(Gravity.CENTER);
        v.setBackground(shape(bg, dp(13)));
        return v;
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
