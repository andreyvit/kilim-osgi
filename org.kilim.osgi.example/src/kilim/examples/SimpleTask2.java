/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.examples;

import kilim.ExitMsg;
import kilim.pausable;
import kilim.fibers.Mailbox;
import kilim.fibers.Task;

/**
 * A slight extension to SimpleTask. This 
 * 
 * [compile] javac -d ./classes SimpleTask2.java
 * [weave]   java kilim.tools.Weave -d ./classes kilim.examples.SimpleTask
 * [run]     java -cp ./classes:./classes:$CLASSPATH  kilim.examples.SimpleTask2
 */
public class SimpleTask2 extends Task {
    public static Mailbox<String> mb = new Mailbox<String>();
    public static Mailbox<ExitMsg> exitmb = new Mailbox<ExitMsg>();
    
    @pausable
    public void execute() {
    	StringBuilder r = new StringBuilder();
        while (true) {
            String s = mb.get();
            if (s.equals("done"))
            	break;
            System.out.print(s);
            r.append(s);
        }
        Task.exit(r.toString());
    }
}
