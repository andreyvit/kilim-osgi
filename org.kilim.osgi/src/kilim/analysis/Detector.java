/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */
package kilim.analysis;
import java.lang.reflect.Method;

import kilim.Constants;
import kilim.pausable;
import kilim.osgi.AlreadyBeingInstrumentedError;
import kilim.osgi.InstrumentationContext;
import kilim.osgi.InstrumentationHook;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodInsnNode;
/**
 * Utility class to check if a method has been marked pausable

 *
 */
public class Detector {
    public static final int                   METHOD_NOT_FOUND         = 0;
    public static final int                   PAUSABLE_METHOD_FOUND    = 1;
    public static final int                   METHOD_NOT_PAUSABLE      = 2;

    // Note that we don't have the kilim package itself in the following list.
    static final String[]                     STANDARD_DONT_CHECK_LIST = {
            "java.", "javax."                                         };

    public static boolean isPausable(String className, String methodName,
            String desc, InstrumentationContext context) {
        return getPausableStatus(className, methodName, desc, context) == PAUSABLE_METHOD_FOUND;
    }

    /**
     * @return one of METHOD_NOT_FOUND, PAUSABLE_METHOD_FOUND, METHOD_NOT_PAUSABLE
     */
    
    public static int getPausableStatus(String className, String methodName,
            String desc, InstrumentationContext context) 
    {
        int ret = METHOD_NOT_FOUND;
        if (methodName.endsWith("init>")) {
            return METHOD_NOT_PAUSABLE; // constructors are not pausable.
        }
        if (methodName.equals("staticContext"))
        	System.out.println("Detector.getPausableStatus()");
        ClassFlow cf = context.findClassFlow(className);
        if (cf == null) {
        	try {
        		String dottedClassName = className.replace('/', '.');
        		try {
        			Class cl = InstrumentationHook.loadClass(dottedClassName);
        	        cf = context.findClassFlow(className);
        			if (cf == null) {
        				Method m = findMethodInHierarchy(cl, methodName, desc);

        				if (m != null) {
        					ret = m.isAnnotationPresent(pausable.class) ? 
        							PAUSABLE_METHOD_FOUND : METHOD_NOT_PAUSABLE;
        				}
        			}
        		} catch (ClassNotFoundException ignore) {}
        	} catch (AlreadyBeingInstrumentedError e) {
        		cf = context.findClassFlow(className);
        	}
        }
		if (ret == Detector.METHOD_NOT_FOUND && cf != null) {
			MethodFlow mf = cf.methodsByName.get(methodName + "|" + desc);
			if (mf == null) {
				String sup = cf.superName;
				if (sup != null) {
					ret = getPausableStatus(sup, methodName, desc, context);
					if (ret != METHOD_NOT_FOUND)
						return ret;
				}
				if (cf.interfaces != null)
					for (Object n : cf.interfaces) {
						String name = (String) n;
						ret = getPausableStatus(name, methodName, desc, context);
						if (ret != METHOD_NOT_FOUND)
							return ret;
					}
				return METHOD_NOT_FOUND;
			}
			ret = Detector.METHOD_NOT_PAUSABLE;
			if (mf.isBridge()) {
				MethodInsnNode call = mf.findOnlyCallInstruction();
				if (call != null)
					return getPausableStatus(call.owner, call.name, call.desc, context);
			}
			if (mf.isPausable())
				ret = Detector.PAUSABLE_METHOD_FOUND;
//			if (mf.visibleAnnotations != null)
//				for (Object obj : mf.visibleAnnotations) {
//					AnnotationNode an = (AnnotationNode) obj;
//					if (an.desc.equals(Constants.D_PAUSABLE)) {
//						ret = Detector.PAUSABLE_METHOD_FOUND;
//						break;
//					}
//				}
		}
		return ret;
    }
    
