package kilim.osgi;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import kilim.analysis.ClassInfo;
import kilim.analysis.ClassWeaver;
import kilim.analysis.Detector;
import kilim.tools.DumpClass;

import org.eclipse.osgi.baseadaptor.BaseAdaptor;
import org.eclipse.osgi.baseadaptor.BaseData;
import org.eclipse.osgi.baseadaptor.bundlefile.BundleEntry;
import org.eclipse.osgi.baseadaptor.hooks.AdaptorHook;
import org.eclipse.osgi.baseadaptor.hooks.ClassLoadingHook;
import org.eclipse.osgi.baseadaptor.loader.BaseClassLoader;
import org.eclipse.osgi.baseadaptor.loader.ClasspathEntry;
import org.eclipse.osgi.baseadaptor.loader.ClasspathManager;
import org.eclipse.osgi.framework.adaptor.BundleClassLoader;
import org.eclipse.osgi.framework.adaptor.BundleData;
import org.eclipse.osgi.framework.adaptor.BundleProtectionDomain;
import org.eclipse.osgi.framework.adaptor.ClassLoaderDelegate;
import org.eclipse.osgi.framework.adaptor.ClassLoaderDelegateHook;
import org.eclipse.osgi.framework.log.FrameworkLog;
import org.eclipse.osgi.internal.baseadaptor.DefaultClassLoader;
import org.eclipse.osgi.internal.loader.BundleLoader;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.packageadmin.PackageAdmin;

