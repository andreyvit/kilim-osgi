/**
 * 
 */
package kilim.osgi;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import kilim.analysis.ClassFlow;
import kilim.analysis.ClassWeaver;

import org.osgi.framework.Bundle;

public class InstrumentationContext {

	public final InstrumentationContext parent;
	private final Bundle bundle;
	private final String klassName;
	private ClassFlow classFlow;
	private final String klassNameWithSlashes;
	
	private static final Map<String, ClassFlow> instrumentedFlows =
		Collections.synchronizedMap(new HashMap<String, ClassFlow>());

	public InstrumentationContext(InstrumentationContext parent,
			Bundle bundle, String klassName) {
		if (bundle == null)
			throw new NullPointerException("bundle is null");
		if (klassName == null)
			throw new NullPointerException("klassName is null");
		this.parent = parent;
		this.bundle = bundle;
		this.klassName = klassName;
		this.klassNameWithSlashes = klassName.replace('.', '/');
	}
	
	boolean isBeingInstrumented(String name) {
		if (this.klassName.equals(name))
			return true;
		if (parent != null)
			return parent.isBeingInstrumented(name);
		else
			return false;
	}

	public Class<?> loadClass(String name) throws ClassNotFoundException {
		if (isBeingInstrumented(name))
			throw new ClassNotFoundException("It's being instrumented, dude!");
		System.out.println("Loading " + name + " from " + bundle.getSymbolicName());
		if (name.equals("com.esko.dtl.core.runtime.DtlMethod"))
			System.out.println("InstrumentationContext.loadClass(): breakpoint");
		Class<?> klass = bundle.loadClass(name);
//		try {
//			klass.getDeclaredMethods();
//		} catch (LinkageError e) {
//			System.out.println("InstrumentationContext.loadClass(" + name + "): LinkageError " + e.getMessage());
//			System.out.println();
//		}
		return klass;
	}

	public ClassFlow findClassFlow(String name) {
		return instrumentedFlows.get(name);
//		if (this.klassNameWithSlashes.equals(name))
//			return getClassFlow();
//		if (parent != null)
//			return parent.findClassFlow(name);
//		else
//			return null;
	}
	
	public int depth() {
		return 1 + (parent == null ? 0 : parent.depth());
	}

	public void setClassFlow(ClassFlow classFlow) {
		this.classFlow = classFlow;
		instrumentedFlows.put(klassNameWithSlashes, classFlow);
	}

	public ClassFlow getClassFlow() {
		return classFlow;
	}

}