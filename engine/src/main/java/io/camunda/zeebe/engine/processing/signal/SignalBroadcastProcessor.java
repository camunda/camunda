/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.signal;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.SignalSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class SignalBroadcastProcessor implements TypedRecordProcessor<SignalRecord> {

  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final EventHandle eventHandle;
  private final TypedResponseWriter responseWriter;
  private final SignalSubscriptionState signalSubscriptionState;

  public SignalBroadcastProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final EventScopeInstanceState eventScopeInstanceState,
      final ProcessState processState,
      final BpmnStateBehavior stateBehavior,
      final EventTriggerBehavior eventTriggerBehavior,
      final SignalSubscriptionState signalSubscriptionState) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    this.signalSubscriptionState = signalSubscriptionState;
    this.keyGenerator = keyGenerator;
    eventHandle =
        new EventHandle(
            keyGenerator,
            eventScopeInstanceState,
            writers,
            processState,
            eventTriggerBehavior,
            stateBehavior);
  }

  @Override
  public void processRecord(final TypedRecord<SignalRecord> command) {
    final var signalRecord = command.getValue();
    final var key = command.getKey();
    final var signalName = signalRecord.getSignalNameBuffer();
    final var eventKey = key > -1 ? key : keyGenerator.nextKey();

    stateWriter.appendFollowUpEvent(eventKey, SignalIntent.BROADCASTED, signalRecord);
    responseWriter.writeEventOnCommand(eventKey, SignalIntent.BROADCASTED, signalRecord, command);

    signalSubscriptionState.visitBySignalName(
        signalName,
        subscription -> {
          final var subscriptionRecord = subscription.getRecord();
          final var processDefinitionKey = subscriptionRecord.getProcessDefinitionKey();

          if (subscriptionRecord.getCatchEventInstanceKey() == -1) {
            eventHandle.activateProcessInstanceForStartEvent(
                processDefinitionKey,
                keyGenerator.nextKey(),
                subscriptionRecord.getCatchEventIdBuffer(),
                signalRecord.getVariablesBuffer());
          }
        });
  }
}