public class InstrumentationHook implements AdaptorHook, ClassLoadingHook,
		ClassLoaderDelegateHook {

	private static final String MANIFEST_KEY = "Kilim-Enabled";

	private Map<String, byte[]> additionalClasses = new HashMap<String, byte[]>();

	private static InstrumentationContext instrumentationContext;

	private BundleContext context;

	private PackageAdmin packageAdmin;

	private DefaultClassLoader fibersPluginClassLoader;

	public static Class<?> loadClass(String name) throws ClassNotFoundException {
		return instrumentationContext.loadClass(name);
	}

	public void frameworkStart(BundleContext context) throws BundleException {
		this.context = context;
		ServiceReference ref = context.getServiceReference(PackageAdmin.class
				.getName());
		packageAdmin = (PackageAdmin) context.getService(ref);
	}

	public void frameworkStop(BundleContext context) throws BundleException {
	}

	public byte[] processClass(String name, byte[] classbytes,
			ClasspathEntry classpathEntry, BundleEntry entry,
			ClasspathManager manager) {
		try {
			Object value = manager.getBaseData().getManifest()
					.get(MANIFEST_KEY);
			if (!(value instanceof String && Boolean
					.parseBoolean((String) value)))
				return null;
		} catch (BundleException e) {
			throw new AssertionError(e);
		}
		
//		System.out.println("InstrumentationHook.processClass(" + name + " )");
		if (instrumentationContext != null
				&& instrumentationContext.isBeingInstrumented(name)) {
//			System.out.println("InstrumentationHook.processClass(" + name
//					+ " ) throwing AlreadyBeingInstrumentedError");
			// note: this exception is caught by MethodFlow
			throw new AlreadyBeingInstrumentedError();
		}
		String sn = manager.getBaseData().getSymbolicName();
		// System.out.println("Instrumenting with Kilim: "
		// + name
		// + " ["
		// + (instrumentationContext == null ? 1
		// : 1 + instrumentationContext.depth()) + "] / "
		// + sn);
		Bundle[] bundles = packageAdmin.getBundles(sn, null);
		if (bundles.length == 0)
			throw new AssertionError("Could not find instrumented bundle: "
					+ sn);
		instrumentationContext = new InstrumentationContext(
				instrumentationContext, bundles[0], name);
		try {
			ClassWeaver weaver = new ClassWeaver(new ByteArrayInputStream(
					classbytes), instrumentationContext);
			List<ClassInfo> infos = weaver.getClassInfos();
			byte[] result = null;
			String nameWithSlashes = name.replace('.', '/');
			for (ClassInfo info : infos) {
				if (info.className.equals(nameWithSlashes))
					result = info.bytes;
				else {
					// System.out.println("Adding additonal class: " +
					// info.className);
					additionalClasses.put(info.className, info.bytes);
				}
			}
			if (result == null)
				return null;
//			if (name.endsWith(".ConstructControlFlowTraverser"))
//				new DumpClass(new ByteArrayInputStream(result), false);
			return result;
			// } catch (IOException e) {
			// e.printStackTrace();
			// throw new AssertionError(e);
		} catch (Throwable e) {
			e.printStackTrace();
			return null;
		} finally {
			instrumentationContext = instrumentationContext.parent;
		}
	}

	public BaseClassLoader createClassLoader(ClassLoader parent,
			ClassLoaderDelegate delegate, BundleProtectionDomain domain,
			BaseData data, String[] bundleclasspath) {
		BundleLoader loader = (BundleLoader) delegate;
		try {
			loader.addDynamicImportPackage(ManifestElement.parseHeader(
					Constants.DYNAMICIMPORT_PACKAGE, "kilim,kilim.analysis"));
		} catch (BundleException be) {
			throw new AssertionError(be);
		}
		if (data.getBundle().getSymbolicName().equals("org.kilim.osgi.fibers")) {
			fibersPluginClassLoader = new DefaultClassLoader(parent, delegate,
					domain, data, bundleclasspath) {
				@Override
				public Class<?> loadClass(String name)
						throws ClassNotFoundException {
					Class<?> result = findLoadedClass(name);
					if (result != null)
						return result;
					byte[] klass = additionalClasses
							.get(name.replace('.', '/'));
					if (klass != null) {
						// System.out.println("Loading additonal class: " +
						// name);
						return defineClass(name, klass, 0, klass.length);
					}
					return super.loadClass(name);
				}
			};
			return fibersPluginClassLoader;
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public Class preFindClass(String name, BundleClassLoader classLoader,
			BundleData data) throws ClassNotFoundException {
		if (name.startsWith("kilim.states."))
			if (fibersPluginClassLoader != null)
				return fibersPluginClassLoader.loadClass(name);
		return null;
	}

	// Methods stubs for hooks we do not require:

	@SuppressWarnings("unchecked")
	public boolean addClassPathEntry(ArrayList cpEntries, String cp,
			ClasspathManager hostmanager, BaseData sourcedata,
			ProtectionDomain sourcedomain) {
		return false;
	}

	public String findLibrary(BaseData data, String libName) {
		return null;
	}

	public ClassLoader getBundleClassLoaderParent() {
		return null;
	}

	public void initializedClassLoader(BaseClassLoader baseClassLoader,
			BaseData data) {
	}

	public void addProperties(Properties properties) {
	}

	public FrameworkLog createFrameworkLog() {
		return null;
	}

	public void frameworkStopping(BundleContext context) {
	}

	public void handleRuntimeError(Throwable error) {
	}

	public void initialize(BaseAdaptor adaptor) {
	}

	public URLConnection mapLocationToURLConnection(String location)
			throws IOException {
		return null;
	}

	public boolean matchDNChain(String pattern, String[] dnChain) {
		return false;
	}

	public Class postFindClass(String name, BundleClassLoader classLoader,
			BundleData data) throws ClassNotFoundException {
		return null;
	}

	public String postFindLibrary(String name, BundleClassLoader classLoader,
			BundleData data) {
		return null;
	}

	public URL postFindResource(String name, BundleClassLoader classLoader,
			BundleData data) throws FileNotFoundException {
		return null;
	}

	public Enumeration postFindResources(String name,
			BundleClassLoader classLoader, BundleData data)
			throws FileNotFoundException {
		return null;
	}

	public String preFindLibrary(String name, BundleClassLoader classLoader,
			BundleData data) throws FileNotFoundException {
		return null;
	}

	public URL preFindResource(String name, BundleClassLoader classLoader,
			BundleData data) throws FileNotFoundException {
		return null;
	}

	public Enumeration preFindResources(String name,
			BundleClassLoader classLoader, BundleData data)
			throws FileNotFoundException {
		return null;
	}

}