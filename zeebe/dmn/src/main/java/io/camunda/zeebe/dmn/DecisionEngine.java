/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dmn;

import java.io.InputStream;

/**
 * Parses and evaluates DMN decisions. A DMN resource can be parsed and stored as a {@link
 * ParsedDecisionRequirementsGraph decision requirements graph}, containing 1 or more {@link
 * ParsedDecision decisions} and their requirements.
 *
 * <p>A parsed decision requirements graph can be used to evaluate a decision with a given {@link
 * DecisionContext variable context}. If successful, it leads to a {@link DecisionEvaluationResult
 * decision result}.
 */
public interface DecisionEngine {

  /**
   * Parses the given DMN resource into a parsed decision requirements graph, containing the
   * decision and its requirements.
   *
   * <p>If the DMN is not valid then it returns a result object that contains the failure message,
   * instead of throwing an exception.
   *
   * @param dmnResource the DMN resource as input stream
   * @return the parsed decision requirements graph, or the failure message if the DMN is not valid
   */
  ParsedDecisionRequirementsGraph parse(InputStream dmnResource);

  /**
   * Evaluates a decision in the provided decision requirements graph.
   *
   * <p>If the decision could not be evaluated successfully, then it returns a result object that
   * contains the evaluation failure instead of throwing an exception.
   *
   * @param decisionRequirementsGraph the graph containing the decision to evaluate and all its
   *     requirements
   * @param decisionId the id of the decision to evaluate
   * @param context the evaluation context used to evaluate the decision
   * @return the result of evaluating the decision, or the evaluation failure if the decision could
   *     not be evaluated successfully
   */
  DecisionEvaluationResult evaluateDecisionById(
      ParsedDecisionRequirementsGraph decisionRequirementsGraph,
      String decisionId,
      DecisionContext context);
}
