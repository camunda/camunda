/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message.command;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import org.agrona.DirectBuffer;

/**
 * Send commands via the subscription endpoint. The commands are send as single messages (instead of request-response).
 * To ensure that a command is received, each command has an ACK command which is send by the receiver.
 *
 * <pre>
 *+---------------------------------------------------------------------------------------------------------------------------------------+
 *|                                                       Message Partition                                                               |
 *|                                                                                                                                       |
 *+-----------^----------------+---------------------------+----------------------^-------------------------^------------------+----------+
 *            |                |                           |                      |                         |                  |
 *    +-------+------+  +------+--------+       +----------+---------+  +---------+---------+       +-------+-------+  +-------+--------+
 *    | Open Message |  | Open Process |       | Correlate Process |  | Correlate Message |       | Close Message |  | Close Process |
 *    | Subscription |  | Instance Sub  |       | Instance Sub       |  | Subscription      |       | Subscription  |  | Instance Sub   |
 *    +-------+------+  +------+--------+       +----------+---------+  +---------+---------+       +-------+-------+  +-------+--------+
 *            |                |                           |                      |                         |                  |
 * +----------+----------------v---------------------------v----------------------+-------------------------+------------------v----------+
 * |                                                                                                                                      |
 * |                                                   Process Instance Partition                                                        |
 * +--------------------------------------------------------------------------------------------------------------------------------------+
 * <pre>
 */
public class SubscriptionCommandSender {
  private final InterPartitionCommandSender interPartitionCommandSender;
  private final int senderPartition;
  private Writers writers;

  public SubscriptionCommandSender(
      final int senderPartition, final InterPartitionCommandSender interPartitionCommandSender) {
    this.senderPartition = senderPartition;
    this.interPartitionCommandSender = interPartitionCommandSender;
  }

  public boolean openMessageSubscription(
      final int subscriptionPartitionId,
      final long processInstanceKey,
      final long elementInstanceKey,
      final long processDefinitionKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey,
      final boolean closeOnCorrelate,
      final String tenantId,
      final DirectBuffer businessId,
      final DirectBuffer elementId,
      final long rootProcessInstanceKey,
      final BpmnElementType elementType) {
    return handleFollowUpCommandBasedOnPartition(
        subscriptionPartitionId,
        ValueType.MESSAGE_SUBSCRIPTION,
        MessageSubscriptionIntent.CREATE,
        new MessageSubscriptionRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setProcessDefinitionKey(processDefinitionKey)
            .setBpmnProcessId(bpmnProcessId)
            .setMessageKey(-1)
            .setMessageName(messageName)
            .setCorrelationKey(correlationKey)
            .setInterrupting(closeOnCorrelate)
            .setTenantId(tenantId)
            .setBusinessId(businessId)
            .setElementId(elementId)
            .setRootProcessInstanceKey(rootProcessInstanceKey)
            .setElementType(elementType));
  }

  /**
   * Sends the open message subscription command directly to the subscription partition. This
   * differs to ${link openMessageSubscription}, as the sending/writing is not delayed. Usually
   * useful or used in scheduled tasks, which want to directly send commands.
   *
   * @param subscriptionPartitionId the partition Id which should receive the command
   * @param processInstanceKey the related process instance key
   * @param elementInstanceKey the related element instance key
   * @param bpmnProcessId the related process id
   * @param messageName the name of the message for which the subscription should be correlated
   * @param correlationKey the correlation key for which the message should be correlated
   * @param closeOnCorrelate indicates whether the subscription should be closed after correlation
   * @param tenantId the tenant id the message subscription is created for
   * @param businessId the business id captured from the subscribing process instance at open time;
   *     used as a post-routing local filter on the subscription partition. May be an empty buffer
   *     when the process instance has no business id.
   */
  public void sendDirectOpenMessageSubscription(
      final int subscriptionPartitionId,
      final long processInstanceKey,
      final long elementInstanceKey,
      final long processDefinitionKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey,
      final boolean closeOnCorrelate,
      final String tenantId,
      final DirectBuffer businessId,
      final DirectBuffer elementId,
      final long rootProcessInstanceKey,
      final BpmnElementType elementType) {
    interPartitionCommandSender.sendCommand(
        subscriptionPartitionId,
        ValueType.MESSAGE_SUBSCRIPTION,
        MessageSubscriptionIntent.CREATE,
        new MessageSubscriptionRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setProcessDefinitionKey(processDefinitionKey)
            .setBpmnProcessId(bpmnProcessId)
            .setMessageKey(-1)
            .setMessageName(messageName)
            .setCorrelationKey(correlationKey)
            .setInterrupting(closeOnCorrelate)
            .setTenantId(tenantId)
            .setBusinessId(businessId)
            .setElementId(elementId)
            .setRootProcessInstanceKey(rootProcessInstanceKey)
            .setElementType(elementType));
  }

