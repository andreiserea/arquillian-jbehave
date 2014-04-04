package org.jboss.arquillian.jbehave.client;

import org.jboss.arquillian.container.test.spi.RemoteLoadableExtension;
import org.jboss.arquillian.container.test.spi.client.deployment.AuxiliaryArchiveAppender;
import org.jboss.arquillian.jbehave.ExtendedState;
import org.jboss.arquillian.jbehave.container.JBehaveContainerExtension;
import org.jboss.arquillian.jbehave.container.JBehaveContainerServiceActivator;
import org.jboss.arquillian.jbehave.injection.ArquillianInstanceStepsFactory;
import org.jboss.arquillian.jbehave.injection.StepEnricherProvider;
import org.jboss.arquillian.junit.ArquillianJbehaveRunner;
import org.jboss.arquillian.utils.ArquillianUtils;
import org.jboss.as.arquillian.service.ArquillianServiceActivator;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;


/**
 * Deployment appender that adds the JBehave Arquillian Runner, step instance enricher and other small extensions
 * @author Andrei Serea
 *
 */
public class ArquillianJBehaveRunnerDeploymentAppender implements AuxiliaryArchiveAppender {

	@Override
	public Archive<?> createAuxiliaryArchive() {
		JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "arquillian-jbehave-runner.jar");
		archive.addPackage("org.jboss.arquillian.jbehave.javassist")
				.addPackages(true, "org.jboss.arquillian.jbehave.extensions")
				.addPackage("de.codecentric.jbehave.junit.monitoring")
				.addPackage("org.jboss.arquillian.jbehave.container")
				.addClass(ArquillianJbehaveRunner.class)
				.addClass(ArquillianUtils.class)
				.addClass(ArquillianInstanceStepsFactory.class)
				.addClass(StepEnricherProvider.class)
				.addClass(ExtendedState.class);
		archive.addAsServiceProvider(RemoteLoadableExtension.class, JBehaveContainerExtension.class);
		//TODO: fix this - do not add ArquillianService, make below line append instead of replace
		archive.addAsServiceProvider(ServiceActivator.class, ArquillianServiceActivator.class, JBehaveContainerServiceActivator.class);
		return archive;
	}

}
