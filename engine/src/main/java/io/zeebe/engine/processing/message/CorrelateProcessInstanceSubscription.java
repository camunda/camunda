/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.message;

import io.zeebe.engine.processing.common.EventHandle;
import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.zeebe.engine.processing.streamprocessor.ReadonlyProcessingContext;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.immutable.ProcessState;
import io.zeebe.engine.state.message.ProcessInstanceSubscription;
import io.zeebe.engine.state.mutable.MutableProcessInstanceSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.ProcessInstanceSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.ProcessInstanceSubscriptionIntent;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ActorControl;
import java.time.Duration;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

public final class CorrelateProcessInstanceSubscription
    implements TypedRecordProcessor<ProcessInstanceSubscriptionRecord> {

  private static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration SUBSCRIPTION_CHECK_INTERVAL = Duration.ofSeconds(30);
  private static final String NO_EVENT_OCCURRED_MESSAGE =
      "Expected to correlate a process instance subscription with element key '%d' and message name '%s', "
          + "but the subscription is not active anymore";
  private static final String NO_SUBSCRIPTION_FOUND_MESSAGE =
      "Expected to correlate process instance subscription with element key '%d' and message name '%s', "
          + "but no such subscription was found";
  private static final String ALREADY_CLOSING_MESSAGE =
      "Expected to correlate process instance subscription with element key '%d' and message name '%s', "
          + "but it is already closing";

  private final MutableProcessInstanceSubscriptionState subscriptionState;
  private final SubscriptionCommandSender subscriptionCommandSender;
  private final ProcessState processState;
  private final ElementInstanceState elementInstanceState;
  private final KeyGenerator keyGenerator;

  private final EventHandle eventHandle;

  private final ProcessInstanceRecord eventSubprocessRecord = new ProcessInstanceRecord();
  private ProcessInstanceSubscriptionRecord subscriptionRecord;
  private DirectBuffer correlationKey;

  public CorrelateProcessInstanceSubscription(
      final MutableProcessInstanceSubscriptionState subscriptionState,
      final SubscriptionCommandSender subscriptionCommandSender,
      final ZeebeState zeebeState) {
    this.subscriptionState = subscriptionState;
    this.subscriptionCommandSender = subscriptionCommandSender;
    processState = zeebeState.getProcessState();
    elementInstanceState = zeebeState.getElementInstanceState();
    keyGenerator = zeebeState.getKeyGenerator();

    eventHandle = new EventHandle(keyGenerator, zeebeState.getEventScopeInstanceState());
  }

  @Override
  public void onRecovered(final ReadonlyProcessingContext processingContext) {
    final ActorControl actor = processingContext.getActor();

    final PendingProcessInstanceSubscriptionChecker pendingSubscriptionChecker =
        new PendingProcessInstanceSubscriptionChecker(
            subscriptionCommandSender, subscriptionState, SUBSCRIPTION_TIMEOUT.toMillis());
    actor.runAtFixedRate(SUBSCRIPTION_CHECK_INTERVAL, pendingSubscriptionChecker);
  }

  @Override
  public void onClose() {}

  @Override
  public void processRecord(
      final TypedRecord<ProcessInstanceSubscriptionRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {

    subscriptionRecord = record.getValue();
    final long elementInstanceKey = subscriptionRecord.getElementInstanceKey();

    final ProcessInstanceSubscription subscription =
        subscriptionState.getSubscription(
            elementInstanceKey, subscriptionRecord.getMessageNameBuffer());

    if (subscription == null || subscription.isClosing()) {
      RejectionType type = RejectionType.NOT_FOUND;
      String reason = NO_SUBSCRIPTION_FOUND_MESSAGE;

      if (subscription != null) { // closing
        type = RejectionType.INVALID_STATE;
        reason = ALREADY_CLOSING_MESSAGE;
        correlationKey = subscription.getCorrelationKey();
      } else {
        correlationKey = subscriptionRecord.getCorrelationKeyBuffer();
      }

      sideEffect.accept(this::sendRejectionCommand);
      streamWriter.appendRejection(
          record,
          type,
          String.format(
              reason,
              subscriptionRecord.getElementInstanceKey(),
              BufferUtil.bufferAsString(subscriptionRecord.getMessageNameBuffer())));
      return;
    }

    if (subscription.shouldCloseOnCorrelate()) {
      subscriptionState.remove(subscription);
    }

    final boolean isTriggered =
        triggerCatchEvent(streamWriter, subscription, record.getValue().getVariablesBuffer());

    if (isTriggered) {
      sideEffect.accept(this::sendAcknowledgeCommand);

      streamWriter.appendFollowUpEvent(
          record.getKey(), ProcessInstanceSubscriptionIntent.CORRELATED, subscriptionRecord);

    } else {
      correlationKey = subscription.getCorrelationKey();
      sideEffect.accept(this::sendRejectionCommand);

      streamWriter.appendRejection(
          record,
          RejectionType.INVALID_STATE,
          String.format(
              NO_EVENT_OCCURRED_MESSAGE,
              subscriptionRecord.getElementInstanceKey(),
              BufferUtil.bufferAsString(subscriptionRecord.getMessageNameBuffer())));
    }
  }

  private boolean triggerCatchEvent(
      final TypedStreamWriter streamWriter,
      final ProcessInstanceSubscription subscription,
      final DirectBuffer variables) {

    final var elementInstance =
        elementInstanceState.getInstance(subscription.getElementInstanceKey());
    if (elementInstance == null) {
      return false;
    }

    final var processDefinitionKey = elementInstance.getValue().getProcessDefinitionKey();
    final var catchEvent =
        processState.getFlowElement(
            processDefinitionKey, subscription.getTargetElementId(), ExecutableFlowElement.class);

    return eventHandle.triggerEvent(streamWriter, elementInstance, catchEvent, variables);
  }

  private boolean sendAcknowledgeCommand() {
    return subscriptionCommandSender.correlateMessageSubscription(
        subscriptionRecord.getSubscriptionPartitionId(),
        subscriptionRecord.getProcessInstanceKey(),
        subscriptionRecord.getElementInstanceKey(),
        subscriptionRecord.getBpmnProcessIdBuffer(),
        subscriptionRecord.getMessageNameBuffer());
  }

  private boolean sendRejectionCommand() {
    return subscriptionCommandSender.rejectCorrelateMessageSubscription(
        subscriptionRecord.getProcessInstanceKey(),
        subscriptionRecord.getBpmnProcessIdBuffer(),
        subscriptionRecord.getMessageKey(),
        subscriptionRecord.getMessageNameBuffer(),
        correlationKey);
  }
}
