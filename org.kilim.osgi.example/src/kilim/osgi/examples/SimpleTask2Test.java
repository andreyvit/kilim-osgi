package kilim.osgi.examples;

import java.util.concurrent.Executors;

import junit.framework.Assert;
import kilim.examples.SimpleTask2;
import kilim.fibers.Task;

import org.junit.Test;

public class SimpleTask2Test {

	@Test
	public void simpleTask2() {
        Task t = new SimpleTask2().start(Executors.newSingleThreadExecutor());
        t.informOnExit(SimpleTask2.exitmb);
        SimpleTask2.mb.putnb("Hello ");
        SimpleTask2.mb.putnb("World\n");
        SimpleTask2.mb.putnb("done");
        
        Object result = SimpleTask2.exitmb.getb().result;
        Assert.assertEquals("Hurray", result.toString());
	}


}
