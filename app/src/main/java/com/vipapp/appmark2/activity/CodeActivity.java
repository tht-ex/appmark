package com.vipapp.appmark2.activity;

import android.graphics.Color;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.vipapp.appmark2.R;
import com.vipapp.appmark2.alert.CodeErrorsDialog;
import com.vipapp.appmark2.alert.ConfirmDialog;
import com.vipapp.appmark2.alert.InfoDialog;
import com.vipapp.appmark2.alert.LoadingDialog;
import com.vipapp.appmark2.alert.strings_list_editor.ProjectSettingsDialog;
import com.vipapp.appmark2.callback.PushCallback;
import com.vipapp.appmark2.compiler.ErrorsParser;
import com.vipapp.appmark2.item.ClassItem;
import com.vipapp.appmark2.item.Item;
import com.vipapp.appmark2.picker.ReplacePicker;
import com.vipapp.appmark2.picker.StringChooser;
import com.vipapp.appmark2.project.Project;
import com.vipapp.appmark2.util.ArrayUtils;
import com.vipapp.appmark2.util.Const;
import com.vipapp.appmark2.util.FileUtils;
import com.vipapp.appmark2.util.Language;
import com.vipapp.appmark2.util.TextUtils;
import com.vipapp.appmark2.util.Thread;
import com.vipapp.appmark2.util.Toast;
import com.vipapp.appmark2.util.wrapper.Animation;
import com.vipapp.appmark2.util.wrapper.Classes;
import com.vipapp.appmark2.util.wrapper.Res;
import com.vipapp.appmark2.util.wrapper.mSharedPreferences;
import com.vipapp.appmark2.util.wrapper.Str;
import com.vipapp.appmark2.widget.CodeLayout;
import com.vipapp.appmark2.widget.CodeText;
import com.vipapp.appmark2.widget.FileActionButton;
import com.vipapp.appmark2.widget.RecyclerView;
import com.vipapp.appmark2.widget.TextView;
import com.vipapp.fjc.ApkBuilderCallBack;
import com.vipapp.appmark2.compiler.Compiler;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.vipapp.appmark2.compiler.ErrorsParser.TYPE_AAPT;
import static com.vipapp.appmark2.compiler.ErrorsParser.TYPE_JAVA;
import static com.vipapp.appmark2.util.Const.DEFAULT_ON_BACK_PRESSED;
import static com.vipapp.appmark2.util.Const.JAVA_LANGUAGE;
import static com.vipapp.appmark2.util.Const.MAX_LINES_IN_TEXT_EDITOR;
import static com.vipapp.appmark2.util.Const.SYMBOL_INSERTED;
import static com.vipapp.appmark2.util.Const.UNDO_ON_BACK_PRESSED;

public class CodeActivity extends BaseActivity {

    ArrayList<ErrorsParser.Error> errors = new ArrayList<>();
    ArrayList<File> history = new ArrayList<>();

    Project project;
    File opened;

    File lastApk;

    boolean lastCompiledSuccessful = false;
    boolean compiling = false;
    boolean textChangedAfterCompile = true;
    boolean saving = false;
    boolean needShowApk = false;
    // Is action button hidden while scrolling
    boolean floatingPanelHidden = false;

    public CodeText code;
    CodeLayout codeLayout;
    DrawerLayout drawerLayout;
    FrameLayout main;
    FrameLayout floatingPanel;
    TextView title;
    TextView path;
    ImageView files;
    ImageView menu;
    ImageView run;
    ImageView undo;
    ImageView redo;
    RecyclerView file_recycler;
    RecyclerView insert_symbol;
    FileActionButton actionButton;

    LinearLayout stateLayout;
    ImageView stateImage;
    ProgressBar stateProgress;

    PushCallback[] callbacks = new PushCallback[]{
            // Run project
            none -> {
                LoadingDialog.show(true);
                if(!compiling && lastCompiledSuccessful) {
                    FileUtils.openFileInExternal(lastApk);
                    LoadingDialog.hide();
                }
                compileProject();
                needShowApk = true;
            },
            // Replace
            none -> {
                ReplacePicker picker = new ReplacePicker(replaceItem -> {
                    String text = Objects.requireNonNull(code.getText()).toString();
                    code.setText(text.replaceAll(replaceItem.getItem1(), replaceItem.getItem2()));
                });
                picker.show();
            },
            // Project settings
            none -> ProjectSettingsDialog.show(project),
            // Undo
            none -> code.undo(),
            // Redo
            none -> code.redo(),
            // Exit
            none -> {
                clearLastProject();
                super.onBackPressed();
            }
    };

