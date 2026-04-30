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
  private static final String ERROR_MESSAGE_BOTH_KEYS_PROVIDED =
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
    return parseValidExpression(record)
        .flatMap(
            expression ->
                validateScope(record)
                    .map(tenantId -> new ValidatedCommand(expression, record, tenantId)));
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

  /**
   * Identifies the (optional) scope and, if present, validates that the caller's tenant owns it.
   * Returns {@code Optional.empty()} when no scope was supplied — this is a valid state, not an
   * error.
   */
  private Either<Rejection, Optional<String>> validateScope(final ExpressionRecord record) {
    final boolean hasProcessInstanceKey = isSet(record.getProcessInstanceKey());
    final boolean hasElementInstanceKey = isSet(record.getElementInstanceKey());

    // Mutually exclusive — supplying both is a client-side error.
    if (hasProcessInstanceKey && hasElementInstanceKey) {
      return reject(RejectionType.INVALID_ARGUMENT, ERROR_MESSAGE_BOTH_KEYS_PROVIDED);
    }

    // Neither set → evaluation runs without an instance scope.
    if (!hasProcessInstanceKey && !hasElementInstanceKey) {
      return Either.right(Optional.empty());
    }

    final var target =
        hasElementInstanceKey
            ? ScopeTarget.elementInstance(record.getElementInstanceKey())
            : ScopeTarget.processInstance(record.getProcessInstanceKey());

    return validateInstanceTenant(record, target).map(Optional::of);
  }

  private Either<Rejection, String> validateInstanceTenant(
      final ExpressionRecord record, final ScopeTarget target) {
    final var instance = elementInstanceState.getInstance(target.key());

    // Unknown key → 404 rather than leaking details about what exists.
    if (instance == null) {
      return reject(RejectionType.NOT_FOUND, target.notFoundMessage());
    }

    final var providedTenantId = record.getTenantId();
    final var actualTenantId = instance.getValue().getTenantId();

    // Tenant mismatch is also reported as not-found so we don't confirm the instance
    // exists under a different tenant.
    if (providedTenantId != null
        && !providedTenantId.isEmpty()
        && !providedTenantId.equals(actualTenantId)) {
      return reject(RejectionType.NOT_FOUND, target.tenantMismatchMessage(providedTenantId));
    }

    return Either.right(actualTenantId);
  }

  private static boolean isSet(final long key) {
    return key != UNSET_KEY;
  }

  private static <T> Either<Rejection, T> reject(final RejectionType type, final String message) {
    return Either.left(new Rejection(type, message));
  }

  public record ValidatedCommand(
      Expression expression, ExpressionRecord record, Optional<String> resolvedTenantId) {}

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
