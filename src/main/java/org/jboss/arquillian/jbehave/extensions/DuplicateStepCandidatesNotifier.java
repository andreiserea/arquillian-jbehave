package org.jboss.arquillian.jbehave.extensions;

import java.util.ArrayList;
import java.util.List;

import org.jbehave.core.steps.StepCandidate;
import org.jbehave.core.steps.StepFinder.PrioritisingStrategy;

/**
 * Logs an error if more then 1 step candidate is found for the same step
 * @author Andrei Serea
 *
 */
public class DuplicateStepCandidatesNotifier implements PrioritisingStrategy {

	@Override
	public List<StepCandidate> prioritise(String stepAsString, List<StepCandidate> candidates) {
		// when there is more than 1 candidate, return an empty list and log the
		// problem
		List<StepCandidate> matches = new ArrayList<>();
		for (StepCandidate candidate : candidates) {
			if (candidate.matches(stepAsString)) {
				matches.add(candidate);
			}
		}
		if (matches.size() > 1) {
			System.err.println("More than one step candidate for step: " + stepAsString);
			for (StepCandidate match : matches) {
				System.err.println(match.getMethod());
			}
			return null;
		}
		// if no match is found return the original list (the match function is
		// broken because of that additional parameter which we're missing -
		// previousNonAndStep)
		if (matches.size() == 0) {
			return candidates;
		}
		return matches;
	}

}