    @Override
    public Integer onCreateView() {
        return R.layout.activity_code;
    }

    @Override
    public void findViews(){
        title = f(R.id.title);
        files = f(R.id.files);
        menu = f(R.id.menu);
        drawerLayout = f(R.id.drawer_layout);
        file_recycler = f(R.id.file_recycler);
        insert_symbol = f(R.id.insert_symbol);
        path = f(R.id.path);
        code = f(R.id.code);
        codeLayout = f(R.id.codeLayout);
        main = f(R.id.main);
        actionButton = f(R.id.actionButton);
        stateLayout = f(R.id.stateLayout);
        stateImage = f(R.id.stateImage);
        stateProgress = f(R.id.stateProgress);
        floatingPanel = f(R.id.floatingPanel);
        run = f(R.id.run);
        undo = f(R.id.undo);
        redo = f(R.id.redo);
    }

    @Override
    public void setCallbacks(){
        files.setOnClickListener(view -> {
            if(drawerLayout.isDrawerOpen(GravityCompat.START))
                drawerLayout.closeDrawer(GravityCompat.START);
            else
                drawerLayout.openDrawer(GravityCompat.START);
        });
        file_recycler.addOnPushCallback(item -> {
            switch(item.getType()){
                case Const.PATH:
                    path.setText((String)item.getInstance());
                    break;
                case Const.FILE_SELECTED:
                    openFile((File)item.getInstance());
                    break;
            }
        });
        insert_symbol.addOnPushCallback(item -> {
            if(item.getType() == SYMBOL_INSERTED){
                CharSequence contentText = code.getText();
                CharSequence insert = (CharSequence) item.getInstance();
                int insertPos = code.getSelectionStart();
                if(insertPos < contentText.length()){
                    char nextChar = contentText.charAt(insertPos);
                    if(ArrayUtils.in_array(new Character[]{'(', ')', '[', ']', '"', ';', ',', '.'}, nextChar)
                            && ArrayUtils.in_array(new CharSequence[]{"    ", ")", "]", "\"", "'", ";", "."}, insert)){
                        code.setSelection(code.getSelectionStart() + 1);
                        return;
                    }
                }
                Objects.requireNonNull(code.getText())
                        .insert(code.getSelectionStart(), insert);
            }
        });
        path.setOnClickListener(view -> {
            StringChooser chooser = new StringChooser(item -> {
                File to_open = history.get(item.getType());
                history.remove(item.getType());
                openFile(to_open);
            });
            chooser.setTitle(getString(R.string.recent_files));
            chooser.setArray(convertToItemArray(history));
            chooser.show();
        });
        menu.setOnClickListener(view -> {
            //noinspection unchecked
            StringChooser chooser = new StringChooser(item -> callbacks[item.getType()].onComplete(null));
            chooser.setTitle(R.string.select_action);
            chooser.setArray(Const.code_menu_chooser);
            chooser.show();
        });
        stateLayout.setOnClickListener(this::onStateLayoutClicked);
        //noinspection unchecked
        run.setOnClickListener(view -> callbacks[0].onComplete(null));
        //noinspection unchecked
        undo.setOnClickListener(view -> callbacks[3].onComplete(null));
        //noinspection unchecked
        redo.setOnClickListener(view -> callbacks[4].onComplete(null));
        setCodeWatcher();
    }

    @Override
    public void setup() {
        overrideTransition();
        lockScreen();
        setupDrawer();
        setupProject();
        setupActionButtonMoves();
    }