  public boolean openProcessMessageSubscription(
      final long processInstanceKey,
      final long elementInstanceKey,
      final long processDefinitionKey,
      final DirectBuffer messageName,
      final boolean closeOnCorrelate,
      final String tenantId,
      final DirectBuffer businessId) {
    return handleFollowUpCommandBasedOnPartition(
        Protocol.decodePartitionId(processInstanceKey),
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
        ProcessMessageSubscriptionIntent.CREATE,
        new ProcessMessageSubscriptionRecord()
            .setSubscriptionPartitionId(senderPartition)
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setProcessDefinitionKey(processDefinitionKey)
            .setMessageKey(-1)
            .setMessageName(messageName)
            .setInterrupting(closeOnCorrelate)
            .setTenantId(tenantId)
            .setBusinessId(businessId));
  }

  public boolean correlateProcessMessageSubscription(
      final long processInstanceKey,
      final long elementInstanceKey,
      final long processDefinitionKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer messageName,
      final long messageKey,
      final DirectBuffer variables,
      final DirectBuffer correlationKey,
      final String tenantId) {
    return handleFollowUpCommandBasedOnPartition(
        Protocol.decodePartitionId(processInstanceKey),
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
        ProcessMessageSubscriptionIntent.CORRELATE,
        new ProcessMessageSubscriptionRecord()
            .setSubscriptionPartitionId(senderPartition)
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setProcessDefinitionKey(processDefinitionKey)
            .setBpmnProcessId(bpmnProcessId)
            .setMessageKey(messageKey)
            .setMessageName(messageName)
            .setVariables(variables)
            .setCorrelationKey(correlationKey)
            .setTenantId(tenantId));
  }

  /**
   * Sends the correlate process message subscription command directly to the subscribed partition.
   * This differs to ${link correlateProcessMessageSubscription}, as the sending/writing is not
   * delayed. Usually useful or used in scheduled tasks, which want to directly send commands.
   *
   * @param processInstanceKey the related process instance key
   * @param elementInstanceKey the related element instance key
   * @param bpmnProcessId the related process id
   * @param messageName the name of the message for which the subscription should be correlated
   * @param messageKey the key of the message for which the subscription should be correlated
   * @param variables the variables of the message
   * @param correlationKey the correlation key for which the message should be correlated
   * @param tenantId the tenant the message subscription is correlated for
   */
  public void sendDirectCorrelateProcessMessageSubscription(
      final long processInstanceKey,
      final long elementInstanceKey,
      final long processDefinitionKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer messageName,
      final long messageKey,
      final DirectBuffer variables,
      final DirectBuffer correlationKey,
      final String tenantId) {
    interPartitionCommandSender.sendCommand(
        Protocol.decodePartitionId(processInstanceKey),
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
        ProcessMessageSubscriptionIntent.CORRELATE,
        new ProcessMessageSubscriptionRecord()
            .setSubscriptionPartitionId(senderPartition)
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setProcessDefinitionKey(processDefinitionKey)
            .setBpmnProcessId(bpmnProcessId)
            .setMessageKey(messageKey)
            .setMessageName(messageName)
            .setVariables(variables)
            .setCorrelationKey(correlationKey)
            .setTenantId(tenantId));
  }

