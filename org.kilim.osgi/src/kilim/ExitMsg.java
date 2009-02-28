/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

/**
 * @see kilim.fibers.Task#informOnExit(Mailbox)
 */
public class ExitMsg {
    public int taskId ; // task id
    public Object result; // contains Throwable if exitCode == 1
    public ExitMsg(int id, Object res) {
        taskId  = id;
        result = res;
    }
    
    public String toString() {
        return "exit(" + taskId + "), result = " + result;
    }
}
