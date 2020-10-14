package dev.jarcadia.vimes;

import dev.jarcadia.redao.Index;
import dev.jarcadia.redao.RedaoCommando;

public class NotificationService {

    private final Index notifications;

    public NotificationService(RedaoCommando rcommando) {
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
