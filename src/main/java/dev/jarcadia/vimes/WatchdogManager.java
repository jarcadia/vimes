package dev.jarcadia.vimes;

import com.jarcadia.retask.RetaskManager;
import com.jarcadia.retask.RetaskStartupCallback;
import com.jarcadia.retask.Task;
import com.jarcadia.retask.WorkerProdvider;

public class WatchdogManager {

    private final RetaskManager retaskManager;
    private final NotificationService notificationService;

    public WatchdogManager(RetaskManager retaskManager, NotificationService notificationService) {
        this.retaskManager = retaskManager;
        this.notificationService = notificationService;
    }

    public void start(RetaskStartupCallback callback) {
        retaskManager.start(callback);
    }

    public void start(Task task) {
        retaskManager.start(task);
    }

    public void start() {
        retaskManager.start();
    }

    /**
     * Register a shutdown hook that will run after:
     *  1. Task popping has stopped
     *  2. Scheduled task polling has stopped
     *  3. All tasks have completed (queue is completely drained)
     *
     *  But will run before:
     *
     *  1. RedisCommando is closed
     *  2. JVM exits
     *
     * @param runnable
     */
    public void registerPreShutdownHook(Runnable runnable) {
        retaskManager.registerPreShutdownHook(runnable);
    }

    /**
     * Register a shutdown hook that will run after:
     *  1. RedisCommando is closed
     *
     *  But will run before:
     *
     *  1. JVM exits
     *
     * @param runnable
     */
    public void registerPostShutdownHook(Runnable runnable) {
        retaskManager.registerPostShutdownHook(runnable);
    }

    public void addWorkerProvider(WorkerProdvider provider) {
        retaskManager.addWorkerProvider(provider);
    }

    public NotificationService getNotificationService() {
        return notificationService;
    }
}
