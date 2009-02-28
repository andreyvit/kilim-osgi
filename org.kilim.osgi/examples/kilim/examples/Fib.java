/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.examples;

import java.math.BigInteger;

import kilim.Generator;
import kilim.pausable;

/**
 * This example illustrates a generator, one that generates fibonocci 
 * numbers in sequence. A generator is an iterator that returns
 * the next object 'yielded' by its execute method. 
 */
public class Fib extends Generator<BigInteger> {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println ("java kilim.examples.Fib <n> for the n_th fibonacci number");
            System.exit(0);
        }
        int n = Integer.parseInt(args[0]);
        Fib fib = new Fib();

        // Iterate through and waste the first n fibonacci numbers 
        for (int i = 0; i < n; i++) {
            fib.next(); 
        }
        // .. and print the last one
        System.out.println("" + n +  " : " + fib.next());
    }

    public @pausable void execute() {
        BigInteger i = BigInteger.ZERO;
        BigInteger j = BigInteger.ONE;
        while(true) {
            /// NOTE: Generator yields a result
            /// j is now available to the caller of this generator's next() method.
            yield(j);
            BigInteger f = i.add(j);
            i = j;
            j = f;
        }
    }
}

