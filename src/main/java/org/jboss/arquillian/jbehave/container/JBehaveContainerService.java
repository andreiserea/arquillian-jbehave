package org.jboss.arquillian.jbehave.container;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.jboss.as.jmx.MBeanServerService;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

public class JBehaveContainerService implements Service<JBehaveContainerService> {

	public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("jbehave", "testrunner");
	private final InjectedValue<MBeanServer> injectedMBeanServer = new InjectedValue<MBeanServer>();

	public static void addService(final ServiceTarget serviceTarget) {
		JBehaveContainerService service = new JBehaveContainerService();
		ServiceBuilder<?> serviceBuilder = serviceTarget.addService(JBehaveContainerService.SERVICE_NAME, service);
		serviceBuilder.addDependency(MBeanServerService.SERVICE_NAME, MBeanServer.class, service.injectedMBeanServer);
		serviceBuilder.install();
	}

	@Override
	public JBehaveContainerService getValue() throws IllegalStateException, IllegalArgumentException {
		return this;
	}

	@Override
	public void start(StartContext context) throws StartException {
		try {
			final ObjectName name = new ObjectName(JBehaveProgressNotifier.JBEHAVE_PROGRESS_NOTIFIER_NAME);
			final MBeanServer mbs = injectedMBeanServer.getValue();
			mbs.registerMBean(new JBehaveProgressNotifier(), name);
		} catch (Throwable t) {
			throw new StartException("Failed to start JBehave Test Runner", t);
		}
		// MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		// try {
		// final ObjectName name = new ObjectName(JBehaveProgressNotifier.JBEHAVE_PROGRESS_NOTIFIER_NAME);
		// mbs.registerMBean(new JBehaveProgressNotifier(), name);
		// } catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException
		// | MalformedObjectNameException e) {
		// e.printStackTrace();
		// }
	}

	@Override
	public void stop(StopContext context) {
		try {
			final ObjectName name = new ObjectName(JBehaveProgressNotifier.JBEHAVE_PROGRESS_NOTIFIER_NAME);
			final MBeanServer mbs = injectedMBeanServer.getValue();
			if (mbs.isRegistered(name)) {
				mbs.unregisterMBean(name);
			}
		} catch (Throwable t) {
			throw new RuntimeException("Failed to start JBehave Test Runner", t);
		}
	}

}
