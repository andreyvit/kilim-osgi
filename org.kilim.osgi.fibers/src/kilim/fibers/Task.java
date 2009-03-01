/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.fibers;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import kilim.ExitMsg;
import kilim.pausable;

/**
 * A base class for tasks. A task is a lightweight thread (it contains its 
 * own stack in the form of a fiber). A concrete subclass of Task must
 * provide a pausable execute method. 
 *  
 */
public abstract class Task implements Runnable {
    static PauseReason         yieldReason = new YieldReason();
    /**
     * Task id, automatically generated
     */
    public final int           id;
    static final AtomicInteger idSource = new AtomicInteger();

    /**
     * The stack manager in charge of rewinding and unwinding
     * the stack when Task.pause() is called.
     */
    protected Fiber            fiber;

    /**
     * The reason for pausing (duh) and performs the role of a await
     * condition in CCS. This object is responsible for resuming
     * the task. 
     * @see kilim.fibers.PauseReason
     */
    protected PauseReason      pauseReason;
    
    /**
     * running = true when it is put on the schdulers run Q (by Task.resume()). 
     * The Task.runExecute() method is called at some point; 'running' remains 
     * true until the end of runExecute (where it is reset), at which point a 
     * fresh decision is made whether the task needs to continue running.
     */
    boolean  running = false;
    boolean  done = false;

    /**
     * @see #informOnExit(Mailbox)
     */
    private Mailbox<ExitMsg>  exitMB;
    
    /** 
     * The object responsible for handing this task to a thread
     * when the task is runnable. 
     */
    protected Executor executor;

    // TODO: move into a separate timer service or into the schduler.
    final static Timer timer = new Timer(true);

    public Task() {
		id = idSource.incrementAndGet();
        fiber = new Fiber(this);
    }
    
    public int id() {
        return id;
    }
    
    /**
     * Used to start the task; the task doesn't resume on its own.
     */
    public Task start(Executor executor) {
        if (executor == null)
        	throw new NullPointerException("executor is null");
        if (this.executor != null)
        	throw new IllegalStateException("Task.start can only be called once");
        this.executor = executor;
        resume();
        return this;
    }
    
    /**
     * Starts a task, using the caller's executor.
     * 
     * <p>This method is equivalent to calling <code>start(getCurrentTask().getExecutor())</code>.</p>
     * 
     * <p>This method is not really pausable, however it is declared as such to prevent
     * non-pausable code from calling it.</p>
     */
    @pausable
    public Task start() {
		Task currentTask = getCurrentTask();
		if (currentTask == null)
			throw new IllegalStateException("Zero-argument Task.start() can only be used from another Task.");
		return start(currentTask.executor);
    }
    
    /**
     * The generated code calls Fiber.upEx, which in turn calls
     * this to find out out where the current method is w.r.t
     * the closest _runExecute method. 
     * @return the number of stack frames above _runExecute(), not including
     * this method
     */
    public int getStackDepth() {
        StackTraceElement[] stes;
        stes = new Exception().getStackTrace();
        int len = stes.length;
        for (int i = 0; i < len; i++) {
            StackTraceElement ste = stes[i];
            if (ste.getMethodName().equals("_runExecute")){
                // discounting WorkerThread.run, Task._runExecute, and Scheduler.getStackDepth
                return i - 1;
            }
        }
        throw new AssertionError("Expected task to be run by WorkerThread");
    }
    
    /**
     * This is typically called by a pauseReason to resume the task.
     */
    public void resume() {
        if (executor == null) return;
        
        boolean doSchedule = false;
        // We don't check pauseReason while resuming (to verify whether
        // it is worth returning to a pause state. The code at the top of stack 
        // will be doing that anyway.
        synchronized(this) {
            if (done) return;
            if (running) {
                return;
            }
            running = doSchedule = true;
        }
        if (doSchedule) {
            executor.execute(this);
        }
    }
    
    public void informOnExit(Mailbox<ExitMsg> exit) {
        exitMB = exit;
    }
    
    /**
     * This is a placeholder that doesn't do anything useful.
     * Weave replaces the call in the bytecode from
     *     invokestateic Task.getCurrentTask
     * to
     *     load fiber
     *     getfield task
     */
    @pausable
    public static Task getCurrentTask() {return null;}

    /**
     * Analogous to System.exit, except an Object can 
     * be used as the exit value
     */
    @pausable
    public static void exit(Object aExitValue) {    }
    public static void exit(Object aExitValue, Fiber f) {
        assert f.pc == 0;
        f.task.setPauseReason(new TaskDoneReason(aExitValue));
        f.togglePause();
    }

