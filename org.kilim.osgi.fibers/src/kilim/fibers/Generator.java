/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.fibers;

import java.util.Iterator;
import java.util.NoSuchElementException;

import kilim.pausable;

/**
 * A Generator, from the caller's perspective, is a normal iterator 
 * that produces values. The difference between a Generator and a
 * regular Java iterator is that the next() method in the latter
 * must return every time, so much manage the stack explicitly. The
 * equivalent method in a generator is execute() (like a task), which
 * is pausable. 
 * 
 *  @see kilim.examples.Fib
 */

public class Generator<T> extends Task implements Iterator<T>, Iterable<T> {
    T nextVal;

    public boolean hasNext() {
        if (nextVal == null) {
            if (isDone())
                return false;
            run();
            return nextVal != null;
        } else {
            return true;
        }
    }

    public T next() {
        T ret;
        if (nextVal != null) {
            ret = nextVal;
            nextVal = null;
            return ret;
        }
        if (isDone()) {
            throw new NoSuchElementException();
        }
        run();
        ret = nextVal;
        nextVal = null;
        return ret;
    }

    public void remove() {
        throw new AssertionError("Not Supported");
    }

    public Iterator<T> iterator() {
        return this;
    }

    public @pausable
    void yield(T val) {
        nextVal = val;
        Task.yield();
    }
}
