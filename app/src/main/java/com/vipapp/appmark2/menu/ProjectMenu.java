package com.vipapp.appmark2.menu;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.vipapp.appmark2.R;
import com.vipapp.appmark2.activity.CodeActivity;
import com.vipapp.appmark2.alert.EditProject;
import com.vipapp.appmark2.alert.confirm.DeleteProject;
import com.vipapp.appmark2.item.Item;
import com.vipapp.appmark2.manager.ProjectManager;
import com.vipapp.appmark2.project.Project;
import com.vipapp.appmark2.util.ImageUtils;
import com.vipapp.appmark2.util.Toast;
import com.vipapp.appmark2.util.wrapper.mActivity;
import com.vipapp.appmark2.util.wrapper.Str;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import static com.vipapp.appmark2.util.Const.PROJECT_MANAGER;

public class ProjectMenu extends DefaultMenu<Project, ProjectMenu.ProjectHolder>{

    public int[] stringRes = new int[]{

    };

    private ProjectManager manager;

    public ArrayList<Project> list(Context context){
        if(manager != null)
            manager.exec(this::pushArray);
        return null;
    }
    @SuppressLint("SetTextI18n")
    public void bind(ProjectHolder vh, Project item, int i) {
        if(item.isValid()) {
            if (item.getName() == null ||
                    item.getPackage() == null ||
                    item.getVersionName() == null)
                Toast.show(String.format(
                        Str.get(R.string.xml_error), item.getManifestFile().getAbsolutePath()));

            vh.name.setText(item.localizeString(item.getName()));
            vh.packag.setText(item.getPackage());
            vh.version_name.setText(item.getVersionName());
            vh.edit.setVisibility(item.getManifest().isCorrect() ? View.VISIBLE : View.GONE);
            ImageUtils.loadInto(item.getIcon(), vh.icon);
            setCallbacks(vh, item, i);
        } else {
            vh.name.setText(R.string.project_broken);
            vh.packag.setText(item.getSource().getAbsolutePath());
            vh.icon.setImageBitmap(null);
            vh.version_name.setText(Integer.toString(item.getError()));
        }
    }

    private void setCallbacks(ProjectHolder vh, Project item, int i){
        vh.itemView.setOnLongClickListener(view -> {
            showView(vh);
            return true;
        });
        vh.itemView.setOnClickListener(view -> {
            Intent intent = new Intent(mActivity.get(), CodeActivity.class);
            intent.putExtra("projectFile", item.getSource());
            mActivity.get().startActivity(intent);
        });
        vh.delete.setOnClickListener(view -> {
            int index = manager.getObjects().indexOf(item);
            DeleteProject.show(item, index);
        });
        vh.edit.setOnClickListener(view -> {
            int index = manager.getObjects().indexOf(item);
            EditProject.show(item, index);
        });
        vh.close.setOnClickListener(view -> hideView(vh));
    }

    @Override
    public void onValueReceived(Item item) {
        if(item.getType() == PROJECT_MANAGER){
            manager = (ProjectManager) item.getInstance();
            manager.exec(this::pushArray);
        }
        super.onValueReceived(item);
    }

    @Override
    public int getLayoutResource() {
        return R.layout.project_default;
    }

    @Override
    public ProjectHolder getViewHolder(ViewGroup parent, int itemType) {
        return new ProjectHolder(inflate(parent));
    }

    private void showView(ProjectHolder vh){
        vh.edit_panel.setVisibility(View.VISIBLE);
    }
    private void hideView(ProjectHolder vh){
        vh.edit_panel.setVisibility(View.GONE);
    }

    static class ProjectHolder extends RecyclerView.ViewHolder {

        public LinearLayout edit_panel;
        public ImageView delete;
        public ImageView close;
        public ImageView edit;
        public TextView name;
        public TextView packag;
        public TextView version_name;
        public ImageView icon;

        public ProjectHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.name);
            packag = itemView.findViewById(R.id.packag);
            version_name = itemView.findViewById(R.id.version_name);
            icon = itemView.findViewById(R.id.icon);
            edit_panel = itemView.findViewById(R.id.edit_panel);
            delete = itemView.findViewById(R.id.delete);
            edit = itemView.findViewById(R.id.edit);
            close = itemView.findViewById(R.id.close);
        }

    }
}