  public boolean correlateMessageSubscription(
      final long messageKey,
      final int subscriptionPartitionId,
      final long processInstanceKey,
      final long elementInstanceKey,
      final long processDefinitionKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer messageName,
      final String tenantId) {
    return handleFollowUpCommandBasedOnPartition(
        subscriptionPartitionId,
        ValueType.MESSAGE_SUBSCRIPTION,
        MessageSubscriptionIntent.CORRELATE,
        new MessageSubscriptionRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setProcessDefinitionKey(processDefinitionKey)
            .setBpmnProcessId(bpmnProcessId)
            .setMessageKey(messageKey)
            .setMessageName(messageName)
            .setTenantId(tenantId));
  }

  public boolean closeMessageSubscription(
      final int subscriptionPartitionId,
      final long processInstanceKey,
      final long elementInstanceKey,
      final long processDefinitionKey,
      final DirectBuffer messageName,
      final String tenantId) {
    return handleFollowUpCommandBasedOnPartition(
        subscriptionPartitionId,
        ValueType.MESSAGE_SUBSCRIPTION,
        MessageSubscriptionIntent.DELETE,
        new MessageSubscriptionRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setProcessDefinitionKey(processDefinitionKey)
            .setMessageKey(-1L)
            .setMessageName(messageName)
            .setTenantId(tenantId));
  }

  /**
   * Sends the close message subscription command directly to the subscription partition. This
   * differs to ${link closeMessageSubscription}, as the sending/writing is not delayed. Usually
   * useful or used in scheduled tasks, which want to directly send commands.
   *
   * @param subscriptionPartitionId the partition Id which should receive the command
   * @param processInstanceKey the related process instance key
   * @param elementInstanceKey the related element instance key
   * @param messageName the name of the message for which the subscription should be closed
   * @param tenantId the tenant for which the subscription should be closed
   */
  public void sendDirectCloseMessageSubscription(
      final int subscriptionPartitionId,
      final long processInstanceKey,
      final long elementInstanceKey,
      final long processDefinitionKey,
      final DirectBuffer messageName,
      final String tenantId) {
    interPartitionCommandSender.sendCommand(
        subscriptionPartitionId,
        ValueType.MESSAGE_SUBSCRIPTION,
        MessageSubscriptionIntent.DELETE,
        new MessageSubscriptionRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setProcessDefinitionKey(processDefinitionKey)
            .setMessageKey(-1L)
            .setMessageName(messageName)
            .setTenantId(tenantId));
  }

  public boolean closeProcessMessageSubscription(
      final long processInstanceKey,
      final long elementInstanceKey,
      final long processDefinitionKey,
      final DirectBuffer messageName,
      final String tenantId) {
    return handleFollowUpCommandBasedOnPartition(
        Protocol.decodePartitionId(processInstanceKey),
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
        ProcessMessageSubscriptionIntent.DELETE,
        new ProcessMessageSubscriptionRecord()
            .setSubscriptionPartitionId(senderPartition)
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setProcessDefinitionKey(processDefinitionKey)
            .setMessageKey(-1)
            .setMessageName(messageName)
            .setTenantId(tenantId));
  }

  public boolean rejectCorrelateMessageSubscription(
      final int subscriptionPartitionId,
      final long processInstanceKey,
      final long elementInstanceKey,
      final long processDefinitionKey,
      final DirectBuffer bpmnProcessId,
      final long messageKey,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey,
      final String tenantId) {
    return handleFollowUpCommandBasedOnPartition(
        subscriptionPartitionId,
        ValueType.MESSAGE_SUBSCRIPTION,
        MessageSubscriptionIntent.REJECT,
        new MessageSubscriptionRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setProcessDefinitionKey(processDefinitionKey)
            .setBpmnProcessId(bpmnProcessId)
            .setMessageName(messageName)
            .setCorrelationKey(correlationKey)
            .setMessageKey(messageKey)
            .setInterrupting(false)
            .setTenantId(tenantId));
  }

