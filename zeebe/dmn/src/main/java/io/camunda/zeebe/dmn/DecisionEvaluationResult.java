/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dmn;

import java.util.List;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.Nullable;

/**
 * The result of a {@link ParsedDecision}. If successful it contains the output of the decision that
 * was made, otherwise it contains the evaluation failure.
 *
 * @see DecisionEngine
 */
public interface DecisionEvaluationResult {

  /**
   * @return {@code true} if the evaluation was not successful, otherwise {@code false}
   */
  boolean isFailure();

  /**
   * Returns the reason why the evaluation failed. Use {@link #isFailure()} to check if the
   * evaluation was successful or not.
   *
   * @return the failure message if the evaluation was not successful, or {@code null} if the
   *     evaluation was successful
   */
  @Nullable String getFailureMessage();

  /**
   * Returns the id of the decision where the evaluation failed. Use {@link #isFailure()} to check
   * if the evaluation was successful or not.
   *
   * @return the id of the decision where the evaluation failed, or {@code null} if the evaluation
   *     was successful
   */
  @Nullable String getFailedDecisionId();

  /**
   * @return the output of the decision if it was made successfully, otherwise {@code null}
   */
  @Nullable DirectBuffer getOutput();

  /**
   * Returns the {@link EvaluatedDecision details} of the evaluated decision and its required
   * decisions. The order depends on the evaluation order, starting from the required decisions. If
   * the evaluation is not successful then it contains the successful evaluated decisions and the
   * decision that was not successful.
   *
   * @return details of the evaluated decisions
   */
  List<EvaluatedDecision> getEvaluatedDecisions();
}
