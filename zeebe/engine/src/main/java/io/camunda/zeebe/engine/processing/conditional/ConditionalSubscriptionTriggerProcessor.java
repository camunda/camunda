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
import io.camunda.zeebe.engine.state.immutable.ConditionalSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalSubscriptionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ConditionalSubscriptionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

@ExcludeAuthorizationCheck
public class ConditionalSubscriptionTriggerProcessor
    implements TypedRecordProcessor<ConditionalSubscriptionRecord> {

  private static final String NO_CONDITIONAL_SUBSCRIPTION_FOUND_MESSAGE =
      "Expected to trigger condition subscription with key '%d', but no such subscription was found "
          + "for process instance with key '%d' and catch event id '%s'.";

  private static final String NO_ACTIVE_ELEMENT_FOUND_FOR_CONDITIONAL_MESSAGE =
      "Expected to trigger condition subscription with key '%d', but the element with key '%d' is "
          + "not active anymore for process instance with key '%d' and catch event id '%s'.";
  private static final DirectBuffer NO_VARIABLES = new UnsafeBuffer();

  private final ConditionalSubscriptionState conditionSubscriptionState;
  private final ProcessState processState;
  private final ElementInstanceState elementInstanceState;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;

  private final EventHandle eventHandle;

  public ConditionalSubscriptionTriggerProcessor(
      final MutableProcessingState processingState,
      final BpmnBehaviors bpmnBehaviors,
      final Writers writers) {
    conditionSubscriptionState = processingState.getConditionalSubscriptionState();
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
  public void processRecord(final TypedRecord<ConditionalSubscriptionRecord> record) {
    final var subscription = record.getValue();
    final String tenantId = subscription.getTenantId();

    final boolean exists = conditionSubscriptionState.exists(tenantId, record.getKey());
    if (!exists) {
      rejectionWriter.appendRejection(
          record,
          RejectionType.NOT_FOUND,
          String.format(
              NO_CONDITIONAL_SUBSCRIPTION_FOUND_MESSAGE,
              record.getKey(),
              subscription.getProcessInstanceKey(),
              subscription.getCatchEventId()));
      return;
    }

    final var elementInstanceKey = subscription.getElementInstanceKey();
    final var elementInstance = elementInstanceState.getInstance(elementInstanceKey);
    final DirectBuffer catchEventIdBuffer = subscription.getCatchEventIdBuffer();
    if (!eventHandle.canTriggerElement(elementInstance, catchEventIdBuffer)) {
      rejectionWriter.appendRejection(
          record,
          RejectionType.INVALID_STATE,
          String.format(
              NO_ACTIVE_ELEMENT_FOUND_FOR_CONDITIONAL_MESSAGE,
              record.getKey(),
              elementInstanceKey,
              subscription.getProcessInstanceKey(),
              subscription.getCatchEventId()));
      return;
    }

    final boolean isStartEvent = elementInstanceKey < 0;
    if (isStartEvent) {
      final long processInstanceKey = keyGenerator.nextKey();
      subscription.setProcessInstanceKey(processInstanceKey);
      stateWriter.appendFollowUpEvent(
          record.getKey(), ConditionalSubscriptionIntent.TRIGGERED, subscription);
      eventHandle.activateProcessInstanceForStartEvent(
          subscription.getProcessDefinitionKey(),
          processInstanceKey,
          subscription.getCatchEventIdBuffer(),
          NO_VARIABLES, // TODO - this needs to updated to pass variables from the condition
          tenantId);
      return;
    }

    stateWriter.appendFollowUpEvent(
        record.getKey(), ConditionalSubscriptionIntent.TRIGGERED, subscription);
    final var catchEvent =
        processState.getFlowElement(
            subscription.getProcessDefinitionKey(),
            tenantId,
            catchEventIdBuffer,
            ExecutableCatchEvent.class);
    eventHandle.activateElement(catchEvent, elementInstanceKey, elementInstance.getValue());
  }
}
