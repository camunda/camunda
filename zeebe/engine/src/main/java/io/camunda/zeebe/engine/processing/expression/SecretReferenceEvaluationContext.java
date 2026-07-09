/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.el.EvaluationContext;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

/**
 * A {@link ScopedEvaluationContext} that resolves a {@code camunda.secrets.<name>} reference used
 * as an expression to its own reference text (so {@code camunda.secrets.token} evaluates to {@code
 * "camunda.secrets.token"}) instead of a {@code null} lookup. Installed only for input-mapping
 * evaluation; the real secret value is substituted later, at job activation.
 *
 * <p>It wraps the delegate and intercepts only the {@code camunda.secrets.*} path — process
 * variables, cluster variables ({@code camunda.vars.*}) and every other lookup are forwarded
 * unchanged, so nothing is shadowed. The transformation is pure, hence replication/replay-safe.
 */
@NullMarked
public final class SecretReferenceEvaluationContext implements ScopedEvaluationContext {

  static final String ROOT = "camunda";
  static final String NAMESPACE = "secrets";

  private final ScopedEvaluationContext delegate;

  public SecretReferenceEvaluationContext(final ScopedEvaluationContext delegate) {
    this.delegate = delegate;
  }

  @Override
  public Either<DirectBuffer, EvaluationContext> getVariable(final String variableName) {
    if (!ROOT.equals(variableName)) {
      return delegate.getVariable(variableName);
    }
    final var camunda = delegate.getVariable(ROOT);
    if (camunda.isLeft() && camunda.getLeft() != null) {
      // a real `camunda` variable value (e.g. a process variable) exists; keep it intact rather
      // than shadowing it, so its members stay reachable and existing semantics are preserved
      return camunda;
    }
    // wrap the delegate's `camunda` context (or absence) so `secrets` resolves while every other
    // key — e.g. cluster variables under `vars` — keeps forwarding to the delegate
    return Either.right(new CamundaNamespaceContext(camunda));
  }

  @Override
  public ScopedEvaluationContext processScoped(final long scopeKey) {
    return new SecretReferenceEvaluationContext(delegate.processScoped(scopeKey));
  }

  @Override
  public ScopedEvaluationContext tenantScoped(final String tenantId) {
    return new SecretReferenceEvaluationContext(delegate.tenantScoped(tenantId));
  }

  /**
   * The {@code camunda} namespace: resolves {@code secrets} to the secret-reference leaf and
   * forwards every other key to the delegate's {@code camunda} content (e.g. {@code vars}).
   */
  private record CamundaNamespaceContext(Either<DirectBuffer, EvaluationContext> delegateCamunda)
      implements EvaluationContext {

    @Override
    public Either<DirectBuffer, EvaluationContext> getVariable(final String variableName) {
      if (NAMESPACE.equals(variableName)) {
        return Either.right(SecretLeafContext.INSTANCE);
      }
      final var camunda = delegateCamunda.isRight() ? delegateCamunda.get() : null;
      return camunda != null ? camunda.getVariable(variableName) : Either.left(null);
    }
  }

  /**
   * The {@code camunda.secrets} namespace: every name resolves to its own reference string literal
   * {@code "camunda.secrets.<name>"}.
   */
  private static final class SecretLeafContext implements EvaluationContext {

    private static final SecretLeafContext INSTANCE = new SecretLeafContext();

    @Override
    public Either<DirectBuffer, EvaluationContext> getVariable(final String name) {
      final String reference = ROOT + "." + NAMESPACE + "." + name;
      // cast to Object to serialize the string as a MessagePack string value, not parse it as JSON
      return Either.left(
          BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack((Object) reference)));
    }
  }
}
