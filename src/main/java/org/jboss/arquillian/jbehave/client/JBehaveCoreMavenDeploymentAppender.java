package org.jboss.arquillian.jbehave.client;

import java.util.Collection;

import org.jboss.arquillian.container.test.spi.RemoteLoadableExtension;
import org.jboss.arquillian.container.test.spi.client.deployment.AuxiliaryArchiveAppender;
import org.jboss.arquillian.jbehave.container.JBehaveContainerExtension;
import org.jboss.arquillian.jbehave.injection.ArquillianInstanceStepsFactory;
import org.jboss.arquillian.jbehave.injection.StepEnricherProvider;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.resolver.api.DependencyResolvers;
import org.jboss.shrinkwrap.resolver.api.maven.MavenDependencyResolver;

public class JBehaveCoreMavenDeploymentAppender implements AuxiliaryArchiveAppender {

	@Override
	public Archive<?> createAuxiliaryArchive() {
		Collection<JavaArchive> archives = DependencyResolvers
				.use(MavenDependencyResolver.class).goOffline()
				.loadMetadataFromPom("pom.xml")
				.artifacts("org.jbehave:jbehave-core:jar:3.8-SNAPSHOT")
				.resolveAs(JavaArchive.class);
		JavaArchive archive = ShrinkWrap.create(JavaArchive.class,
				"arquillian-jbehave.jar");
		for (Archive<JavaArchive> element : archives) {
			archive.merge(element);
		}

		archive.addClasses(JBehaveContainerExtension.class,
				ArquillianInstanceStepsFactory.class,
				StepEnricherProvider.class);
		archive.addAsServiceProvider(RemoteLoadableExtension.class,
				JBehaveContainerExtension.class);
		return archive;
	}

}
