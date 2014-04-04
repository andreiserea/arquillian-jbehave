package org.jboss.arquillian.junit;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.jbehave.core.ConfigurableEmbedder;
import org.jbehave.core.embedder.ScenarioFilter;
import org.jbehave.core.model.Scenario;
import org.jbehave.core.model.Story;
import org.jbehave.core.reporters.NullStoryReporter;
import org.jboss.arquillian.jbehave.extensions.CoverageScenarioFilter;
import org.jboss.resteasy.client.ClientRequest;
import org.junit.internal.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;

/**
 * An extended Arquillian Jbehave runner that also flushes the cobertura coverage data
 * 
 * @author Andrei Serea
 * 
 */
public class ArquillianJbehaveCoverageRunner extends ArquillianJbehaveRunner {

	private final static String INCREMENTAL_FLUSH_PROPERTY = "cobertura.flush.incremental";
	private final static String CONTAINER_IP;
	private final static String CONTAINER_PORT;
	private final static String COBERTURA_SERVLET_URL_NONINC = "http://%s:%s/coberturaMC/flushCobertura";
	private final static String COBERTURA_SERVLET_URL_INC = "http://%s:%s/coberturaMC/incFlushCobertura";
	private final static String servletFlushURLInc;
	private final static String servletFlushURLNonInc;
	private final static boolean incremental;

	static {
		CONTAINER_IP = System.getProperty("arquillian.container.address");
		final String jbossOffsetAsString = System.getProperty("jboss.port.offset");
		int jbossOffset = 0;
		if (jbossOffsetAsString != null) {
			try {
				jbossOffset = Integer.parseInt(jbossOffsetAsString);
			} catch (final NumberFormatException ex) {
				System.out.printf("Incorrect jboss offset %s - ignoring!\n", jbossOffsetAsString);
			}
		}
		CONTAINER_PORT = 8080 + jbossOffset + "";
		incremental = Boolean.parseBoolean(System.getProperty(INCREMENTAL_FLUSH_PROPERTY));
		servletFlushURLInc = String.format(COBERTURA_SERVLET_URL_INC, CONTAINER_IP, CONTAINER_PORT);
		servletFlushURLNonInc = String.format(COBERTURA_SERVLET_URL_NONINC, CONTAINER_IP, CONTAINER_PORT);
	}

	public ArquillianJbehaveCoverageRunner(Class<? extends ConfigurableEmbedder> testClass) throws Throwable {
		super(testClass);
		// notify the scenario filter (if any) about running remote
		ScenarioFilter scenarioFilter = this.configuration.scenarioFilter();
		if (scenarioFilter instanceof CoverageScenarioFilter) {
			((CoverageScenarioFilter) scenarioFilter).setRunningRemote(isRunningRemote);
		}
		// set up the reporter that tracks when a scenario finishes to flush cobertura
		this.configureStoryReporterFormats(new IncCoverageFlushStoryReporter());
	}

	@Override
	protected Statement withAfterClasses(final Statement originalStatement) {
		if (!isRunningRemote && !incremental) {
			// only in junit jvm, before uninstalling the app in the container, flush the cobertura
			// when incremental the cobertura file is flushed after each scenario
			return super.withAfterClasses(new Statement() {
				@Override
				public void evaluate() throws Throwable {
					List<Throwable> exceptions = new ArrayList<Throwable>();
					try {
						originalStatement.evaluate();
					} catch (Throwable e) {
						exceptions.add(e);
					}
					// call cobertura while app is still loaded to flush coverage data
					ClientRequest request = new ClientRequest(servletFlushURLNonInc);
					try {
						request.post();
					} catch (Exception e) {
						exceptions.add(e);
					}
					if (exceptions.isEmpty()) {
						return;
					}
					if (exceptions.size() == 1) {
						throw exceptions.get(0);
					}
					throw new MultipleFailureException(exceptions);
				}
			});
		} else {
			return super.withAfterClasses(originalStatement);
		}
	}

	private class IncCoverageFlushStoryReporter extends NullStoryReporter {

		private String crrScenario;
		private Story crrStory;
		private int crrScenarioIndex;

		@Override
		public void beforeStory(Story story, boolean givenStory) {
			this.crrStory = story;
		}

		@Override
		public void beforeScenario(String title) {
			List<Scenario> scenarios = this.crrStory.getScenarios();
			int index = -1;
			for (int i = 0; i < scenarios.size(); i++) {
				if (scenarios.get(i).getTitle().equals(title)) {
					index = i;
					break;
				}
			}
			this.crrScenario = title;
			this.crrScenarioIndex = index;
		}

		@Override
		public void afterScenario() {
			// call the cobertura servlet to flush after each scenario if incremental
			if (!isRunningRemote && incremental) {
				//construct the name of the test file from the story file name and the index of the scenario in the story
				ClientRequest request = new ClientRequest(servletFlushURLInc).queryParameter("test",
						Paths.get(this.crrStory.getPath()).getFileName() + "_" + this.crrScenarioIndex);
				try {
					request.post();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			this.crrScenario = null;
			this.crrScenarioIndex = -1;
		}
	}
}