  /**
   * Sends a cross-partition request from {@code P_K = hash(correlationKey)} to {@code P_B =
   * hash(businessId)} asking {@code P_B} to create a message-start process instance under its
   * businessId uniqueness invariant. The reply is one of {@link
   * MessageStartProcessInstanceRequestIntent#STARTED}, {@link
   * MessageStartProcessInstanceRequestIntent#UNIQUENESS_REJECTED}, or {@link
   * MessageStartProcessInstanceRequestIntent#NO_SUBSCRIPTION_REJECTED}; the routing flip that
   * dispatches this request from the publish path lands later.
   *
   * <p>The wire payload mirrors the originating publish: {@code messageKey} additionally encodes
   * the source partition (via Zeebe's key-partitioning), so the reply target on {@code P_B} is
   * derivable without a separate field.
   */
  public boolean sendStartProcessInstanceRequest(
      final int targetPartitionId,
      final long messageKey,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey,
      final DirectBuffer businessId,
      final long processDefinitionKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer startEventId,
      final long messageStartEventSubscriptionKey,
      final DirectBuffer variables,
      final long messageDeadline,
      final String tenantId) {
    return handleFollowUpCommandBasedOnPartition(
        targetPartitionId,
        ValueType.MESSAGE_START_PROCESS_INSTANCE_REQUEST,
        MessageStartProcessInstanceRequestIntent.REQUEST,
        buildStartProcessInstanceRequest(
            messageKey,
            messageName,
            correlationKey,
            businessId,
            processDefinitionKey,
            bpmnProcessId,
            startEventId,
            messageStartEventSubscriptionKey,
            variables,
            messageDeadline,
            tenantId));
  }

  /**
   * Sends the start-process-instance ask directly to {@code P_B} bypassing the writers' post-commit
   * task. Mirrors {@link #sendDirectOpenMessageSubscription} and exists for the scheduled retry
   * task on {@code P_K} that drains the pending-ask state ({@link
   * io.camunda.zeebe.engine.processing.message.PendingMessageStartAskCheckScheduler}).
   */
  public void sendDirectStartProcessInstanceRequest(
      final int targetPartitionId,
      final long messageKey,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey,
      final DirectBuffer businessId,
      final long processDefinitionKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer startEventId,
      final long messageStartEventSubscriptionKey,
      final DirectBuffer variables,
      final long messageDeadline,
      final String tenantId) {
    interPartitionCommandSender.sendCommand(
        targetPartitionId,
        ValueType.MESSAGE_START_PROCESS_INSTANCE_REQUEST,
        MessageStartProcessInstanceRequestIntent.REQUEST,
        buildStartProcessInstanceRequest(
            messageKey,
            messageName,
            correlationKey,
            businessId,
            processDefinitionKey,
            bpmnProcessId,
            startEventId,
            messageStartEventSubscriptionKey,
            variables,
            messageDeadline,
            tenantId));
  }

  /**
   * Sends the {@link MessageStartProcessInstanceRequestIntent#STARTED} reply from {@code P_B} back
   * to {@code P_K}. The target partition is derived from {@code request.getMessageKey()} — the
   * source partition is encoded in the message key by Zeebe's key-partitioning.
   *
   * <p>The reply replays the request payload so the {@code P_K} handler has every field it needs to
   * commit the correlation locally (subscription-correlated event, local correlationKey lock entry,
   * buffered-message removal) without any further cross-partition lookup; the {@code
   * processInstanceKey} of the newly created instance is added on top.
   */
  public boolean sendStartProcessInstanceStarted(
      final MessageStartProcessInstanceRequestRecord request, final long processInstanceKey) {
    final MessageStartProcessInstanceRequestRecord reply =
        new MessageStartProcessInstanceRequestRecord();
    reply.wrap(request);
    reply.setProcessInstanceKey(processInstanceKey);
    return handleFollowUpCommandBasedOnPartition(
        Protocol.decodePartitionId(request.getMessageKey()),
        ValueType.MESSAGE_START_PROCESS_INSTANCE_REQUEST,
        MessageStartProcessInstanceRequestIntent.START,
        reply);
  }

