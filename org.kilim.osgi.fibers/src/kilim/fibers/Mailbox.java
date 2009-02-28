/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.fibers;

import java.util.LinkedList;

import kilim.pausable;

/**
 * This is a typed buffer that supports multiple producers and a single
 * consumer. It is the basic construct used for tasks to interact and
 * synchronize with each other (as opposed to direct java calls or static member
 * variables). put() and get() are the two essential functions.
 * 
 * We use the term "block" to mean thread block, and "pause" to mean
 * fiber pausing. The suffix "nb" on some methods (such as getnb())
 * stands for non-blocking.
 */

public class Mailbox<T> {
    // TODO. Give mbox a config name and id and make monitorable
    T[] msgs;
    private int iprod = 0; // producer index
    private int icons = 0; // consumer index;
    private int numMsgs = 0;
    private int maxMsgs = 300;
    MsgAvListener sink;
    LinkedList<SpcAvListener> srcs = new LinkedList<SpcAvListener>();

    // DEBUG stuff
    // To do: move into monitorable stat object
    /*
     * public int nPut = 0; public int nGet = 0; public int nWastedPuts = 0;
     * public int nWastedGets = 0;
     */
    public Mailbox() {
        this(10);
    }

    public Mailbox(int initialSize) {
        this(10, Integer.MAX_VALUE);
    }

    @SuppressWarnings("unchecked")
    public Mailbox(int initialSize, int maxSize) {
        if (initialSize > maxSize)
            throw new IllegalArgumentException("initialSize: " + initialSize
                    + " cannot exceed maxSize: " + maxSize);
        msgs = (T[]) new Object[initialSize];
        maxMsgs = maxSize;
    }

    /**
     * Get, don't pause or block.
     * 
     * @return stored message
     */
    public T getnb() {
        T msg = deq();
        if (msg != null) {
            notifySpaceAvailable();
        }
        return msg;
    }

    @pausable
    public T get() {
        T msg = deq();
        while (msg == null) {
            Task t = Task.getCurrentTask();
            Empty_MsgAvListener pauseReason = new Empty_MsgAvListener(t, this);
            addMsgAvailableListener(pauseReason);
            // The task will have to reevaluate the reason for pausing before it
            // changes
            // its status (from running to waiting)
            Task.pause(pauseReason);
            removeMsgAvailableListener(pauseReason);
            msg = deq();
        }
        notifySpaceAvailable();
        return msg;
    }

//    /**
//     * Takes an array of mailboxes and returns the index of the first mailbox
//     * that has a message. It is possible that because of race conditions, an
//     * earlier mailbox in the list may also have received a message.
//     */
//    @pausable
//    public static int select(Mailbox... mboxes) {
//        while (true) {
//            for (int i = 0; i < mboxes.length; i++) {
//                if (mboxes[i].hasMessage()) {
//                    return i;
//                }
//            }
//            Task t = Task.getCurrentTask();
//            EmptySet_MsgAvListener pauseReason = new EmptySet_MsgAvListener(t,
//                    mboxes);
//            for (int i = 0; i < mboxes.length; i++) {
//                mboxes[i].addMsgAvailableListener(pauseReason);
//            }
//            Task.pause(pauseReason);
//            for (int i = 0; i < mboxes.length; i++) {
//                mboxes[i].removeMsgAvailableListener(pauseReason);
//            }
//        }
//    }

    public synchronized void addSpaceAvailableListener(SpcAvListener spcOb) {
        srcs.add(spcOb);
    }

    public synchronized void removeSpaceAvailableListener(SpcAvListener spcOb) {
        srcs.remove(spcOb);
    }

    public synchronized void addMsgAvailableListener(MsgAvListener msgOb) {
        if (sink != null) {
            throw new AssertionError(
                    "Error: A mailbox can not be shared by two consumers. New = "
                            + ((Empty_MsgAvListener) msgOb).task
                            + ((Empty_MsgAvListener) sink).task);
        }
        sink = msgOb;
    }

