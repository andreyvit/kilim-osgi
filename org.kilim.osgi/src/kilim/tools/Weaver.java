/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.tools;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import kilim.KilimException;
import kilim.analysis.ClassInfo;
import kilim.analysis.ClassWeaver;
import kilim.analysis.FileLister;

/**
 * This file creates a ClassWeaver object for each .class file to be found in
 * the args. It also expands .jar and directories recursively.
 */

public class Weaver {
    public static String outputDir = null;
    public static boolean verbose = true;
    public static Pattern excludePattern = null;

    public static void main(String[] args) throws IOException {
//        System.out.println(System.getProperty("java.class.path"));
        for (String name : parseArgs(args)) {
            try {
                if (name.endsWith(".class")) {
                    if (exclude(name))
                        continue;
                    weaveFile(name, new BufferedInputStream(
                            new FileInputStream(name)));
                } else if (name.endsWith(".jar")) {
                    for (FileLister.Entry fe : new FileLister(name)) {
                        String nm = fe.getFileName();
                        if (nm.endsWith(".class")) {
                            nm = nm.substring(0, nm.length() - 6).replace('/',
                                    '.');
                            if (exclude(nm))
                                continue;
                            weaveFile(nm, fe.getInputStream());
                        }
                    }
                } else if (new File(name).isDirectory()) {
                    for (FileLister.Entry fe : new FileLister(name)) {
                        String nm = fe.getFileName();
                        if (nm.endsWith(".class")) {
                            if (exclude(nm))
                                continue;
                            weaveFile(nm, fe.getInputStream());
                        }
                    }
                } else {
                    weaveClass(name);
                }
            } catch (KilimException ke) {
                System.err.println("Error weaving " + name + ". " + ke.getMessage());
//                ke.printStackTrace();
                System.exit(1);
            } catch (IOException ioe) {
                System.err.println("Unable to find/process '" + name + "'");
                System.exit(1);
            } catch (Throwable t) {
                System.err.println("Error weaving " + name);
                t.printStackTrace();
                System.exit(1);
            }
        }
        System.exit(0);
    }

    private static boolean exclude(String name) {
        return excludePattern == null ? false : excludePattern.matcher(name)
                .find();
    }

    static void weaveFile(String name, InputStream is) throws IOException {
        ClassWeaver cw = new ClassWeaver(is);
        writeClasses(cw);
    }

    public static void weaveClass(String name) throws IOException {
        ClassWeaver cw = new ClassWeaver(name);
        writeClasses(cw);
    }

    static void writeClasses(ClassWeaver cw) throws IOException {
        List<ClassInfo> cis = cw.getClassInfos();
        if (cis.size() > 0) {
            for (ClassInfo ci : cis) {
                writeClass(ci);
            }
        }
    }

    static void writeClass(ClassInfo ci) throws IOException {
        String dir = outputDir + "/" + getDirName(ci.className);
        mkdir(dir);
        String className = outputDir + '/' + ci.className + ".class";
        if (ci.className.startsWith("kilim/S_")) {
            // Check if we already have that file
            if (new File(className).exists())
                return;
        }
        FileOutputStream fos = new FileOutputStream(className);
        fos.write(ci.bytes);
        fos.close();
        if (verbose) {
            System.out.println("Wrote: " + className);
        }
    }

    static void mkdir(String dir) throws IOException {
        File f = new File(dir);
        if (!f.exists()) {
            if (!f.mkdirs()) {
                throw new IOException("Unable to create directory: " + dir);
            }
        }
    }

    static String getDirName(String className) {
        int end = className.lastIndexOf('/');
        return (end == -1) ? "" : className.substring(0, end);
    }

    static void help() {
        System.err
                .println("java kilim.tools.Weaver opts -d <outputDir> (class/directory/jar)+");
        System.err.println("   where opts are   -q : quiet");
        System.err
                .println("                    -x <regex> : exclude all classes matching regex");
        System.exit(1);
    }

    static ArrayList<String> parseArgs(String[] args) throws IOException {
        if (args.length == 0)
            help();

        ArrayList<String> ret = new ArrayList<String>(args.length);
        String regex = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-d")) {
                outputDir = args[++i];
            } else if (arg.equals("-q")) {
                verbose = false;
            } else if (arg.equals("-h")) {
                help();
            } else if (arg.equals("-x")) {
                regex = args[++i];
                excludePattern = Pattern.compile(regex);
            } else {
                ret.add(arg);
            }
        }
        if (outputDir == null) {
            System.err.println("Specify output directory with -d option");
            System.exit(1);
        }
        mkdir(outputDir);
        return ret;
    }
}
