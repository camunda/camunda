/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el.impl;

import io.camunda.zeebe.el.EvaluationWarning;
import java.util.ArrayList;
import java.util.List;

public class FeelEvaluationWarning implements EvaluationWarning {

  private final String type;
  private final String message;

  public FeelEvaluationWarning(final String type, final String message) {
    this.type = type;
    this.message = message;
  }

  public static List<EvaluationWarning> fromResult(
      final org.camunda.feel.api.EvaluationResult evaluationResult) {
    final var warnings = new ArrayList<EvaluationWarning>();
    evaluationResult
        .suppressedFailures()
        .foreach(
            suppressedFailure -> {
              final var warning =
                  new FeelEvaluationWarning(
                      suppressedFailure.failureType().toString(),
                      suppressedFailure.failureMessage());
              return warnings.add(warning);
            });
    return warnings;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public String getMessage() {
    return message;
  }
}
