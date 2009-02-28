/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.examples;

import kilim.ExitMsg;
import kilim.Mailbox;
import kilim.pausable;
import kilim.Task;

/**
 * Spawn a task, communicate through a shared mailbox. The task's
 * termination is knowm through another mailbox.
 * 
 * The structure of this class is not much different from a Thread 
 * version that uses PipedInput/OutputStreams (Task instead of Thread,
 * execute() instead of run(), and typed, buffered mailboxes instead
 * of pipes.
 * 
 * [compile] javac -d ./classes SimpleTask.java
 * [weave]   java kilim.tools.Weave -d ./classes kilim.examples.SimpleTask
 * [run]     java -cp ./classes:./classes:$CLASSPATH  kilim.examples.SimpleTask
 */
public class SimpleTask extends Task {
    static Mailbox<String> mb = new Mailbox<String>();
    
    public static void main(String[] args) throws Exception {
        Task t = new SimpleTask().start();
        
        mb.putnb("Hello ");
        mb.putnb("World\n");
        mb.putnb("done");
    }

    /**
     * The entry point. mb.get() is a blocking call that yields
     * the thread ("pausable")
     */
    @pausable
    public void execute() {
        while (true) {
            String s = mb.get();
            if (s.equals("done")) break;
            System.out.print(s);
        }
        
        // This is not good form. Tasks shouldn't be exiting the system. 
        // See SimpleTask2 for a better way to clean up.
        System.exit(0);
    }
}
