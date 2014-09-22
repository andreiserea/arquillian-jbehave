package org.jboss.arquillian.jbehave.extensions;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Paths;
import java.util.Set;

import org.apache.log4j.Logger;
import org.jbehave.core.embedder.ScenarioFilter;
import org.jbehave.core.model.Scenario;

public class CoverageScenarioFilter implements ScenarioFilter {

	private final static Logger logger = Logger.getLogger(CoverageScenarioFilter.class);
	private final static String DEFAULT_COVERAGE_DATA_FILE = "junittestplan.bin";
	private Set<String> unmodifiedScenarios = null;
	private boolean isRunningRemote;
	private boolean init = false;
	private String testPlanFile;

	public CoverageScenarioFilter() {
		this(DEFAULT_COVERAGE_DATA_FILE);
	}

	public CoverageScenarioFilter(String testPlanFile) {
		this.testPlanFile = testPlanFile;
	}

	private void init() {
		// read the data file
		try (ObjectInputStream is = new ObjectInputStream(new FileInputStream(testPlanFile))) {
			unmodifiedScenarios = (Set<String>) is.readObject();
		} catch (FileNotFoundException ex) {
			// if file is missing log it and continue
			logger.warn("Test plan file " + testPlanFile + " was not found!");
		} catch (IOException | ClassNotFoundException ex) {
			logger.error("Failed reading the test plan file " + testPlanFile, ex);
		}
		init = true;
	}

	@Override
	public boolean allowed(Scenario scenario) {
		if (!isRunningRemote) {
			if (!init) {
				init();
			}
			String scenarioId = Paths.get(scenario.getStory().getPath()).getFileName() + "_" + scenario.getStory().getScenarios().indexOf(scenario);
			if (unmodifiedScenarios != null && unmodifiedScenarios.contains(scenarioId)) {
				logger.info("Skipping scenario " + scenario.getTitle());
				return false;
			} else {
				logger.info("Executing scenario " + scenario.getTitle());
			}
		}
		// if no test plan exists (first run) or running remote allow all scenarios to run
		// allow to run in remote to avoid calculating the svn differences each time a step is ran in remote
		return true;
	}

	public void setRunningRemote(boolean isRunningRemote) {
		this.isRunningRemote = isRunningRemote;
	}
}
