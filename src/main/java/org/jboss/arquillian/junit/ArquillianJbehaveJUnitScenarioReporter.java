package org.jboss.arquillian.junit;

import org.jbehave.core.configuration.Keywords;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;

import de.codecentric.jbehave.junit.monitoring.JUnitScenarioReporter;

/**
 * A JUnit scenario reporter integrated with {@link ArquillianJbehaveRunner}
 * @author Andrei Serea
 *
 */
public class ArquillianJbehaveJUnitScenarioReporter extends
		JUnitScenarioReporter {

	public ArquillianJbehaveJUnitScenarioReporter(RunNotifier notifier,
			int totalTests, Description rootDescription, Keywords keywords) {
		super(notifier, totalTests, rootDescription, keywords, false);
	}
	
	@Override
	public void afterScenario() {
		super.afterScenario();
		ArquillianJbehaveRunner.afterScenario();
	}

}
