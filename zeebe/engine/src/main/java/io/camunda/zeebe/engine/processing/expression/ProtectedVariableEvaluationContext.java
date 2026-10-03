/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.el.EvaluationContext;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.agrona.DirectBuffer;

/**
 * Hides process instance variables whose name matches a sensitive-variable pattern from the
 * expression endpoint, so neither its result nor its warnings can carry their values: FEEL sees
 * such a variable as absent and evaluates the rest of the expression as usual.
 *
 * <p>Every variable it hides is added to {@code hiddenVariables}, which the caller owns and resets
 * per evaluation, so the response can say which variables were withheld. A name is only recorded
 * when the variable exists, so the response never claims to hide a variable that isn't there.
 */
final class ProtectedVariableEvaluationContext implements ScopedEvaluationContext {

  private final ScopedEvaluationContext delegate;
  private final List<Pattern> sensitiveVariablePatterns;
  private final Set<String> hiddenVariables;

  ProtectedVariableEvaluationContext(
      final ScopedEvaluationContext delegate,
      final List<Pattern> sensitiveVariablePatterns,
      final Set<String> hiddenVariables) {
    this.delegate = delegate;
    this.sensitiveVariablePatterns = sensitiveVariablePatterns;
    this.hiddenVariables = hiddenVariables;
  }

  @Override
  public ScopedEvaluationContext processScoped(final long scopeKey) {
    return new ProtectedVariableEvaluationContext(
        delegate.processScoped(scopeKey), sensitiveVariablePatterns, hiddenVariables);
  }

  @Override
  public ScopedEvaluationContext tenantScoped(final String tenantId) {
    return new ProtectedVariableEvaluationContext(
        delegate.tenantScoped(tenantId), sensitiveVariablePatterns, hiddenVariables);
  }

  @Override
  public Either<DirectBuffer, EvaluationContext> getVariable(final String variableName) {
    final var variable = delegate.getVariable(variableName);
    if (isPresent(variable) && isSensitive(variableName)) {
      hiddenVariables.add(variableName);
      return Either.left(null);
    }
    return variable;
  }

  private boolean isSensitive(final String variableName) {
    return sensitiveVariablePatterns.stream()
        .anyMatch(pattern -> pattern.matcher(variableName).matches());
  }

  private static boolean isPresent(final Either<DirectBuffer, EvaluationContext> variable) {
    return variable.isRight() || variable.getLeft() != null;
  }
}
