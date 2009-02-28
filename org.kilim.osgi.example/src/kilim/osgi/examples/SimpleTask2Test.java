package kilim.osgi.examples;

import junit.framework.Assert;

import org.junit.Test;

import kilim.examples.SimpleTask2;
import kilim.fibers.Task;

public class SimpleTask2Test {

	@Test
	public void simpleTask2() {
        Task t = new SimpleTask2().start();
        t.informOnExit(SimpleTask2.exitmb);
        SimpleTask2.mb.putnb("Hello ");
        SimpleTask2.mb.putnb("World\n");
        SimpleTask2.mb.putnb("done");
        
        Object result = SimpleTask2.exitmb.getb().result;
        Assert.assertEquals("Hurray", result.toString());
	}


}
