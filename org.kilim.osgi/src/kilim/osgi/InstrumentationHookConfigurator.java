package kilim.osgi;import org.eclipse.osgi.baseadaptor.HookConfigurator;import org.eclipse.osgi.baseadaptor.HookRegistry;public class InstrumentationHookConfigurator implements HookConfigurator {	public void addHooks(HookRegistry hookRegistry) {		InstrumentationHook hook = new InstrumentationHook();		hookRegistry.addAdaptorHook(hook);		hookRegistry.addClassLoadingHook(hook);		hookRegistry.addClassLoaderDelegateHook(hook);	}}