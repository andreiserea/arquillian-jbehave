package org.jboss.arquillian.junit;

import java.util.ArrayList;
import java.util.List;

import org.jbehave.core.ConfigurableEmbedder;
import org.jboss.resteasy.client.ClientRequest;
import org.junit.internal.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;

/**
 * An extended Arquillian Jbehave runner that also flushes the cobertura coverage data
 * 
 * @author Andrei Serea
 * 
 */
public class ArquillianJbehaveCoverageRunner extends ArquillianJbehaveRunner {

	private static String CONTAINER_ADDRESS;

	public ArquillianJbehaveCoverageRunner(Class<? extends ConfigurableEmbedder> testClass) throws Throwable {
		super(testClass);
		if (!isRunningRemote) {
			CONTAINER_ADDRESS = System.getProperty("arquillian.container.address");
		}
	}

	@Override
	protected Statement withAfterClasses(final Statement originalStatement) {
		if (!isRunningRemote) {
			// only in junit jvm, before uninstalling the app in the container, flush the cobertura
			return super.withAfterClasses(new Statement() {
				@Override
				public void evaluate() throws Throwable {
					List<Throwable> exceptions = new ArrayList<Throwable>();
					try {
						originalStatement.evaluate();
					} catch (Throwable e) {
						exceptions.add(e);
					}
					// call cobertura while app is still loaded to flush coverage data
					ClientRequest request = new ClientRequest(String.format(
							"http://%s:8080/coberturaMC/flushCobertura", CONTAINER_ADDRESS));
					try {
						request.post();
					} catch (Exception e) {
						exceptions.add(e);
					}
					if (exceptions.isEmpty()) {
						return;
					}
					if (exceptions.size() == 1) {
						throw exceptions.get(0);
					}
					throw new MultipleFailureException(exceptions);
				}

			});
		} else {
			return super.withAfterClasses(originalStatement);
		}
	}
}