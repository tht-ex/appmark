package com.vipapp.appmark2.util;

import java.util.Date;

public class Time {
    public static long getCurrentTime() {
        return new Date().getTime();
    }
}