    @Override
    public void onBackPressed() {
        // Check settings
        if(mSharedPreferences.getBoolean(UNDO_ON_BACK_PRESSED, DEFAULT_ON_BACK_PRESSED)) {
            code.undo();
        } else {
            clearLastProject();
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    @Override
    protected void onResume() {
        super.onResume();
        readFile(opened);
        Classes.get();
    }

    public void save(){
        save(none -> {});
    }

    public void save(PushCallback<Void> callback) {
        opened = opened != null && opened.exists()? opened: null;
        if(opened != null) {
            saving = true;
            Thread.start(() -> {
                FileUtils.writeFileUI(opened, Objects.requireNonNull(code.getText()).toString());
                saving = false;
                Thread.ui(() -> callback.onComplete(null));
            });
        } else {
            Thread.ui(() -> callback.onComplete(null));
        }
    }

    public void readFile(File file){
        readFile(file, isOpened -> {});
    }
    public void readFile(File file, boolean forceOpen){
        readFile(file, forceOpen, isOpened -> {});
    }
    public void readFile(File file, PushCallback<Boolean> isOpened){
        readFile(file, false, isOpened);
    }
    // if file is too large and dialog 'fileTooLarge' is shown isOpened will receive false
    public void readFile(File file, boolean forceOpen, PushCallback<Boolean> isOpened){
        if(file != null && !saving) FileUtils.readFile(file, string -> {
            if(!string.equals(Objects.requireNonNull(code.getText()).toString()))
                if(string.length() <= MAX_LINES_IN_TEXT_EDITOR * 10000 + 1 || forceOpen){
                    opened = file;
                    code.setText(string);
                    code.clearHistory();
                    codeLayout.hidePopup();
                    if (project != null) setupActionButton();
                    isOpened.onComplete(true);
                } else {
                    showFileTooLarge(string, file, isOpened);
                }
        });

    }

    private void showFileTooLarge(String text, File file, PushCallback<Boolean> isOpened){
        String title = String.format(Str.get(R.string.too_large_file), text.length(), MAX_LINES_IN_TEXT_EDITOR * 10000);
        ConfirmDialog.show(title, Str.get(R.string.continue_string), Str.get(R.string.open), result -> {
            if(result) {
                readFile(file, true, isOpened);
            } else {
                FileUtils.openFileInExternal(file);
                isOpened.onComplete(false);
            }
        });
    }

    public void overrideTransition(){
        if(getIntent().getBooleanExtra("opened_last", false)){
            overridePendingTransition(0, 0);
        }
    }

    public void clearLastProject(){
        mSharedPreferences.remove("last_project");
    }

    private void setupActionButtonMoves(){
        codeLayout.setOnScrollChangeListener(scrollChange -> {
            boolean scrolledUp = scrollChange.getOldVert() > scrollChange.getVert();
            if(Math.abs(scrollChange.getVert() - scrollChange.getOldVert()) > 10)
                if(scrolledUp == floatingPanelHidden)
                if(scrolledUp)
                    showFloatingPanel();
                else
                    hideFloatingPanel();
        });
    }

    private void hideFloatingPanel(){
        floatingPanelHidden = true;
        hideView(floatingPanel);
    }

    private void hideView(View v){
        Animation.moveY(v, 300 * Res.get().getDisplayMetrics().density);
        Animation.fadeOut(v);
    }

    private void showFloatingPanel(){
        floatingPanelHidden = false;
        showView(floatingPanel);
    }

    public void showView(View v){
        Animation.moveY(v, 0f);
        Animation.fadeIn(v);
    }

    private void onStateLayoutClicked(View v){
        if(!compiling && errors.size() > 0){
            CodeErrorsDialog.show(errors, this);
        }
    }

    private void hideStateLayout(){
        stateLayout.setVisibility(View.GONE);
    }

    private void showStateLayout(){
        stateLayout.setVisibility(View.VISIBLE);
    }

    private void setStateLayoutLoading(){
        showStateLayout();
        stateProgress.setVisibility(View.VISIBLE);
        stateImage.setVisibility(View.GONE);
    }

    private void setStateLayoutWarning(){
        showStateImage();
        setStateImage(R.drawable.warning_icon);
    }

    private void setStateLayoutError(){
        showStateImage();
        setStateImage(R.drawable.error_icon);
    }

    private void showStateImage(){
        showStateLayout();
        stateProgress.setVisibility(View.GONE);
        stateImage.setVisibility(View.VISIBLE);
    }

    private void setStateImage(@DrawableRes int res) {
        stateImage.setImageResource(res);
    }

    public void setupProject(){
        Project.fromFile((File) getIntent().getSerializableExtra("projectFile"), project -> {
            this.project = project;
            unlockScreen();
            onProjectLoad();
        });
    }
    public void setupDrawer(){
        drawerLayout.setScrimColor(Color.TRANSPARENT);
        drawerLayout.setDrawerElevation(0);
        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            public void onDrawerSlide(@NonNull View view, float v) {
                float slideX = view.getWidth() * v;
                main.setTranslationX(slideX);
            }
            public void onDrawerOpened(@NonNull View view) {}
            public void onDrawerClosed(@NonNull View view) {}
            public void onDrawerStateChanged(int i) {}
        });
    }
    public void setupViews(){
        code.setTextSize(Const.MAIN_EDITOR_FONT_SIZE);
        file_recycler.pushValue(Const.PATH, project.getSource());
    }

