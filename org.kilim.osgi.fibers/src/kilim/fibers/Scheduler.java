/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.fibers;

import java.util.LinkedList;


/** 
 * This is a basic FIFO Executor. It maintains a list of
 * runnable tasks and hands them out to WorkerThreads. Note that
 * we don't maintain a list of all tasks, but we will at some point
 * when we introduce monitoring/watchdog services. 
 * Paused tasks are not GC'd because their PauseReasons ought to be 
 * registered with some other live object.
 * 
 */
public class Scheduler {
    static Scheduler defaultScheduler = null;
    protected boolean shutdown = false;
    protected LinkedList<Task> runnableTasks = new LinkedList<Task>();
    protected Scheduler() {}
    
    public Scheduler(int numThreads) {
        for (int i = 0; i < numThreads; i++) {
            new WorkerThread(this).start();
        }
    }
    
    /**
     * Schedule a task to run. It is the task's job to ensure that
     * it is not scheduled when it is runnable.
     * ensure that 
     */
    public synchronized void schedule(Task t) {
        runnableTasks.add(t);
        notify();
    }
    
    public void shutdown() {
        synchronized(this) {
            shutdown = true;
            notifyAll();
        }
    }
    
    synchronized Task getNextTask(WorkerThread wt) {
        while (true) {
            if (shutdown) return null;
            if (runnableTasks.isEmpty()) {
                try {
                    wait();
                } catch (InterruptedException ignore) {}
            } else {
                return runnableTasks.poll();
            }
        }
    }

    public synchronized static Scheduler getDefaultScheduler() {
        if (defaultScheduler == null) {
            defaultScheduler = new Scheduler(1);
        }
        return defaultScheduler;
    }
    
    public static void setDefaultScheduler(Scheduler s) {
        defaultScheduler = s;
    }

    public synchronized void dump() {
        System.out.print("Scheduler " + this + "\n\t");
        for (Task t: runnableTasks) {
            System.out.println(t);
        }
    }
}
