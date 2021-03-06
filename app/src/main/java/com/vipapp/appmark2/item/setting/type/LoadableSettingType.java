package com.vipapp.appmark2.item.setting.type;

import android.content.SharedPreferences;
import android.view.View;
import com.vipapp.appmark2.item.SettingsType;
import com.vipapp.appmark2.item.setting.item.LoadableItem;

public class LoadableSettingType extends SettingsType<LoadableItem> {
    public LoadableSettingType(int xml_resource) {
        super(xml_resource);
    }
    public void setupView(View v, SharedPreferences prefs, LoadableItem item) {
        item.setup(v);
    }
}