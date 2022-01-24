package io.camunda.zeebe.dmn;

/**
 * {@link ParsedDecision Decisions} can only be made within in a specific context. The context must
 * contain all input data required by the decision in the {@link ParsedDecisionRequirementsGraph
 * decision requirements graph} in order to successfully make a decision.
 *
 * @see DecisionEngine
 */
public interface DecisionContext {}
