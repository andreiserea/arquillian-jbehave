package org.jboss.arquillian.jbehave.container;

import org.jbehave.core.steps.StepIndex;

public class JBehaveProgressNotifier implements JBehaveProgressNotifierMXBean {

	public static final String JBEHAVE_PROGRESS_NOTIFIER_NAME = "org.jboss.arquillian.jbehave:type=JbehaveProgressNotifier";
	
	private StepIndex nextStep;

	public void setNextStep(StepIndex nextStep) {
		this.nextStep = nextStep;
	}
	
	public StepIndex getNextStep() {
		return this.nextStep; 
	}
}
