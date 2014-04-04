package org.jboss.arquillian.jbehave.container;

import org.jbehave.core.steps.StepIndex;

public interface JBehaveProgressNotifierMXBean {

	void setNextStep(StepIndex nextStep);

	StepIndex getNextStep();

}