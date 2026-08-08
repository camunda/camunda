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
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo.AuthDataFormat;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import org.agrona.DirectBuffer;

public final class BpmnSignalBehavior {

  private final SignalRecord signalRecord =
      new SignalRecord().setVariables(DocumentValue.EMPTY_DOCUMENT);
  private final KeyGenerator keyGenerator;
  private final VariableState variableState;
  private final TypedCommandWriter commandWriter;
  private final ExpressionProcessor expressionBehavior;

  public BpmnSignalBehavior(
      final KeyGenerator keyGenerator,
      final VariableState variableState,
      final Writers writers,
      final ExpressionProcessor expressionBehavior) {
    this.keyGenerator = keyGenerator;
    this.expressionBehavior = expressionBehavior;
    this.variableState = variableState;
    commandWriter = writers.command();
  }

  public Either<Failure, ?> broadcastNewSignal(
      final BpmnElementContext context, final ExecutableSignal signal) {

    final var variables =
        variableState.getVariablesLocalAsDocument(context.getElementInstanceKey());

    return evaluateSignalName(signal, context)
        .map(
            signalName -> {
              triggerSignalBroadcast(signalName, context.getTenantId(), variables);
              return null;
            });
  }

  private Either<Failure, String> evaluateSignalName(
      final ExecutableSignal signal, final BpmnElementContext context) {

    ensureNotNull("signal", signal);

    if (signal.getSignalName().isEmpty()) {
      return expressionBehavior.evaluateStringExpression(
          signal.getSignalNameExpression(), context.getElementInstanceKey());
    }

    return Either.right(signal.getSignalName().get());
  }

  private void triggerSignalBroadcast(
      final String signalName, final String tenantId, final DirectBuffer variables) {

    ensureNotNullOrEmpty("signalName", signalName);

    signalRecord.reset();
    signalRecord.setSignalName(signalName).setVariables(variables).setTenantId(tenantId);

    final var key = keyGenerator.nextKey();
    // A signal broadcast triggered by a throw event is an internal side-effect of process
    // execution, not a user-initiated broadcast, so it must not be subjected to the
    // CREATE_PROCESS_INSTANCE / UPDATE_PROCESS_INSTANCE authorization check in
    // SignalBroadcastProcessor. It is normally treated as internal via isInternalCommand(), but a
    // command processed within a batch operation propagates its batchOperationReference to every
    // follow-up command, which makes the follow-up non-internal. Marking the follow-up as
    // PRE_AUTHORIZED keeps it exempt from the user authorization check regardless of the inherited
    // batchOperationReference.
    final var authInfo = new AuthInfo().setFormat(AuthDataFormat.PRE_AUTHORIZED);
    commandWriter.appendFollowUpCommand(
        key,
        SignalIntent.BROADCAST,
        signalRecord,
        FollowUpCommandMetadata.of(builder -> builder.authInfo(authInfo)));
  }
}
