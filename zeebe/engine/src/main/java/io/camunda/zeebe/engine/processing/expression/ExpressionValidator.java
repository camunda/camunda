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

  private static final String ERROR_MESSAGE_EMPTY_EXPRESSION = "No expression provided";
  private static final String ERROR_MESSAGE_BLANK_EXPRESSION =
      "The expression must not be blank or empty";
  private static final String ERROR_MESSAGE_EITHER_OR =
      "Either 'processInstanceKey' or 'elementInstanceKey' must be provided, not both";
  private static final String ERROR_MESSAGE_PROCESS_INSTANCE_NOT_FOUND =
      "No process instance found with key '%d'";
  private static final String ERROR_MESSAGE_ELEMENT_INSTANCE_NOT_FOUND =
      "No element instance found with key '%d'";
  private static final String ERROR_MESSAGE_TENANT_MISMATCH_PROCESS_INSTANCE =
      "No process instance found with key '%d' for tenant '%s'";
  private static final String ERROR_MESSAGE_TENANT_MISMATCH_ELEMENT_INSTANCE =
      "No element instance found with key '%d' for tenant '%s'";

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

    return validateExpressionText(record)
        .flatMap(this::parseExpression)
        .flatMap(
            expression ->
                resolveScopedContext(record)
                    .map(resolvedRecord -> new ValidatedCommand(expression, resolvedRecord)));
  }

  private Either<Rejection, String> validateExpressionText(final ExpressionRecord record) {
    final var expression = record.getExpression();

    // No expression field at all → caller omitted it from the request body.
    if (expression == null) {
      return reject(RejectionType.INVALID_ARGUMENT, ERROR_MESSAGE_EMPTY_EXPRESSION);
    }

    // Present but whitespace-only → reject separately to give a clearer error.
    if (expression.isBlank()) {
      return reject(RejectionType.INVALID_ARGUMENT, ERROR_MESSAGE_BLANK_EXPRESSION);
    }

    return Either.right(expression);
  }

  private Either<Rejection, Expression> parseExpression(final String expressionText) {
    final var expression = expressionLanguage.parseExpression(expressionText);

    // FEEL parser rejected the input (syntax error, unknown function, etc.).
    if (!expression.isValid()) {
      return reject(
          RejectionType.INVALID_ARGUMENT,
          "Failed to parse expression: " + expression.getFailureMessage());
    }

    return Either.right(expression);
  }

  private Either<Rejection, ExpressionRecord> resolveScopedContext(final ExpressionRecord record) {
    return resolveScopeTarget(record)
        .flatMap(
            target ->
                target
                    // A scope was provided → look it up and validate tenant ownership.
                    .map(scopedTarget -> resolveInstance(record, scopedTarget))
                    // No scope provided → record passes through unchanged.
                    .orElseGet(() -> Either.right(record)));
  }

  private Either<Rejection, Optional<ScopeTarget>> resolveScopeTarget(
      final ExpressionRecord record) {
    final boolean hasProcessInstanceKey = record.getProcessInstanceKey() >= 0;
    final boolean hasElementInstanceKey = record.getElementInstanceKey() >= 0;

    // Both keys are mutually exclusive — supplying both is a client-side error.
    if (hasProcessInstanceKey && hasElementInstanceKey) {
      return reject(RejectionType.INVALID_ARGUMENT, ERROR_MESSAGE_EITHER_OR);
    }

    // Neither key set → evaluation runs without an instance scope (cluster-only variables).
    if (!hasProcessInstanceKey && !hasElementInstanceKey) {
      return Either.right(Optional.empty());
    }

    // Exactly one key is set → classify it into the matching scope target.
    return Either.right(
        Optional.of(
            hasElementInstanceKey
                ? ScopeTarget.elementInstance(record.getElementInstanceKey())
                : ScopeTarget.processInstance(record.getProcessInstanceKey())));
  }

  private Either<Rejection, ExpressionRecord> resolveInstance(
      final ExpressionRecord record, final ScopeTarget target) {
    final var instance = elementInstanceState.getInstance(target.key());

    // Unknown key → 404 rather than leaking details about what exists.
    if (instance == null) {
      return reject(RejectionType.NOT_FOUND, target.notFoundMessage());
    }

    final var providedTenantId = record.getTenantId();
    final var actualTenantId = instance.getValue().getTenantId();

    // Caller supplied a tenant that doesn't own the instance → treat as not-found
    // to avoid confirming the instance exists under a different tenant.
    if (providedTenantId != null
        && !providedTenantId.isEmpty()
        && !providedTenantId.equals(actualTenantId)) {
      return reject(RejectionType.NOT_FOUND, target.tenantMismatchMessage(providedTenantId));
    }

    // Infer the tenant from the instance so downstream auth / variable resolution
    // always operate against the instance's owning tenant.
    record.setTenantId(actualTenantId);
    return Either.right(record);
  }

  private static <T> Either<Rejection, T> reject(final RejectionType type, final String message) {
    return Either.left(new Rejection(type, message));
  }

  public record ValidatedCommand(Expression expression, ExpressionRecord record) {}

  private record ProcessInstanceScopeTarget(long key) implements ScopeTarget {
    @Override
    public String notFoundMessage() {
      return ERROR_MESSAGE_PROCESS_INSTANCE_NOT_FOUND.formatted(key);
    }

    @Override
    public String tenantMismatchMessage(final String tenantId) {
      return ERROR_MESSAGE_TENANT_MISMATCH_PROCESS_INSTANCE.formatted(key, tenantId);
    }
  }

  private record ElementInstanceScopeTarget(long key) implements ScopeTarget {
    @Override
    public String notFoundMessage() {
      return ERROR_MESSAGE_ELEMENT_INSTANCE_NOT_FOUND.formatted(key);
    }

    @Override
    public String tenantMismatchMessage(final String tenantId) {
      return ERROR_MESSAGE_TENANT_MISMATCH_ELEMENT_INSTANCE.formatted(key, tenantId);
    }
  }

  private sealed interface ScopeTarget
      permits ProcessInstanceScopeTarget, ElementInstanceScopeTarget {
    long key();

    String notFoundMessage();

    String tenantMismatchMessage(String tenantId);

    static ScopeTarget processInstance(final long key) {
      return new ProcessInstanceScopeTarget(key);
    }

    static ScopeTarget elementInstance(final long key) {
      return new ElementInstanceScopeTarget(key);
    }
  }
}
