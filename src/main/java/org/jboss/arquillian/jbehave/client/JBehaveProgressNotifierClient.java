package org.jboss.arquillian.jbehave.client;

import java.util.List;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jbehave.core.steps.Step;
import org.jboss.arquillian.container.spi.client.protocol.metadata.JMXContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.jbehave.ExtendedState;
import org.jboss.arquillian.jbehave.container.JBehaveProgressNotifier;
import org.jboss.arquillian.jbehave.container.JBehaveProgressNotifierMXBean;
import org.jboss.arquillian.test.spi.event.suite.Test;

public class JBehaveProgressNotifierClient {

	@Inject
	private Instance<ProtocolMetaData> protocolMetadata;

	public void nextStepNotifier(@Observes(precedence = Integer.MAX_VALUE) Test testEvent) {
		Step step = (Step) ExtendedState.getProperty(ExtendedState.CRR_STEP);
		@SuppressWarnings("unchecked")
		List<String> storyPaths = (List<String>) ExtendedState.getProperty(ExtendedState.STORY_PATHS);
		MBeanServerConnection mbeanServer = protocolMetadata.get().getContext(JMXContext.class).getConnection();
		JBehaveProgressNotifierMXBean jBehaveProgressNotifier;
		try {
			jBehaveProgressNotifier = JMX.newMXBeanProxy(mbeanServer, new ObjectName(
					JBehaveProgressNotifier.JBEHAVE_PROGRESS_NOTIFIER_NAME), JBehaveProgressNotifierMXBean.class);
			jBehaveProgressNotifier.setNextStep(step.index(storyPaths));
		} catch (MalformedObjectNameException e) {
			e.printStackTrace();
		}
	}
}
