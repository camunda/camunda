/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.message;

import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.ReadonlyProcessingContext;
import io.zeebe.engine.processor.SideEffectProducer;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.deployment.model.element.AbstractFlowElement;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableStartEvent;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.DeployedWorkflow;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.message.WorkflowInstanceSubscription;
import io.zeebe.engine.state.message.WorkflowInstanceSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.WorkflowInstanceSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ActorControl;
import java.time.Duration;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

public final class CorrelateWorkflowInstanceSubscription
    implements TypedRecordProcessor<WorkflowInstanceSubscriptionRecord> {

  private static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration SUBSCRIPTION_CHECK_INTERVAL = Duration.ofSeconds(30);
  private static final String NO_EVENT_OCCURRED_MESSAGE =
      "Expected to correlate a workflow instance subscription with element key '%d' and message name '%s', "
          + "but the subscription is not active anymore";
  private static final String NO_SUBSCRIPTION_FOUND_MESSAGE =
      "Expected to correlate workflow instance subscription with element key '%d' and message name '%s', "
          + "but no such subscription was found";
  private static final String ALREADY_CLOSING_MESSAGE =
      "Expected to correlate workflow instance subscription with element key '%d' and message name '%s', "
          + "but it is already closing";

  private final WorkflowInstanceSubscriptionState subscriptionState;
  private final SubscriptionCommandSender subscriptionCommandSender;
  private final WorkflowState workflowState;
  private final KeyGenerator keyGenerator;

  private final WorkflowInstanceRecord eventSubprocessRecord = new WorkflowInstanceRecord();
  private WorkflowInstanceSubscriptionRecord subscriptionRecord;
  private DirectBuffer correlationKey;

  public CorrelateWorkflowInstanceSubscription(
      final WorkflowInstanceSubscriptionState subscriptionState,
      final SubscriptionCommandSender subscriptionCommandSender,
      final ZeebeState zeebeState) {
    this.subscriptionState = subscriptionState;
    this.subscriptionCommandSender = subscriptionCommandSender;
    workflowState = zeebeState.getWorkflowState();
    keyGenerator = zeebeState.getKeyGenerator();
  }

  @Override
  public void onOpen(final ReadonlyProcessingContext processingContext) {
    final ActorControl actor = processingContext.getActor();

    final PendingWorkflowInstanceSubscriptionChecker pendingSubscriptionChecker =
        new PendingWorkflowInstanceSubscriptionChecker(
            subscriptionCommandSender, subscriptionState, SUBSCRIPTION_TIMEOUT.toMillis());
    actor.runAtFixedRate(SUBSCRIPTION_CHECK_INTERVAL, pendingSubscriptionChecker);
  }

  @Override
  public void onClose() {}

  @Override
  public void processRecord(
      final TypedRecord<WorkflowInstanceSubscriptionRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {

    subscriptionRecord = record.getValue();
    final long elementInstanceKey = subscriptionRecord.getElementInstanceKey();

    final WorkflowInstanceSubscription subscription =
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

    final ElementInstance elementInstance =
        workflowState.getElementInstanceState().getInstance(subscription.getElementInstanceKey());
    final long eventKey = keyGenerator.nextKey();
    final boolean isOccurred =
        workflowState
            .getEventScopeInstanceState()
            .triggerEvent(
                subscriptionRecord.getElementInstanceKey(),
                eventKey,
                subscription.getTargetElementId(),
                record.getValue().getVariablesBuffer());

    if (isOccurred) {
      sideEffect.accept(this::sendAcknowledgeCommand);

      streamWriter.appendFollowUpEvent(
          record.getKey(), WorkflowInstanceSubscriptionIntent.CORRELATED, subscriptionRecord);
      writeEventOccurred(record, streamWriter, subscription, elementInstance);
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

  private void writeEventOccurred(
      final TypedRecord<WorkflowInstanceSubscriptionRecord> record,
      final TypedStreamWriter streamWriter,
      final WorkflowInstanceSubscription subscription,
      final ElementInstance elementInstance) {
    final long workflowKey = elementInstance.getValue().getWorkflowKey();
    final DeployedWorkflow workflow = workflowState.getWorkflowByKey(workflowKey);

    if (isEventSubprocessStart(workflow, subscription.getTargetElementId())) {
      eventSubprocessRecord.reset();
      eventSubprocessRecord
          .setWorkflowKey(workflowKey)
          .setElementId(subscription.getTargetElementId())
          .setWorkflowInstanceKey(record.getValue().getWorkflowInstanceKey())
          .setBpmnElementType(BpmnElementType.START_EVENT)
          .setBpmnProcessId(workflow.getBpmnProcessId())
          .setVersion(workflow.getVersion())
          .setFlowScopeKey(elementInstance.getKey());

      streamWriter.appendFollowUpEvent(
          keyGenerator.nextKey(), WorkflowInstanceIntent.EVENT_OCCURRED, eventSubprocessRecord);
    } else {
      streamWriter.appendFollowUpEvent(
          subscriptionRecord.getElementInstanceKey(),
          WorkflowInstanceIntent.EVENT_OCCURRED,
          elementInstance.getValue());
    }
  }

  private boolean isEventSubprocessStart(
      final DeployedWorkflow workflow, final DirectBuffer catchEventId) {
    final AbstractFlowElement catchEvent = workflow.getWorkflow().getElementById(catchEventId);

    return ExecutableStartEvent.class.isAssignableFrom(catchEvent.getClass())
        && ((ExecutableStartEvent) catchEvent).getEventSubProcess() != null;
  }

  private boolean sendAcknowledgeCommand() {
    return subscriptionCommandSender.correlateMessageSubscription(
        subscriptionRecord.getSubscriptionPartitionId(),
        subscriptionRecord.getWorkflowInstanceKey(),
        subscriptionRecord.getElementInstanceKey(),
        subscriptionRecord.getBpmnProcessIdBuffer(),
        subscriptionRecord.getMessageNameBuffer());
  }

  private boolean sendRejectionCommand() {
    return subscriptionCommandSender.rejectCorrelateMessageSubscription(
        subscriptionRecord.getWorkflowInstanceKey(),
        subscriptionRecord.getBpmnProcessIdBuffer(),
        subscriptionRecord.getMessageKey(),
        subscriptionRecord.getMessageNameBuffer(),
        correlationKey);
  }
}
