/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message.command;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
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
      final DirectBuffer bpmnProcessId,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey,
      final boolean closeOnCorrelate) {
    return handleFollowUpCommandBasedOnPartition(
        subscriptionPartitionId,
        ValueType.MESSAGE_SUBSCRIPTION,
        MessageSubscriptionIntent.CREATE,
        new MessageSubscriptionRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setBpmnProcessId(bpmnProcessId)
            .setMessageKey(-1)
            .setMessageName(messageName)
            .setCorrelationKey(correlationKey)
            .setInterrupting(closeOnCorrelate));
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
   */
  public void sendDirectOpenMessageSubscription(
      final int subscriptionPartitionId,
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey,
      final boolean closeOnCorrelate) {
    interPartitionCommandSender.sendCommand(
        subscriptionPartitionId,
        ValueType.MESSAGE_SUBSCRIPTION,
        MessageSubscriptionIntent.CREATE,
        new MessageSubscriptionRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setBpmnProcessId(bpmnProcessId)
            .setMessageKey(-1)
            .setMessageName(messageName)
            .setCorrelationKey(correlationKey)
            .setInterrupting(closeOnCorrelate));
  }

  public boolean openProcessMessageSubscription(
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName,
      final boolean closeOnCorrelate) {
    return handleFollowUpCommandBasedOnPartition(
        Protocol.decodePartitionId(processInstanceKey),
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
        ProcessMessageSubscriptionIntent.CREATE,
        new ProcessMessageSubscriptionRecord()
            .setSubscriptionPartitionId(senderPartition)
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setMessageKey(-1)
            .setMessageName(messageName)
            .setInterrupting(closeOnCorrelate));
  }

  public boolean correlateProcessMessageSubscription(
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer messageName,
      final long messageKey,
      final DirectBuffer variables,
      final DirectBuffer correlationKey) {
    return handleFollowUpCommandBasedOnPartition(
        Protocol.decodePartitionId(processInstanceKey),
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
        ProcessMessageSubscriptionIntent.CORRELATE,
        new ProcessMessageSubscriptionRecord()
            .setSubscriptionPartitionId(senderPartition)
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setBpmnProcessId(bpmnProcessId)
            .setMessageKey(messageKey)
            .setMessageName(messageName)
            .setVariables(variables)
            .setCorrelationKey(correlationKey));
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
   */
  public void sendDirectCorrelateProcessMessageSubscription(
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer messageName,
      final long messageKey,
      final DirectBuffer variables,
      final DirectBuffer correlationKey) {
    interPartitionCommandSender.sendCommand(
        Protocol.decodePartitionId(processInstanceKey),
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
        ProcessMessageSubscriptionIntent.CORRELATE,
        new ProcessMessageSubscriptionRecord()
            .setSubscriptionPartitionId(senderPartition)
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setBpmnProcessId(bpmnProcessId)
            .setMessageKey(messageKey)
            .setMessageName(messageName)
            .setVariables(variables)
            .setCorrelationKey(correlationKey));
  }

  public boolean correlateMessageSubscription(
      final int subscriptionPartitionId,
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer messageName) {
    return handleFollowUpCommandBasedOnPartition(
        subscriptionPartitionId,
        ValueType.MESSAGE_SUBSCRIPTION,
        MessageSubscriptionIntent.CORRELATE,
        new MessageSubscriptionRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setBpmnProcessId(bpmnProcessId)
            .setMessageKey(-1)
            .setMessageName(messageName));
  }

  public boolean closeMessageSubscription(
      final int subscriptionPartitionId,
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName) {
    return handleFollowUpCommandBasedOnPartition(
        subscriptionPartitionId,
        ValueType.MESSAGE_SUBSCRIPTION,
        MessageSubscriptionIntent.DELETE,
        new MessageSubscriptionRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setMessageKey(-1L)
            .setMessageName(messageName));
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
   */
  public void sendDirectCloseMessageSubscription(
      final int subscriptionPartitionId,
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName) {
    interPartitionCommandSender.sendCommand(
        subscriptionPartitionId,
        ValueType.MESSAGE_SUBSCRIPTION,
        MessageSubscriptionIntent.DELETE,
        new MessageSubscriptionRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setMessageKey(-1L)
            .setMessageName(messageName));
  }

  public boolean closeProcessMessageSubscription(
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName) {
    return handleFollowUpCommandBasedOnPartition(
        Protocol.decodePartitionId(processInstanceKey),
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
        ProcessMessageSubscriptionIntent.DELETE,
        new ProcessMessageSubscriptionRecord()
            .setSubscriptionPartitionId(senderPartition)
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setMessageKey(-1)
            .setMessageName(messageName));
  }

  public boolean rejectCorrelateMessageSubscription(
      final long processInstanceKey,
      final DirectBuffer bpmnProcessId,
      final long messageKey,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey) {
    return handleFollowUpCommandBasedOnPartition(
        Protocol.decodePartitionId(processInstanceKey),
        ValueType.MESSAGE_SUBSCRIPTION,
        MessageSubscriptionIntent.REJECT,
        new MessageSubscriptionRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(-1L)
            .setBpmnProcessId(bpmnProcessId)
            .setMessageName(messageName)
            .setCorrelationKey(correlationKey)
            .setMessageKey(messageKey)
            .setInterrupting(false));
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
