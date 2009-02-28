/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.fibers;


/**
 * @see Task#pause(PauseReason)
 */
public interface PauseReason {
    /**
     * True if the reason for pausing continues to be valid.
     */
    boolean isValid();
}
