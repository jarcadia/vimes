package com.jarcadia.watchdog;

import com.jarcadia.rcommando.Index;
import com.jarcadia.rcommando.RedisCommando;

public class NotificationService {

    private final Index notifications;

    public NotificationService(RedisCommando rcommando) {
        this.notifications = rcommando.getPrimaryIndex("notifications");
    }

    public void info(String msg) {
        insert("info", msg);
    }

    public void warn(String msg) {
        insert("warn", msg);
    }

    public void error(String msg) {
        insert("error", msg);
    }

    private void insert(String level, String msg) {
        notifications.get().setTs("level", level, "msg", msg);
    }
}