    public void lockScreen(){
        LoadingDialog.show();
        LoadingDialog.setTitle(R.string.loading_project);
        code.setEnabled(false);
    }
    public void unlockScreen(){
        LoadingDialog.hide();
        code.setEnabled(true);
    }

    public void onProjectLoad(){
        setupViews();
        openLastFile();
        pushProject();
        setLastProject();
        compileProject();
        if(opened != null) setupActionButton();
    }

    public void openLastFile(){
        openFile(project.getAif().getLastFile());
    }
    public void pushProject(){
        actionButton.setProject(project);
        file_recycler.pushValue(Const.PROJECT, project);
    }
    public void setLastProject(){
        mSharedPreferences.putString("last_project", project.getSource().getAbsolutePath());
    }

    public void openFile(File file){
        openFile(file, null);
    }
    public void openFile(File file, PushCallback<Void> onOpen){
        if(file.exists()) {
            if (FileUtils.resolveMimeType(file).contains("text"))
                openTextFile(file, onOpen);
            else if(!FileUtils.openFileInExternal(file)){
                Toast.show(R.string.open_file_error);
            }
        }
    }

    public void openTextFile(File file){
        openTextFile(file, null);
    }
    public void openTextFile(File file, PushCallback<Void> onOpen){
        if(!file.equals(opened)) {
            // Saving previous opened file
            save(none -> {
                // CodeText setup
                readFile(file, isOpened -> {
                    if(isOpened) {
                        codeLayout.setLanguage(Language.fromFile(file));
                        // View setup
                        title.setText(FileUtils.getFileName(file));
                        underLineErrors();
                        file_recycler.pushValue(Const.OPENED_FILE, file);
                        // Other
                        addToHistory(file);
                        project.getAif().setLastFile(file);
                        if(onOpen != null)
                            onOpen.onComplete(null);
                    }
                });
            });
        } else {
            if(onOpen != null)
                onOpen.onComplete(null);
        }
        drawerLayout.closeDrawer(GravityCompat.START);
    }
    private void underLineErrors(){
        code.clearErrors();
        try {
            for (ErrorsParser.Error error : errors) {
                if (error.getFile().equals(opened)) {
                    int line = error.getLineNumber() - 1;
                    int start = code.getLineStartIndex(line);
                    int end = code.getLineEndIndex(line);
                    if(error.getType() == ErrorsParser.Error.TYPE_ERROR)
                        code.setError(start, end);
                    else code.setWarning(start, end);
                }
            }
        } catch (Exception ignored){}
    }
    private void addToHistory(File file){
        history.remove(file);
        history.add(0, file);
        history = history.size() > 10 ? new ArrayList<>(history.subList(0, 10)) : history;
    }
    private void setupActionButton(){
        actionButton.setFile(opened);
    }

