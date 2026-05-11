/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.el.EvaluationContext;
import io.camunda.zeebe.engine.processing.secret.SecretStore;
import io.camunda.zeebe.util.Either;
import java.util.Objects;
import org.agrona.DirectBuffer;

/**
 * Resolving variant of the {@code camunda.secret.*} FEEL namespace. Unlike {@link
 * SecretEvaluationContext} — which is wired engine-wide and always returns the literal reference
 * string — this context consults the configured {@link SecretStore} and emits the underlying secret
 * value as a MessagePack string. It is intended to be used <em>only</em> on paths where
 * materialising the secret is acceptable: the standalone FEEL evaluation endpoint reaches it
 * through {@link ExpressionBehavior}, and the job-activation path (next stage) will reach it via a
 * per-request lookup.
 *
 * <p>If the requested secret is absent, {@code getVariable} returns {@code Left(null)} — the
 * FEEL-idiomatic representation of an undefined path, which the FEEL engine surfaces as a warning
 * on the resulting record.
 */
public final class ResolvingSecretEvaluationContext implements ScopedEvaluationContext {

  private final SecretStore secretStore;

  public ResolvingSecretEvaluationContext(final SecretStore secretStore) {
    this.secretStore = Objects.requireNonNull(secretStore);
  }

  @Override
  public Either<DirectBuffer, EvaluationContext> getVariable(final String variableName) {
    if (variableName == null || variableName.isEmpty()) {
      return Either.left(null);
    }
    return secretStore
        .resolve(variableName)
        .map(SecretEvaluationContext::encodeAsMsgPackString)
        .<Either<DirectBuffer, EvaluationContext>>map(Either::left)
        .orElse(Either.left(null));
  }
}