    /**
     * Exit the task with a throwable indicating an error condition. The value
     * is conveyed through the exit mailslot (see informOnExit).
     * All exceptions trapped by the task scheduler also set the error result.
     */
    @pausable
    public static void errorExit(Throwable ex) {  }
    public static void errorExit(Throwable ex, Fiber f) {
        assert f.pc == 0;
        f.task.setPauseReason(new TaskDoneReason(ex));
        f.togglePause();
    }

    private static void errNotWoven() {
        System.err.println("############################################################");
        System.err.println("Task has either not been woven or the classpath is incorrect");
        System.err.println("############################################################");
        Thread.dumpStack();
        System.exit(0);
    }
    
    private static void errNotWoven(Task t) {
        System.err.println("############################################################");
        System.err.println("Task " + t.getClass() + " has either not been woven or the classpath is incorrect");
        System.err.println("############################################################");
        Thread.dumpStack();
        System.exit(0);
    }

    /**
     * @param milliseconds
     * to sleep. Like thread.sleep, except it doesn't throw an interrupt, and it
     * doesn't hog the java thread.
     */
    @pausable
    public static void sleep(final long millis) {
        // create a temp mailbox, and wait on it.
        final Mailbox<Integer> sleepmb = new Mailbox<Integer>();
        timer.schedule(new TimerTask() {
            public void run() {
                sleepmb.putnb(0);
            }
        }, millis);
        sleepmb.get(); // block until a message posted
    }

    /**
     * Yield cooperatively to the next task waiting to use the thread.
     */
    @pausable
    public static void yield() {errNotWoven();}
    public static void yield(Fiber f) {
        if (f.pc == 0) {
            f.task.setPauseReason(yieldReason);
        }
        f.togglePause();
    }

    /**
     * Ask the current task to pause with a reason object, that is 
     * responsible for resuming the task when the reason (for pausing)
     * is not valid any more.
     * @param the reason
     */
    @pausable
    public static void pause(PauseReason pr) {errNotWoven();}
    public static void pause(PauseReason pr, Fiber f) {
        if (f.pc == 0) {
            f.task.setPauseReason(pr);
        } else {
            f.task.setPauseReason(null);
        }
        f.togglePause();
    }

    /*
     * This is the fiber counterpart to the execute() method
     * that allows us to detec when a subclass has not been woven.
     * 
     * If the subclass has not been woven, it won't have an
     * execute method of the following form, and this method 
     * will be called instead. 
     */
    @pausable
    public  void execute() throws Exception {
        errNotWoven(this);
    }
    
    public void execute(Fiber f) throws Exception {
        errNotWoven(this);
    }

    public String toString() {
        return "" + id + "(running=" + running + ",pr=" + pauseReason+")";
    }
    
    public String dump() {
        synchronized(this) {
            return "" + id + 
            "(running=" + running + 
            ", pr=" + pauseReason +
            ")";
        }
    }

    final void setPauseReason(PauseReason pr) {
        pauseReason = pr;
    }

    public final PauseReason getPauseReason() {
        return pauseReason;
    }

    
    public synchronized boolean isDone() {
        return done;
    }
    
    /**
     * Called by WorkerThread, it is the wrapper that performs pre and post
     * execute processing (in addition to calling the execute(fiber) method
     * of the task.
     */
    public void run() {
        Fiber f = fiber;
        boolean isDone = false; 
        try {
             // start execute. fiber is wound to the beginning.
            execute(f.begin());
        
            // execute() done. Check fiber if it is pausing and reset it.
            isDone = f.end() || (pauseReason instanceof TaskDoneReason);

        } catch (Throwable th) {
            th.printStackTrace();
            // Definitely done
            setPauseReason(new TaskDoneReason(th));
            isDone = true;
        }

        if (isDone) {
            // inform on exit
            if (exitMB != null) {
                Object exitMsg = "OK";
                if (pauseReason instanceof TaskDoneReason) {
                    exitMsg = ((TaskDoneReason)pauseReason).exitObj;
                }
                exitMB.putnb(new ExitMsg(id, exitMsg));
            }
        } else {
            synchronized (this) {
                running = false;
            }
            
            // The task has been in "running" mode until now, and may have missed
            // notifications to the pauseReason object (that is, it would have
            // resisted calls to resume(). If the pauseReason is not valid any
            // more, we'll resume. 
            if (!pauseReason.isValid()) {
                resume();
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
                return obj == this;
    }

    @Override
    public int hashCode() {
        return id;
    }
}

