/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.arquillian.jbehave.client;

import org.jboss.arquillian.container.test.spi.client.deployment.AuxiliaryArchiveAppender;
import org.jboss.arquillian.core.spi.LoadableExtension;
import org.jboss.arquillian.jbehave.injection.StepEnricherProvider;


/**
 * A version of the LoadableExtension with JBehave loaded from Maven pom file (for cases when JBehave is
 * set up as a global module in jboss, to speed up deployment time)
 * 
 * @author Andrei Serea
 * 
 */
public class ShrinkedJBehaveExtension implements LoadableExtension {

	@Override
	public void register(ExtensionBuilder builder) {
		builder.service(AuxiliaryArchiveAppender.class, JBehaveCoreMavenDeploymentAppender.class)
				.service(AuxiliaryArchiveAppender.class, ArquillianJBehaveRunnerDeploymentAppender.class)
				.observer(StepEnricherProvider.class);
	}

}
