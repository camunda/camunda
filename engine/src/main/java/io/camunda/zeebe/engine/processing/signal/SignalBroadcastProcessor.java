/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.signal;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.common.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEvent;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SignalSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.agrona.DirectBuffer;

public class SignalBroadcastProcessor implements DistributedTypedRecordProcessor<SignalRecord> {

  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final EventHandle eventHandle;
  private final TypedResponseWriter responseWriter;
  private final SignalSubscriptionState signalSubscriptionState;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final ProcessState processState;
  private final ElementInstanceState elementInstanceState;

  public SignalBroadcastProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ProcessingState processingState,
      final BpmnStateBehavior stateBehavior,
      final EventTriggerBehavior eventTriggerBehavior,
      final CommandDistributionBehavior commandDistributionBehavior) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    processState = processingState.getProcessState();
    signalSubscriptionState = processingState.getSignalSubscriptionState();
    this.keyGenerator = keyGenerator;
    this.commandDistributionBehavior = commandDistributionBehavior;
    elementInstanceState = processingState.getElementInstanceState();
    eventHandle =
        new EventHandle(
            keyGenerator,
            processingState.getEventScopeInstanceState(),
            writers,
            processState,
            eventTriggerBehavior,
            stateBehavior);
  }

  @Override
  public void processNewCommand(final TypedRecord<SignalRecord> command) {
    final long eventKey = keyGenerator.nextKey();
    final var signalRecord = command.getValue();

    stateWriter.appendFollowUpEvent(eventKey, SignalIntent.BROADCASTED, signalRecord);
    responseWriter.writeEventOnCommand(eventKey, SignalIntent.BROADCASTED, signalRecord, command);

    signalSubscriptionState.visitBySignalName(
        signalRecord.getSignalNameBuffer(),
        signalRecord.getTenantId(),
        subscription -> {
          final var subscriptionRecord = subscription.getRecord();
          if (subscriptionRecord.getCatchEventInstanceKey() == -1) {
            eventHandle.activateProcessInstanceForStartEvent(
                subscriptionRecord.getProcessDefinitionKey(),
                keyGenerator.nextKey(),
                subscriptionRecord.getCatchEventIdBuffer(),
                signalRecord.getVariablesBuffer(),
                signalRecord.getTenantId());
          } else {
            activateElement(subscriptionRecord, signalRecord.getVariablesBuffer());
          }
        });

    commandDistributionBehavior.distributeCommand(eventKey, command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<SignalRecord> command) {
    final var value = command.getValue();
    signalSubscriptionState.visitBySignalName(
        value.getSignalNameBuffer(),
        value.getTenantId(),
        subscription -> activateElement(subscription.getRecord(), value.getVariablesBuffer()));

    stateWriter.appendFollowUpEvent(command.getKey(), SignalIntent.BROADCASTED, command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command.getKey(), command);
  }

  private void activateElement(
      final SignalSubscriptionRecord subscription, final DirectBuffer variables) {
    final var processDefinitionKey = subscription.getProcessDefinitionKey();
    final var catchEventInstanceKey = subscription.getCatchEventInstanceKey();
    final var catchEventId = subscription.getCatchEventIdBuffer();
    final var catchEvent =
        processState.getFlowElement(
            processDefinitionKey,
            subscription.getTenantId(),
            catchEventId,
            ExecutableCatchEvent.class);

    final var elementInstance = elementInstanceState.getInstance(catchEventInstanceKey);
    final var canTriggerElement = eventHandle.canTriggerElement(elementInstance, catchEventId);

    if (canTriggerElement) {
      eventHandle.activateElement(
          catchEvent, catchEventInstanceKey, elementInstance.getValue(), variables);
    }
  }
}
