package com.animus.romshelf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.content.ActivityNotFoundException;
import android.text.InputType;
import java.text.DateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
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
    private static final int PICK_ROMS = 210, PICK_SHARED = 211, PICK_JOHN = 212, PICK_COVER = 213, EXPORT_SAVE = 214, IMPORT_SAVE = 215, PICK_PIZZA = 216;
    private static final String[] SAVE_FOLDERS = {FileStore.SHARED, FileStore.JOHN, FileStore.PIZZA};
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean scanning = new AtomicBoolean(false);
    private final List<Game> games = new ArrayList<>();
    private FileStore store;
    private CoverLoader covers;
    private AiResolver ai;
    private ChatGPTAuth auth;
    private SharedPreferences appPrefs;
    private boolean newestFirst = true;
    private Game chosenCoverGame;
    private Game pendingSaveGame;
    private String pendingSaveSourceKey = FileStore.SHARED;
    private String pendingSaveDestinationKey = FileStore.SHARED;
    private GridLayout grid;
    private TextView summary, folderStatus;
    private EditText search;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new FileStore(this);
        covers = new CoverLoader(this);
        auth = new ChatGPTAuth(this);
        ai = new AiResolver(this, auth);
        appPrefs = getSharedPreferences("rom_shelf_launcher", MODE_PRIVATE);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        buildUi();
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        if (store != null && grid != null) {
            if (games.isEmpty()) refresh();
            else refreshSaveState();
        }
    }

    private void buildUi() {
        LinearLayout base = new LinearLayout(this);
        base.setOrientation(LinearLayout.VERTICAL);
        base.setPadding(dp(16), dp(14), dp(16), 0);
        base.setBackgroundColor(BACKGROUND);
        setContentView(base);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        base.addView(top, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView brand = text("ROM Shelf", 24, TEXT, true);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView sorter = pill("Recent ▾", CARD, SECONDARY);
        sorter.setPadding(dp(10), dp(10), dp(10), dp(10));
        sorter.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Sort library")
            .setItems(new String[] {"Recently played (save timestamps)", "Title A–Z"}, (d, which) -> {
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
        String destination = "game_launcher_" + game.key;
        if (!appPrefs.contains(destination)) {
            for (String oldKey : game.legacyKeys) {
                String source = "game_launcher_" + oldKey;
                String value = appPrefs.getString(source, "");
                if (!value.isEmpty()) { appPrefs.edit().putString(destination, value).apply(); break; }
            }
        }
        ai.migrateIdentity(game);
        covers.migrateIdentity(game);
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
        boolean savesLinked = store.getUri(FileStore.SHARED) != null || store.getUri(FileStore.JOHN) != null || store.getUri(FileStore.PIZZA) != null;
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
        folderStatus.setText("ROMs " + mark(FileStore.ROMS) + "   Shared " + mark(FileStore.SHARED) +
            "   John " + mark(FileStore.JOHN) + "   Pizza " + mark(FileStore.PIZZA) + "   ·   Tap to play · Hold for options");
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
        tile.addView(textBox, new LinearLayout.LayoutParams(-1, dp(100)));
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
        TextView recent = text(game.lastPlayedEstimate() > 0 ? "Saved " + shortDate(game.lastPlayedEstimate()) :
            (("SAVED".equals(game.saveStatus())) ? "Save date unknown" : "Not played yet"), 10, game.lastPlayedEstimate() > 0 ? GREEN : SECONDARY, false);
        recent.setMaxLines(1);
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

    private String shortDate(long millis) {
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(millis));
    }
    private String fullDate(long millis) {
        return millis <= 0 ? "Unknown (folder did not provide timestamp)" :
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(millis));
    }

    private static final String[] EMULATORS = {"Pizza Boy GBA", "Pizza Boy GBA Free", "John GBA", "John GBA Lite"};
    private static final String[] PACKAGES = {"it.dbtecno.pizzaboygba", "it.dbtecno.pizzaboygbafree", "com.johnemulators.johngba", "com.johnemulators.johngbalite"};

    private String launcherFor(Game game) {
        return appPrefs.getString("game_launcher_" + game.key, appPrefs.getString("default_launcher", ""));
    }
    private String launcherLabel(String packageId) {
        for (int i=0;i<PACKAGES.length;i++) if (PACKAGES[i].equals(packageId)) return EMULATORS[i];
        return "Choose emulator";
    }
    private void chooseEmulator(Game game, boolean launchAfterSelection) {
        String current = game == null ? appPrefs.getString("default_launcher", "") : launcherFor(game);
        int selected = -1;
        for (int i=0;i<PACKAGES.length;i++) if (PACKAGES[i].equals(current)) selected=i;
        new AlertDialog.Builder(this)
            .setTitle(game == null ? "Default GBA emulator" : "Launch " + game.title + " with")
            .setSingleChoiceItems(EMULATORS, selected, (dialog, index) -> {
                String pkg = PACKAGES[index];
                if (game == null) appPrefs.edit().putString("default_launcher",pkg).apply();
                else appPrefs.edit().putString("game_launcher_" + game.key,pkg).apply();
                dialog.dismiss();
                if (launchAfterSelection && game != null) launchGame(game);
                else toast((game == null ? "Default: " : game.title + " → ") + launcherLabel(pkg));
            }).setNegativeButton("Cancel", null).show();
    }
    private void launchGame(Game game) {
        String pkg = launcherFor(game);
        if (pkg.isEmpty()) { chooseEmulator(game, true); return; }
        Uri rom = game.rom.getUri();
        // URI permission is passed to the chosen emulator; no broad filesystem access is needed.
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(rom, "application/octet-stream");
        intent.setPackage(pkg);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setClipData(ClipData.newRawUri("GBA ROM", rom));
        try {
            startActivity(intent);
        } catch (Exception e) {
            // Android emulators do not all expose compatible external-ROM VIEW activities.
            new AlertDialog.Builder(this).setTitle("Could not launch ROM")
                .setMessage(launcherLabel(pkg) + " did not accept the external ROM intent. Install/check that emulator or choose a different one. If it opens but doesn't load, import the game through the emulator first. Your ROM and save are unchanged.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Change emulator",(d,w)->chooseEmulator(game,true)).show();
        }
    }
    private void openGame(Game game) {
        List<String> choices = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        addAction(choices, actions, "Play using " + launcherLabel(launcherFor(game)), () -> launchGame(game));
        addAction(choices, actions, "Choose emulator for this game", () -> chooseEmulator(game, false));
        for (String folder : SAVE_FOLDERS) {
            if (saveFor(game, folder) != null)
                addAction(choices, actions, "Export " + folderLabel(folder) + " battery save (.sav)", () -> exportSave(game, folder));
            if (store.getUri(folder) != null)
                addAction(choices, actions, "Import .sav into " + folderLabel(folder), () -> confirmImport(game, folder));
            if (store.newestBackup(game, folder) != null)
                addAction(choices, actions, "Restore backup in " + folderLabel(folder), () -> confirmRestore(game, folder));
        }
        addAction(choices, actions, "AI: identify game and search artwork", () -> identifyGame(game, true));
        addAction(choices, actions, "Edit display title", () -> editDisplayTitle(game));
        String imageSource = covers.sourceFor(game);
        if (!imageSource.isEmpty()) addAction(choices, actions, "View artwork source", () -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(imageSource))); }
            catch (Exception ignored) { toast("No browser available"); }
        });
        addAction(choices, actions, "Choose cover image", () -> chooseCover(game));
        addAction(choices, actions, "Remove custom cover", () -> { covers.removeCustom(game); render(); });
        addAction(choices, actions, "Save and ROM details", () -> new AlertDialog.Builder(this)
            .setTitle(game.title)
            .setMessage("ROM: " + game.fileName + "\nVersion: " + game.version +
                "\nSave: " + (game.sharedSave == null ? "No .sav found" : game.sharedSave.getName() + " (" + game.sharedSave.length() + " bytes)") +
                "\nJohn GBA save: " + describeSave(game.johnSave, game.johnStamp) +
                "\nPizza Boy save: " + describeSave(game.pizzaSave, game.pizzaStamp) +
                "\nLast save: " + fullDate(game.lastPlayedEstimate()) +
                "\nSelected emulator: " + launcherLabel(launcherFor(game)) +
                "\n\nSave dates approximate play activity. ROM Shelf cannot force an emulator to load a save state. Only compatible battery saves are shared. Duplicate ROMs are hidden, not deleted.")
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

    private DocumentFile saveFor(Game game, String key) {
        if (FileStore.SHARED.equals(key)) return game.sharedSave;
        if (FileStore.JOHN.equals(key)) return game.johnSave;
        if (FileStore.PIZZA.equals(key)) return game.pizzaSave;
        return null;
    }

    private String folderLabel(String key) {
        if (FileStore.SHARED.equals(key)) return "Shared saves";
        if (FileStore.JOHN.equals(key)) return "John GBA saves";
        if (FileStore.PIZZA.equals(key)) return "Pizza Boy saves";
        return "selected saves";
    }

    private String describeSave(DocumentFile save, long stamp) {
        return save == null ? "Not found" : save.getName() + " (" + save.length() + " bytes, " + fullDate(stamp) + ")";
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
                covers.clearAiImage(g);
                render();
            }).show();
    }

    private void aiSettings() {
        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (auth.connected()) {
            addAction(items, actions, "Connected: " + auth.account(), () -> toast("ChatGPT plan authorized"));
            addAction(items, actions, "Manage ChatGPT plan usage", () -> {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/settings/usage")));
            });
            addAction(items, actions, "Disconnect ChatGPT", () -> new AlertDialog.Builder(this)
                .setTitle("Disconnect ChatGPT?").setMessage("End this ROM Shelf authorization and remove locally saved tokens.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Disconnect", (d,w) -> io.execute(() -> {
                    String msg=auth.disconnect(); main.post(() -> toast(msg));
                })).show());
        } else addAction(items, actions, "Continue with ChatGPT", this::signInChatGPT);
        addAction(items, actions, "How AI works", () -> new AlertDialog.Builder(this).setTitle("Private AI matching")
            .setMessage("Connect with the official ChatGPT browser flow. ROM Shelf asks separately for plan usage. Only ROM filenames and current display metadata are sent for title/art research; ROM and save files remain local. AI requests count toward eligible ChatGPT plan usage and may use credits where authorized. The app never reads your Codex CLI tokens. You can disconnect anytime.")
            .setPositiveButton("OK",null).show());
        new AlertDialog.Builder(this).setTitle("ChatGPT AI")
            .setItems(items.toArray(new String[0]), (d,i) -> actions.get(i).run())
            .setNegativeButton("Close",null).show();
    }
    private void signInChatGPT() {
        toast("Complete authorization in your browser");
        auth.signIn((error, email) -> main.post(() -> {
            if (error != null) new AlertDialog.Builder(this).setTitle("ChatGPT connection unsuccessful")
                .setMessage(error).setPositiveButton("OK",null).show();
            else toast("ChatGPT connected: " + email);
        }));
    }

    private void identifyGame(Game g, boolean force) {
        if (!ai.configured()) { aiSettings(); return; }
        if (!force && ai.processed(g)) return;
        toast("AI searching for " + g.title);
        io.execute(() -> {
            try {
                AiResolver.Answer answer = ai.identify(g);
                store.setMetadata(g, answer.title, answer.version);
                if (!answer.imageUrl.isEmpty()) covers.setAiImage(g, answer.imageUrl, answer.sourceUrl);
                else covers.clearAiImage(g);
                ai.markProcessed(g);
                main.post(() -> { render(); toast("Updated: " + g.title + (answer.imageUrl.isEmpty() ? " (no verified art URL)" : " + art")); });
            } catch (Exception e) { main.post(() -> toast("AI request failed: " + e.getMessage())); }
        });
    }

    private void bulkIdentify() {
        if (!ai.configured()) { aiSettings(); return; }
        List<Game> pending = new ArrayList<>();
        for (Game g : games) if (!ai.processed(g) && pending.size() < 12) pending.add(g);
        if (pending.isEmpty()) { toast("No new games to identify (use per-game AI to retry)"); return; }
        new AlertDialog.Builder(this).setTitle("AI identify " + pending.size() + " games?")
            .setMessage("This will send game filenames to OpenAI, use online image search, and make up to " + pending.size() +
                " ChatGPT plan requests. ROM and save files remain on your device; AI usage counts toward your eligible plan allowance and any authorized credits.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Run AI", (d,w) -> io.execute(() -> {
                int ok = 0; String lastError = null;
                for (Game game : pending) {
                    try {
                        AiResolver.Answer result = ai.identify(game);
                        store.setMetadata(game, result.title, result.version);
                        if (!result.imageUrl.isEmpty()) covers.setAiImage(game, result.imageUrl, result.sourceUrl);
                        else covers.clearAiImage(game);
                        ai.markProcessed(game);
                        ok++;
                    } catch (Exception e) { lastError = e.getMessage(); break; }
                    main.post(this::render);
                }
                final int done = ok;
                final String error = lastError;
                main.post(() -> { render(); toast(done + " titles updated" + (error == null ? "" : " · Stopped: " + error)); });
            })).show();
    }

    private void openSettings() {
        String[] options = {
            "ROM folder " + mark(FileStore.ROMS),
            "Shared GBA .sav folder " + mark(FileStore.SHARED),
            "John GBA .sav folder " + mark(FileStore.JOHN),
            "Pizza Boy .sav folder " + mark(FileStore.PIZZA),
            "Default emulator: " + launcherLabel(appPrefs.getString("default_launcher", "")),
            "Rescan collection",
            "ChatGPT AI: " + (auth.connected() ? "Connected" : "Not connected"),
            "AI identify up to 12 new games",
            "Launcher and save folder help"
        };
        new AlertDialog.Builder(this).setTitle("ROM Shelf settings")
            .setItems(options, (d,which) -> {
                if (which == 0) pickFolder(PICK_ROMS);
                else if (which == 1) pickFolder(PICK_SHARED);
                else if (which == 2) pickFolder(PICK_JOHN);
                else if (which == 3) pickFolder(PICK_PIZZA);
                else if (which == 4) chooseEmulator(null,false);
                else if (which == 5) refresh();
                else if (which == 6) aiSettings();
                else if (which == 7) bulkIdentify();
                else showHelp();
            }).setNegativeButton("Close",null).show();
    }
    private void showHelp() {
        new AlertDialog.Builder(this).setTitle("How ROM Shelf works")
            .setMessage("1. Select the ROM folder. Optionally link shared, John GBA, and Pizza Boy .sav folders that Android lets you access.\n2. Choose the default emulator in Settings.\n3. Tap any cover to open a ROM; long-press it for that game's settings and emulator override.\n4. ROM Shelf tracks saves in the folders you link. The emulator handles loading its battery save from its configured directory; ROM Shelf cannot redirect private emulator storage. Use the emulator's own import if external ROM launch is unsupported.\n\nRecent sorting uses the newest visible .sav timestamp as an activity estimate; a save state is not a portable battery save. Android may block private emulator folders.\n\nIdentical ROM content hashes appear once; original files are not deleted.")
            .setPositiveButton("OK",null).show();
    }
    private void pickFolder(int request) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent,request); } catch (Exception e) { toast("Folder picker unavailable"); }
    }

    private void chooseCover(Game game) {
        chosenCoverGame = game;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, PICK_COVER);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
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
                try { covers.importCover(game, uri); message = "Cover image updated"; }
                catch (Exception e) { message = e.getMessage(); }
                final String resultText = message;
                main.post(() -> { toast(resultText); render(); });
            });
            return;
        }
        String key = request == PICK_ROMS ? FileStore.ROMS : request == PICK_SHARED ? FileStore.SHARED :
            request == PICK_JOHN ? FileStore.JOHN : request == PICK_PIZZA ? FileStore.PIZZA : null;
        if (key == null) return;
        try {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, flags);
            store.saveUri(key, uri);
            if (FileStore.ROMS.equals(key)) refresh(); else refreshSaveState();
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
