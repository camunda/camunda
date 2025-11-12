/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.conditional;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEvent;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ConditionSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.condition.ConditionSubscriptionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ConditionSubscriptionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

@ExcludeAuthorizationCheck
public class ConditionalTriggerProcessor
    implements TypedRecordProcessor<ConditionSubscriptionRecord> {

  private final ConditionSubscriptionState conditionSubscriptionState;
  private final ProcessState processState;
  private final ElementInstanceState elementInstanceState;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;

  private final EventHandle eventHandle;

  public ConditionalTriggerProcessor(
      final MutableProcessingState processingState,
      final BpmnBehaviors bpmnBehaviors,
      final Writers writers) {
    conditionSubscriptionState = processingState.getConditionSubscriptionState();
    processState = processingState.getProcessState();
    elementInstanceState = processingState.getElementInstanceState();
    keyGenerator = processingState.getKeyGenerator();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    eventHandle =
        new EventHandle(
            keyGenerator,
            processingState.getEventScopeInstanceState(),
            writers,
            processState,
            bpmnBehaviors.eventTriggerBehavior(),
            bpmnBehaviors.stateBehavior());
  }

  @Override
  public void processRecord(final TypedRecord<ConditionSubscriptionRecord> record) {
    final var subscription = record.getValue();
    final var elementInstanceKey = subscription.getElementInstanceKey();
    final var processDefinitionKey = subscription.getProcessDefinitionKey();

    final boolean exists =
        conditionSubscriptionState.exists(subscription.getTenantId(), record.getKey());

    if (!exists) {
      rejectionWriter.appendRejection(
          record,
          RejectionType.NOT_FOUND,
          String.format(
              "Expected to trigger condition subscription with key '%d', but no such subscription was found",
              record.getKey()));
      return;
    }

    final var elementInstance = elementInstanceState.getInstance(elementInstanceKey);
    if (!eventHandle.canTriggerElement(elementInstance, subscription.getCatchEventIdBuffer())) {
      rejectionWriter.appendRejection(
          record,
          RejectionType.INVALID_STATE,
          String.format(
              "Expected to trigger condition for element with id '%s' in instance with key '%d', but the element is not active anymore",
              subscription.getCatchEventId(), elementInstanceKey));
      return;
    }

    final var catchEventId = subscription.getCatchEventIdBuffer();
    final var catchEvent =
        processState.getFlowElement(
            processDefinitionKey,
            subscription.getTenantId(),
            catchEventId,
            ExecutableCatchEvent.class);

    stateWriter.appendFollowUpEvent(
        record.getKey(), ConditionSubscriptionIntent.TRIGGERED, subscription);
    eventHandle.activateElement(catchEvent, elementInstanceKey, elementInstance.getValue());
  }
}