    public synchronized void removeMsgAvailableListener(MsgAvListener obj) {
        if (sink == obj) {
            sink = null;
        }
    }

    private void notifySpaceAvailable() {
        SpcAvListener src;
        synchronized (this) {
            // Ensure there is some space before notifying the spc listeners
            // The mbox may have filled up between put calling enq() and this
            // method. This reduces the number of false starts.
            if ((maxMsgs - numMsgs) == 0)
                return;
            src = srcs.poll();
        }
        if (src != null) {
            src.spaceAvailable(this);
        }
    }

    private void notifyMsgAvailable() {
        MsgAvListener snk;
        synchronized (this) {
            snk = sink;
            // Ensure that a msg is available before notifying
            if (snk == null || numMsgs == 0)
                return;
            sink = null;
        }
        snk.msgAvailable(this);
    }

    synchronized T deq() {
        int n = numMsgs;
        if (n == 0)
            return null;
        int ic = icons;
        T msg = msgs[ic];
        icons = (ic + 1) % msgs.length;
        numMsgs = n - 1;
        // DEBUG
        // nGet++;
        // if (msg == null) nWastedGets++;
        return msg;
    }

    public boolean putnb(T msg) {
        if (enq(msg)) {
            notifyMsgAvailable();
            return true;
        }
        return false;
    }

    @pausable
    public void put(T msg) {
        Task t = Task.getCurrentTask();
        while (!enq(msg)) {
            Full_SpcAvListener pauseReason = new Full_SpcAvListener(t, this);
            addSpaceAvailableListener(pauseReason);
            Task.pause(pauseReason);
            removeSpaceAvailableListener(pauseReason);
        }
        notifyMsgAvailable();
    }

    public void putb(T msg) {
        putb(msg, 0 /* infinite wait */);
    }

    public void putb(T msg, final long millis) {
        long remaining = millis;
        long start = System.currentTimeMillis();
        boolean enqueued = false;
        synchronized (this) {
            while (true) {
                SpcAvListener listener = null;
                enqueued = enq(msg);
                if (enqueued)
                    break;

                listener = new SpcAvListener() {
                    public void spaceAvailable(Mailbox MB) {
                        synchronized (Mailbox.this) {
                            Mailbox.this.notify();
                        }
                    }
                };
                addSpaceAvailableListener(listener);
                try {
                    wait(remaining);
                } catch (InterruptedException ignore) {
                }
                removeSpaceAvailableListener(listener);

                if (millis != 0) {
                    // Check for spurious breaks
                    long elapsed = System.currentTimeMillis() - start;
                    if (elapsed < millis) {
                        remaining -= elapsed;
                    } else
                        break;
                }
            }
            if (enqueued) {
                notifyMsgAvailable();
            }
        }
    }

    /*
     * returns true if able to enq in a ring buffer.
     */
    @SuppressWarnings("unchecked")
    private synchronized boolean enq(T msg) {
        if (msg == null) {
            throw new NullPointerException("Null message supplied to put");
        }
        int ip = iprod;
        int ic = icons;
        int n = numMsgs;
        if (n == msgs.length) {
            assert ic == ip : "numElements == msgs.length && ic != ip";
            if (n == maxMsgs) {
                return false;
            }
            T[] newmsgs = (T[]) new Object[Math.min(n * 2, maxMsgs)];
            System.arraycopy(msgs, ic, newmsgs, 0, n - ic);
            if (ic > 0) {
                System.arraycopy(msgs, 0, newmsgs, n - ic, ic);
            }
            msgs = newmsgs;
            ip = n;
            ic = 0;
        }
        numMsgs = n + 1;
        msgs[ip] = msg;
        iprod = (ip + 1) % msgs.length;
        icons = ic;
        // nPut++;
        return true; // for now, no bounds enforced
    }

    public synchronized boolean hasMessage() {
        return numMsgs > 0;
    }

