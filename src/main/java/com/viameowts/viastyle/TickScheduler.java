package com.viameowts.viastyle;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Centralized one-shot task scheduler.
 *
 * <p>Registers a <b>single</b> {@link ServerTickEvents#END_SERVER_TICK} listener
 * and processes all pending delayed tasks from it.  This replaces the old
 * pattern of registering a <b>new permanent listener per task</b>, which
 * caused an unbounded memory leak on long-running servers.</p>
 *
 * <p>Call {@link #init()} once during mod initialization, then use
 * {@link #schedule(int, Runnable)} anywhere to enqueue one-shot tasks.</p>
 */
public final class TickScheduler {

    private static final List<ScheduledTask> tasks = new ArrayList<>();
    private static boolean initialized = false;

    private TickScheduler() {}

    /** Register the single shared tick listener.  Call once from mod init. */
    public static void init() {
        if (initialized) return;
        initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
    }

    /**
     * Schedules a task to run after {@code delayTicks} server ticks.
     * If {@code delayTicks <= 0}, the task runs immediately on the
     * calling thread.
     *
     * @param delayTicks ticks to wait before executing
     * @param task       the runnable to execute
     */
    public static void schedule(int delayTicks, Runnable task) {
        if (delayTicks <= 0) {
            task.run();
            return;
        }
        synchronized (tasks) {
            tasks.add(new ScheduledTask(delayTicks, task));
        }
    }

    private static void tick() {
        // Collect ready tasks first, then run them OUTSIDE the iterator.
        // This prevents ConcurrentModificationException if a task calls
        // schedule() (which adds to `tasks`) while we are iterating.
        List<Runnable> ready = new ArrayList<>();
        synchronized (tasks) {
            Iterator<ScheduledTask> it = tasks.iterator();
            while (it.hasNext()) {
                ScheduledTask t = it.next();
                if (--t.remaining <= 0) {
                    ready.add(t.task);
                    it.remove();
                }
            }
        }
        for (Runnable r : ready) {
            r.run();
        }
    }

    private static class ScheduledTask {
        int remaining;
        final Runnable task;

        ScheduledTask(int remaining, Runnable task) {
            this.remaining = remaining;
            this.task = task;
        }
    }
}