  /**
   * Sends the {@link MessageStartProcessInstanceRequestIntent#UNIQUENESS_REJECTED} reply from
   * {@code P_B} back to {@code P_K} after the businessId uniqueness check on {@code P_B} found an
   * active holder. {@code P_K} keeps the message buffered (TTL continues) and waits for the
   * pull-based release introduced in a later increment.
   */
  public boolean sendStartProcessInstanceUniquenessRejected(
      final MessageStartProcessInstanceRequestRecord request) {
    return sendStartProcessInstanceRejection(
        request, MessageStartProcessInstanceRequestIntent.REJECT_UNIQUENESS);
  }

  /**
   * Sends the {@link MessageStartProcessInstanceRequestIntent#NO_SUBSCRIPTION_REJECTED} reply from
   * {@code P_B} back to {@code P_K} when {@code P_B} has no message-start-event subscription for
   * the requested definition yet (deployment-distribution race). {@code P_K} keeps the message
   * buffered, matching its local "no subscription right now" semantics; this is deliberately not a
   * discard.
   */
  public boolean sendStartProcessInstanceNoSubscriptionRejected(
      final MessageStartProcessInstanceRequestRecord request) {
    return sendStartProcessInstanceRejection(
        request, MessageStartProcessInstanceRequestIntent.REJECT_NO_SUBSCRIPTION);
  }

  private boolean sendStartProcessInstanceRejection(
      final MessageStartProcessInstanceRequestRecord request,
      final MessageStartProcessInstanceRequestIntent intent) {
    final MessageStartProcessInstanceRequestRecord reply =
        new MessageStartProcessInstanceRequestRecord();
    reply.wrap(request);
    // processInstanceKey stays at -1 for rejection replies; defaults are kept by wrap.
    return handleFollowUpCommandBasedOnPartition(
        Protocol.decodePartitionId(request.getMessageKey()),
        ValueType.MESSAGE_START_PROCESS_INSTANCE_REQUEST,
        intent,
        reply);
  }

  private MessageStartProcessInstanceRequestRecord buildStartProcessInstanceRequest(
      final long messageKey,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey,
      final DirectBuffer businessId,
      final long processDefinitionKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer startEventId,
      final long messageStartEventSubscriptionKey,
      final DirectBuffer variables,
      final long messageDeadline,
      final String tenantId) {
    return new MessageStartProcessInstanceRequestRecord()
        .setMessageKey(messageKey)
        .setMessageName(messageName)
        .setCorrelationKey(correlationKey)
        .setBusinessId(businessId)
        .setProcessDefinitionKey(processDefinitionKey)
        .setBpmnProcessId(bpmnProcessId)
        .setStartEventId(startEventId)
        .setMessageStartEventSubscriptionKey(messageStartEventSubscriptionKey)
        .setVariables(variables)
        .setMessageDeadline(messageDeadline)
        .setTenantId(tenantId);
  }

  /**
   * Handles the follow-up / result command based on the partition ID.
   *
   * <p>If the {@see receiverPartitionId} is similar to the sendPartitionId, then we will write a
   * follow-up command to the log, via the internal writers. If the partition id is different then
   * the command is sent over the wire to the receiverPartitionId.
   *
   * @param receiverPartitionId to which partition the command should be sent/written to
   * @param valueType the value type of the follow-up command
   * @param intent the intent of the follow-up command
   * @param record the record value of the follow-up command
   * @return always true
   */
  private boolean handleFollowUpCommandBasedOnPartition(
      final int receiverPartitionId,
      final ValueType valueType,
      final Intent intent,
      final UnifiedRecordValue record) {
    if (receiverPartitionId == senderPartition) {
      writers.command().appendNewCommand(intent, record);
    } else {
      writers
          .sideEffect()
          .appendSideEffect(
              () -> {
                interPartitionCommandSender.sendCommand(
                    receiverPartitionId, valueType, intent, record);
                return true;
              });
    }
    return true;
  }

  public void setWriters(final Writers writers) {
    this.writers = writers;
  }
}
