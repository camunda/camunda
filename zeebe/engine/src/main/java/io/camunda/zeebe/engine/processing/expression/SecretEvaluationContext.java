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
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.util.Either;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Engine-wide {@link ScopedEvaluationContext} for the {@code camunda.secret.*} FEEL namespace.
 *
 * <p>Resolution is deliberately inert: any path lookup {@code camunda.secret.X} yields the
 * <em>literal string</em> {@code "camunda.secret.X"}, never the underlying secret value. This is
 * the layer that keeps real secrets out of the variable store, the logstream, and the exporter
 * pipeline by default. The two paths that need real values — job activation with an explicit {@code
 * camunda.secret.X} entry in {@code fetchVariables}, and the standalone FEEL evaluation endpoint —
 * override this context with a resolving variant via {@link ExpressionBehavior} (next stage of the
 * PoC).
 *
 * <p>Returned buffers carry a freshly MessagePack-encoded string per lookup; per-instance use is
 * single-threaded (one engine partition processor), so no synchronisation or pooling is needed.
 *
 * @see SecretStore#FEEL_NAMESPACE
 */
public final class SecretEvaluationContext implements ScopedEvaluationContext {

  @Override
  public Either<DirectBuffer, EvaluationContext> getVariable(final String variableName) {
    if (variableName == null || variableName.isEmpty()) {
      return Either.left(null);
    }
    return Either.left(encodeReferenceString(SecretStore.FEEL_NAMESPACE + variableName));
  }

  private static DirectBuffer encodeReferenceString(final String value) {
    final MsgPackWriter writer = new MsgPackWriter();
    final MutableDirectBuffer writeBuffer = new ExpandableArrayBuffer();
    writer.wrap(writeBuffer, 0);

    final DirectBuffer stringWrapper = new UnsafeBuffer();
    stringWrapper.wrap(value.getBytes());
    writer.writeString(stringWrapper);

    final DirectBuffer resultView = new UnsafeBuffer();
    resultView.wrap(writeBuffer, 0, writer.getOffset());
    return resultView;
  }
}
