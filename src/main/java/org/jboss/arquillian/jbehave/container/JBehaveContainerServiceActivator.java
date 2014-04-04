package org.jboss.arquillian.jbehave.container;

import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceRegistryException;
import org.jboss.msc.service.ServiceTarget;

public class JBehaveContainerServiceActivator implements ServiceActivator {

	@Override
	public void activate(ServiceActivatorContext serviceActivatorContext) throws ServiceRegistryException {
		ServiceTarget serviceTarget = serviceActivatorContext.getServiceTarget();
		JBehaveContainerService.addService(serviceTarget);
	}

}