    public synchronized boolean hasSpace() {
        return (maxMsgs - numMsgs) > 0;
    }

    /**
     * retrieve a message, blocking the thread indefinitely. Note, this is a
     * heavyweight block, unlike #get() that pauses the Fiber but doesn't block
     * the thread.
     */

    public T getb() {
        return getb(0);
    }

    /**
     * retrieve a msg, and block the Java thread for the time given.
     * 
     * @param millis.
     *            max wait time
     * @return null if timed out.
     */
    public T getb(final long millis) {
        long remaining = millis;
        long start = System.currentTimeMillis();
        T msg = null;
        synchronized (this) {
            while (true) {
                MsgAvListener listener = null;
                msg = deq();
                if (msg != null) {
                    break;
                }
                listener = new MsgAvListener() {
                    public void msgAvailable(Mailbox MB) {
                        synchronized (Mailbox.this) {
                            Mailbox.this.notify();
                        }
                    }
                };
                addMsgAvailableListener(listener);
                try {
                    wait(remaining);
                } catch (InterruptedException ignore) {
                }
                removeMsgAvailableListener(listener);

                if (millis != 0) {
                    // Check for spurious breaks
                    long elapsed = System.currentTimeMillis() - start;
                    if (elapsed < millis) {
                        remaining -= elapsed;
                    } else
                        break;
                }
            }
            if (msg != null) {
                notifySpaceAvailable();
            }
            return msg;
        }
    }

    public synchronized String toString() {
        return "id:" + System.identityHashCode(this) + " " +
        // DEBUG "nGet:" + nGet + " " +
                // "nPut:" + nPut + " " +
                // "numWastedPuts:" + nWastedPuts + " " +
                // "nWastedGets:" + nWastedGets + " " +
                "numMsgs:" + numMsgs;
    }
}

class Empty_MsgAvListener implements PauseReason, MsgAvListener {
    final Task task;
    final Mailbox mbx;

    // DEBUG
    // boolean notified = false;

    Empty_MsgAvListener(Task t, Mailbox mb) {
        task = t;
        mbx = mb;
    }

    public boolean isValid() {
        // The pauseReason is "Empty" if the mbox has no message
        return !mbx.hasMessage();
    }

    public void msgAvailable(Mailbox mb) {
        // DEBUG notified = true;
        task.resume();
    }

    public String toString() {
        return " Waiting for msg = " + isValid()
        // + ", notified: " + notified
        ;
    }

    public void cancel() {
        mbx.removeMsgAvailableListener(this);
    }

}

class Full_SpcAvListener implements PauseReason, SpcAvListener {
    final Task task;
    final Mailbox mbx;

    Full_SpcAvListener(Task t, Mailbox mb) {
        task = t;
        mbx = mb;
    }

    public boolean isValid() {
        // The pauseReason is "Full" if the mbox has no space available
        return !mbx.hasSpace();
    }

    public void spaceAvailable(Mailbox mb) {
        task.resume();
    }

    public void cancel() {
        mbx.removeSpaceAvailableListener(this);
    }
}

class EmptySet_MsgAvListener implements PauseReason, MsgAvListener {
    final Task task;
    final Mailbox[] mbxs;

    EmptySet_MsgAvListener(Task t, Mailbox[] mbs) {
        task = t;
        mbxs = mbs;
    }

    public boolean isValid() {
        // The pauseReason is "Empty" if the none of the mboxes have any
        // elements
        for (Mailbox mb : mbxs) {
            if (mb.hasMessage())
                return false;
        }
        return true;
    }

    public void msgAvailable(Mailbox mb) {
        task.resume();
        for (Mailbox m : mbxs) {
            if (m != mb) {
                mb.removeMsgAvailableListener(this);
            }
        }
    }

    public void cancel() {
        for (Mailbox mb : mbxs) {
            mb.removeMsgAvailableListener(this);
        }
    }
}
