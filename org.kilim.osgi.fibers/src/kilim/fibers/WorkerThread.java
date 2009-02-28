/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.fibers;


public class WorkerThread extends Thread {
    Scheduler scheduler; 
    WorkerThread(Scheduler ascheduler) {scheduler=ascheduler;}
    
    public void run() {
        while (true) {
            Task t = scheduler.getNextTask(this); // blocks until task available
            if (t == null) break; // scheduler shut down
            t._runExecute();
        }
    }            
}