    private void setCodeWatcher(){
        code.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                Thread.delay(0, 500, () -> {
                    textChangedAfterCompile = true;
                    if(!compiling)
                        compileProject();
                });
                // Make auto imports only when language is JAVA
                if(code.getLanguage() == JAVA_LANGUAGE) {
                    // Get current char if it was inserted
                    if (charSequence.length() > start + before) {
                        // If current char is dot when we can parse class to import
                        char current_char = charSequence.charAt(start + before);
                        if (current_char == '.') {
                            String current_class = TextUtils.replaceLast(TextUtils.getCurrentWord(code), "\\.", "");
                            // Check if word is not contains dots
                            if(!current_class.contains(".")) {
                                List<String> imports = TextUtils.parseImports(Objects.requireNonNull(code.getText()).toString());
                                // Searching for class import
                                Classes.get(classItems -> {
                                    // getting only item with current_class name
                                    ArrayList<ClassItem> currentImports = ArrayUtils.filter(classItems, item -> item.getName().equals(current_class));
                                    if (hasNotAnyImport(imports, current_class)) {
                                        for (ClassItem Import : currentImports) {
                                            showImportDialog(Import.getFullname());
                                        }
                                    }
                                });
                            }
                        }
                    }
                }
            }
            public void afterTextChanged(Editable editable) {
                if(editable.length() > MAX_LINES_IN_TEXT_EDITOR * 10000)
                    Toast.show(R.string.text_cropped);
            }
        });
    }

    private boolean hasNotAnyImport(@Nullable List<String> imports, String currentClass){
        return imports != null && !imports.contains(currentClass);
    }
    private void showImportDialog(String Import){
        Thread.ui(() -> ConfirmDialog.show(String.format(Str.get(R.string.make_import), Import), result -> {
            if(result) addImport(Import);
        }));
    }
    private void addImport(String Import){
        Editable editable = code.getText();
        editable.insert(editable.toString().indexOf(';') + 1, "\nimport " + Import + ";");
    }

    private ArrayList<Item<String>> convertToItemArray(@NonNull ArrayList<File> files){
        ArrayList<Item<String>> returnable = new ArrayList<>();
        for(int i = 0; i < files.size(); i++){
            returnable.add(new Item<>(i, files.get(i).getName()));
        }
        return returnable;
    }

    private void compileProject(){
        Thread.ui(this::setStateLayoutLoading);
        save();
        Compiler.compileDebug(project, new ApkBuilderCallBack() {
            public void aapt() {
                LoadingDialog.setTitle(R.string.aapt);
            }
            public void aaptOK(@NotNull String output) {

            }
            public void aaptERR(int code, @NotNull String output) {
                showErrors(R.string.aapt_error, TYPE_AAPT, output);
            }
            public void java() {
                LoadingDialog.setTitle(R.string.java);
            }
            public void javaOK(@NotNull String output_out, @NotNull String output_err) {
                parseErrors(TYPE_JAVA, output_err, true);
            }
            public void javaERR(@NotNull String output_out, @NotNull String output_err) {
                showErrors(R.string.java_error, TYPE_JAVA, output_err);
            }
            public void dx() {
                LoadingDialog.setTitle(R.string.dx);
            }
            public void dxOK(@NotNull String output) {}
            public void dxERR(int code) {
                showErrors(R.string.dx_error, -1,"Error code: " + code);
            }
            public void pkg() {
                LoadingDialog.setTitle(R.string.pkg);
            }
            public void sign() {
                LoadingDialog.setTitle(R.string.apk_signing);
            }
            public void OK(@NotNull String output) {

            }
            public void ERR(@NotNull Exception exception) {
                showErrors(R.string.runtime_error, -1, exception.toString());
            }
            public void apk(@NotNull String path) {
                LoadingDialog.hide();
                lastCompiledSuccessful = true;
                lastApk = new File(path);
                if(needShowApk) {
                    needShowApk = false;
                    FileUtils.openFileInExternal(lastApk);
                }
                onCompile();
            }
        });
    }

    private void parseErrors(int error_type, String message, boolean warnsOnly){
        Thread.ui(() -> {
            errors = new ErrorsParser(error_type, message).getErrors();
            underLineErrors();
            if(warnsOnly)
                setStateLayoutWarning();
            else
                setStateLayoutError();
            if(needShowApk) {
                needShowApk = false;
                LoadingDialog.hide();
                if(!warnsOnly)
                    CodeErrorsDialog.show(errors, this);
            }
        });
    }

    private void showErrors(@StringRes int title, int error_type, String message){
        lastCompiledSuccessful = false;
        Thread.ui(() -> {
            hideStateLayout();
            if(error_type >= 0){
                parseErrors(error_type, message, false);
            } else {
                if (needShowApk) {
                    needShowApk = false;
                    LoadingDialog.hide();
                    InfoDialog dialog = new InfoDialog(title, message);
                    dialog.show();
                }
            }
            onCompile();
        });
    }

    private void onCompile(){
        Thread.ui(() -> {
            if(errors.size() == 0) {
                code.clearErrors();
                hideStateLayout();
            }
        });
        if(textChangedAfterCompile)
            compileProject();
        textChangedAfterCompile = false;
        compiling = false;
    }

}
