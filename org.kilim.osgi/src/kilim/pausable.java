/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;
import java.lang.annotation.*;


/**
 * This annotation on a method tells kilim's Weave tool to analyze
 * and transform that method.
 * 
 * The name is deliberately not capitalized -- "@Pausable" 
 * is more visually noisy and takes away from the name of the method
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface pausable {

}