    public static Method findMethodInHierarchy(Class cl, String methodName,
            String desc) {
        if (cl == null)  return null;
//        System.out.println("Detector.findMethodInHierarchy(" + cl.getSimpleName() + "." + methodName +")");
        
        for (Method om : cl.getDeclaredMethods()) {
            if (om.getName().equals(methodName) && Type.getMethodDescriptor(om).equals(desc)) {
                if (om.isBridge()) continue;
                return om;
            }
        }

        if (cl == Object.class)
            return null;

        Method m = findMethodInHierarchy(cl.getSuperclass(), methodName, desc);
        if (m != null)
            return m;
        for (Class ifcl : cl.getInterfaces()) {
            m = findMethodInHierarchy(ifcl, methodName, desc);
            if (m != null)
                return m;
        }
        return null;
    }

    public static String D_FIBER_ = Constants.D_FIBER + ")";
    public static int _getPausableStatus(String className, String methodName,
            String desc) {
        int ret;
        className = className.replace('/', '.');
        try {
            Class cl = InstrumentationHook.loadClass(className);
            Method m = findMethodInHierarchy(cl, methodName, desc);
            if (m == null) {
                desc = desc.replace(")", D_FIBER_);
                m = findMethodInHierarchy(cl, methodName, desc);
            }
            ret = (m == null ) ? METHOD_NOT_FOUND : 
                   m.isAnnotationPresent(pausable.class) ? 
                           PAUSABLE_METHOD_FOUND : METHOD_NOT_PAUSABLE;
        } catch (ClassNotFoundException cnfe) {
            ret = METHOD_NOT_FOUND;
        }
        System.out.println("Detector : " + className + methodName + desc + " -- "  + statusToStr(ret));
        return ret;
    }
    private static String statusToStr(int st) {
        switch (st) {
        case METHOD_NOT_FOUND : return "not found";
        case PAUSABLE_METHOD_FOUND : return "pausable";
        case METHOD_NOT_PAUSABLE : return "not pausable";
        default: throw new AssertionError("Unknown status");
        }
    }

    public static Method _findMethodInHierarchy(Class cl, String methodName,
            String desc) {
        if (cl == null)
            return null;
        if (notPausable(cl.getName()) || methodName.equals("<init>")
                || methodName.equals("<clinit>"))
            return null;

        for (Method om : cl.getDeclaredMethods()) {
            if (om.getName().equals(methodName) && Type.getMethodDescriptor(om).equals(desc)) {
                return om;
            }
        }

        if (cl == Object.class)
            return null;

        Method m = findMethodInHierarchy(cl.getSuperclass(), methodName, desc);
        if (m != null)
            return m;
        for (Class ifcl : cl.getInterfaces()) {
            m = findMethodInHierarchy(ifcl, methodName, desc);
            if (m != null)
                return m;
        }
        return null;
    }

    static String[] dontCheckList = null;

    private static boolean notPausable(String name) {
        if (dontCheckList == null) {
            initDontCheckList();
        }
        for (String pkgPrefix : dontCheckList) {
            if (name.startsWith(pkgPrefix))
                return true;
        }
        return false;
    }

    private static void initDontCheckList() {
        try {
            String pkgs = System.getProperty("kilim.notPausablePackages");
            if (pkgs != null) {
                String[] pkgList = pkgs.split(";");
                for (int i = 0; i < pkgList.length; i++) {
                    String name = pkgList[i];
                    if (name.endsWith(".*")) {
                        // remove the '*'
                        name = name.substring(0, name.length() - 2);
                        pkgList[i] = name;
                    }
                }
                dontCheckList = new String[pkgList.length
                        + STANDARD_DONT_CHECK_LIST.length];
                System.arraycopy(pkgList, 0, dontCheckList, 0, pkgList.length);
                System.arraycopy(pkgList, pkgList.length, STANDARD_DONT_CHECK_LIST, 0, STANDARD_DONT_CHECK_LIST.length);
            }
        } catch (Exception ignore) {
        }

        if (dontCheckList == null) {
            dontCheckList = STANDARD_DONT_CHECK_LIST;
        }
    }
}
