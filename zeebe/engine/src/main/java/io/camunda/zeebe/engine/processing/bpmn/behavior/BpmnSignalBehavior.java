/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import static io.camunda.zeebe.util.EnsureUtil.ensureNotNull;
import static io.camunda.zeebe.util.EnsureUtil.ensureNotNullOrEmpty;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSignal;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.TagUtil;
import org.agrona.DirectBuffer;

public final class BpmnSignalBehavior {

  private final SignalRecord signalRecord =
      new SignalRecord().setVariables(DocumentValue.EMPTY_DOCUMENT);
  private final KeyGenerator keyGenerator;
  private final VariableState variableState;
  private final TypedCommandWriter commandWriter;
  private final ExpressionProcessor expressionBehavior;
  private final BpmnStateBehavior stateBehavior;

  public BpmnSignalBehavior(
      final KeyGenerator keyGenerator,
      final VariableState variableState,
      final Writers writers,
      final ExpressionProcessor expressionBehavior,
      final BpmnStateBehavior stateBehavior) {
    this.keyGenerator = keyGenerator;
    this.expressionBehavior = expressionBehavior;
    this.variableState = variableState;
    this.stateBehavior = stateBehavior;
    commandWriter = writers.command();
  }

  public Either<Failure, ?> broadcastNewSignal(
      final BpmnElementContext context, final ExecutableSignal signal) {

    final var variables =
        variableState.getVariablesLocalAsDocument(context.getElementInstanceKey());

    final var runScope = getRunScope(context.getRootProcessInstanceKey());

    return evaluateSignalName(signal, context)
        .map(
            signalName -> {
              triggerSignalBroadcast(signalName, context.getTenantId(), variables, runScope);
              return null;
            });
  }

  private String getRunScope(final long rootProcessInstanceKey) {
    // the reserved run-scope tag is carried on the root instance record, not the throwing element
    final var rootInstance = stateBehavior.getElementInstance(rootProcessInstanceKey);
    return rootInstance != null
        ? TagUtil.findRunScopeValue(rootInstance.getValue().getTags()).orElse("")
        : "";
  }

  private Either<Failure, String> evaluateSignalName(
      final ExecutableSignal signal, final BpmnElementContext context) {

    ensureNotNull("signal", signal);

    if (signal.getSignalName().isEmpty()) {
      return expressionBehavior.evaluateStringExpression(
          signal.getSignalNameExpression(), context.getElementInstanceKey(), context.getTenantId());
    }

    return Either.right(signal.getSignalName().get());
  }

  private void triggerSignalBroadcast(
      final String signalName,
      final String tenantId,
      final DirectBuffer variables,
      final String runScope) {

    ensureNotNullOrEmpty("signalName", signalName);

    signalRecord.reset();
    signalRecord
        .setSignalName(signalName)
        .setVariables(variables)
        .setTenantId(tenantId)
        .setRunScope(runScope);

    final var key = keyGenerator.nextKey();
    commandWriter.appendFollowUpCommand(key, SignalIntent.BROADCAST, signalRecord);
  }
}
