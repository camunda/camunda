/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import static io.camunda.zeebe.util.EnsureUtil.ensureNotNull;
import static io.camunda.zeebe.util.EnsureUtil.ensureNotNullOrEmpty;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableEndEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSignal;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;

public final class BpmnSignalBehavior {

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final ExpressionProcessor expressionBehavior;

  public BpmnSignalBehavior(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final ExpressionProcessor expressionBehavior) {
    this.keyGenerator = keyGenerator;
    this.expressionBehavior = expressionBehavior;
    stateWriter = writers.state();
  }

  public Either<Failure, ?> broadcastNewSignal(
      final BpmnElementContext context, final ExecutableEndEvent element) {
    final var signal = element.getSignal();
    final var scopeKey = context.getElementInstanceKey();
    return evaluateSignalName(signal, context)
        .map(
            signalName -> {
              writeSignalBroadcastEvent(signalName);
              return null;
            });
  }

  private Either<Failure, String> evaluateSignalName(
      final ExecutableSignal signal, final BpmnElementContext context) {
    ensureNotNull("signal", signal);

    if (signal.getSignalName().isPresent()) {
      return Either.right(signal.getSignalName().get());
    }

    return expressionBehavior.evaluateStringExpression(
        signal.getSignalNameExpression(), context.getElementInstanceKey());
  }

  private void writeSignalBroadcastEvent(final String signalName) {

    ensureNotNullOrEmpty("signalName", signalName);

    final var record = new SignalRecord();
    record.setSignalName(signalName);

    final var key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(key, SignalIntent.BROADCAST, record);
  }
}
