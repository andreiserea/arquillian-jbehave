package org.jboss.arquillian.junit;

import org.jbehave.core.reporters.NullStoryReporter;

public class ArquillianJBehaveStoryReporter extends NullStoryReporter {

	private String crrScenario;
	
	@Override
    public void beforeScenario(String title) {
		this.crrScenario = title;
    }
	
	@Override
    public void afterScenario() {
		
    }
}
