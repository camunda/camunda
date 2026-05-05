/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.expression.ExpressionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.Optional;

public final class ExpressionValidator {

  private static final long UNSET_KEY = -1L;

  private static final String ERROR_MESSAGE_EMPTY_EXPRESSION = "No expression provided";
  private static final String ERROR_MESSAGE_BLANK_EXPRESSION =
      "The expression must not be blank or empty";
  private static final String ERROR_MESSAGE_SCOPE_NOT_FOUND = "No scope found with key '%d'";
  private static final String ERROR_MESSAGE_TENANT_MISMATCH_SCOPE =
      "No scope found with key '%d' for tenant '%s'";

  private final ExpressionLanguage expressionLanguage;
  private final ElementInstanceState elementInstanceState;

  public ExpressionValidator(
      final ExpressionLanguage expressionLanguage,
      final ElementInstanceState elementInstanceState) {
    this.expressionLanguage = expressionLanguage;
    this.elementInstanceState = elementInstanceState;
  }

  public Either<Rejection, ValidatedCommand> validate(final TypedRecord<ExpressionRecord> command) {
    final var record = command.getValue();
    return parseValidExpression(record)
        .flatMap(
            expr -> resolveScope(record).map(scope -> new ValidatedCommand(expr, record, scope)));
  }

  private Either<Rejection, Optional<ResolvedInstance>> resolveScope(
      final ExpressionRecord record) {
    final long scopeKey = record.getScopeKey();
    return isSet(scopeKey)
        ? validateInstance(record, scopeKey).map(Optional::of)
        : Either.right(Optional.empty());
  }

  private Either<Rejection, ResolvedInstance> validateInstance(
      final ExpressionRecord record, final long scopeKey) {

    final var instance = elementInstanceState.getInstance(scopeKey);
    if (instance == null) {
      return reject(RejectionType.NOT_FOUND, ERROR_MESSAGE_SCOPE_NOT_FOUND.formatted(scopeKey));
    }

    final var value = instance.getValue();
    if (tenantMismatch(record.getTenantId(), value.getTenantId())) {
      return reject(
          RejectionType.NOT_FOUND,
          ERROR_MESSAGE_TENANT_MISMATCH_SCOPE.formatted(scopeKey, record.getTenantId()));
    }

    return Either.right(new ResolvedInstance(value.getTenantId(), value.getBpmnProcessId()));
  }

  private static boolean tenantMismatch(final String provided, final String actual) {
    return provided != null && !provided.isEmpty() && !provided.equals(actual);
  }

  /** Combines text-level validation and FEEL parsing — they always travel together. */
  private Either<Rejection, Expression> parseValidExpression(final ExpressionRecord record) {
    final var text = record.getExpression();

    if (text == null) {
      return reject(RejectionType.INVALID_ARGUMENT, ERROR_MESSAGE_EMPTY_EXPRESSION);
    }
    if (text.isBlank()) {
      return reject(RejectionType.INVALID_ARGUMENT, ERROR_MESSAGE_BLANK_EXPRESSION);
    }

    final var expression = expressionLanguage.parseExpression(text);
    if (!expression.isValid()) {
      return reject(
          RejectionType.INVALID_ARGUMENT,
          "Failed to parse expression: " + expression.getFailureMessage());
    }
    return Either.right(expression);
  }

  private static boolean isSet(final long key) {
    return key != UNSET_KEY;
  }

  private static <T> Either<Rejection, T> reject(final RejectionType type, final String message) {
    return Either.left(new Rejection(type, message));
  }

  public record ValidatedCommand(
      Expression expression,
      ExpressionRecord record,
      Optional<ResolvedInstance> resolvedInstance) {}

  /**
   * Metadata derived from the resolved scope instance — captured here so the processor can reuse it
   * (tenant inference, authorization checks) without a second state lookup.
   */
  public record ResolvedInstance(String tenantId, String bpmnProcessId) {}
}
