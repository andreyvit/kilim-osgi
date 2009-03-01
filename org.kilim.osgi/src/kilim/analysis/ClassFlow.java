/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */
package kilim.analysis;
import kilim.*;
import kilim.osgi.InstrumentationContext;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/**
 * This class reads a .class file (or stream), wraps each method with a MethodFlow
 * object and optionally analyzes it.
 * 

 */
public class ClassFlow extends ClassNode {
    ArrayList<MethodFlow> methodFlows;
    ClassReader cr;
    String classDesc;
    /**
     * true if any of the methods contained in the class file is pausable.
     * ClassWeaver uses it later to avoid weaving if isPausable isn't true.
     */
    private boolean isPausable;
    /**
     * true if the .class being read is already woven. All woven files have
     * a pausable annotation on the entire class.
     */
    private boolean isWoven = false;
    
    final Map<String, MethodFlow> methodsByName = new HashMap<String, MethodFlow>();
    
	private final InstrumentationContext context;
    
    public ClassFlow(InputStream is, InstrumentationContext context) throws IOException {
		cr = new ClassReader(is);
		this.context = context;
		this.context.setClassFlow(this);
    }
    
    public ClassFlow(String aClassName, InstrumentationContext context) throws IOException {
		cr = new ClassReader(aClassName);
		this.context = context;
		this.context.setClassFlow(this);
    }
    
    @SuppressWarnings({"unchecked"})
    public MethodVisitor visitMethod(
            final int access,
            final String name,
            final String desc,
            final String signature,
            final String[] exceptions)
    {
        MethodFlow mn = new MethodFlow( this, access, name,  desc, signature,
                exceptions, context);
        super.methods.add(mn);
        methodsByName.put(name + "|" + desc, mn);
        return mn;
    }
    
    public ArrayList<MethodFlow> getMethodFlows(){
        assert (methodFlows != null): "ClassFlow.analyze not called";
        return methodFlows;
    }

    public ArrayList<MethodFlow> analyze(boolean forceAnalysis) throws KilimException {
//        cr.accept(this, ClassReader.SKIP_DEBUG);
        cr.accept(this, false);
        cr = null; // We don't need this any more.
        classDesc = TypeDesc.getInterned("L" + name + ';');
        ArrayList<MethodFlow> flows = new ArrayList<MethodFlow>(methods.size());
        
        for (Object o: methods) {
        	MethodFlow mf = (MethodFlow)o;
        	mf.postProcess();
        }
        
        for (Object o: methods) {
            MethodFlow mf = (MethodFlow)o;
            if (mf.isBridge()) {
            	MethodInsnNode node = mf.findOnlyCallInstruction();
            	if (node != null)
            		mf.setPausable(Detector.isPausable(node.owner, node.name, node.desc, context));
            	else 
            		System.out.println("ClassFlow[" + name +"].analyze(): multiple calls in a bridge method " + mf.name + " " + mf.desc);
            }
            mf.verifyPausables();
            if (mf.isPausable()) isPausable = true;
            if ((mf.isPausable() || forceAnalysis) && (!mf.isAbstract())) {
                mf.analyze();
            }
            flows.add(mf);
        }
        methodFlows = flows;
        return flows;
    }
    
    public String getClassDescriptor() { return classDesc; }
    
    public String getClassName() { return super.name.replace('/', '.');}

    public boolean isPausable() {
        getMethodFlows(); // check analyze has been run.
        return isPausable;
    }
    
    public boolean isWoven() {
        return isWoven;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        AnnotationVisitor ret = super.visitAnnotation(desc, visible);
        if (desc.equals(Constants.D_PAUSABLE)) {
            isWoven = true;
        }
        return ret;
    }

     boolean isInterface() {
        return (this.access & Constants.ACC_INTERFACE) != 0;
    }
}
