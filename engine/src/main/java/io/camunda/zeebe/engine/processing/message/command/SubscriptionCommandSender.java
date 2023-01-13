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
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
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

    if (subscriptionPartitionId == senderPartition) {
      writers.command().appendNewCommand(
          MessageSubscriptionIntent.CREATE,
          new MessageSubscriptionRecord()
              .setProcessInstanceKey(processInstanceKey)
              .setElementInstanceKey(elementInstanceKey)
              .setBpmnProcessId(bpmnProcessId)
              .setMessageKey(-1)
              .setMessageName(messageName)
              .setCorrelationKey(correlationKey)
              .setInterrupting(closeOnCorrelate));
    } else {
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
    return true;
  }

  public boolean openProcessMessageSubscription(
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName,
      final boolean closeOnCorrelate) {
    final int receiverPartitionId = Protocol.decodePartitionId(processInstanceKey);
    if (receiverPartitionId == senderPartition) {
      writers.command().appendNewCommand(
          ProcessMessageSubscriptionIntent.CREATE,
          new ProcessMessageSubscriptionRecord()
              .setSubscriptionPartitionId(senderPartition)
              .setProcessInstanceKey(processInstanceKey)
              .setElementInstanceKey(elementInstanceKey)
              .setMessageKey(-1)
              .setMessageName(messageName)
              .setInterrupting(closeOnCorrelate));
    } else {
    interPartitionCommandSender.sendCommand(
        receiverPartitionId,
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
    return true;
  }

  public boolean correlateProcessMessageSubscription(
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer messageName,
      final long messageKey,
      final DirectBuffer variables,
      final DirectBuffer correlationKey) {
    final int receiverPartitionId = Protocol.decodePartitionId(processInstanceKey);
    if (receiverPartitionId == senderPartition) {
      writers.command().appendNewCommand(
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
    } else {
      interPartitionCommandSender.sendCommand(
          receiverPartitionId,
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
    return true;
  }

  public boolean correlateMessageSubscription(
      final int subscriptionPartitionId,
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer messageName) {
    if (subscriptionPartitionId == senderPartition) {
      writers.command().appendNewCommand(
          MessageSubscriptionIntent.CORRELATE,
          new MessageSubscriptionRecord()
              .setProcessInstanceKey(processInstanceKey)
              .setElementInstanceKey(elementInstanceKey)
              .setBpmnProcessId(bpmnProcessId)
              .setMessageKey(-1)
              .setMessageName(messageName));
    } else {
      interPartitionCommandSender.sendCommand(
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
    return true;
  }

  public boolean closeMessageSubscription(
      final int subscriptionPartitionId,
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName) {
    if (subscriptionPartitionId == senderPartition) {
      writers.command().appendNewCommand(
          ProcessMessageSubscriptionIntent.DELETE,
          new MessageSubscriptionRecord()
              .setProcessInstanceKey(processInstanceKey)
              .setElementInstanceKey(elementInstanceKey)
              .setMessageKey(-1L)
              .setMessageName(messageName));
    } else {
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
    return true;
  }

  public boolean closeProcessMessageSubscription(
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName) {

    final int receiverPartitionId = Protocol.decodePartitionId(processInstanceKey);
    if (receiverPartitionId == senderPartition) {
      writers.command().appendNewCommand(
          ProcessMessageSubscriptionIntent.DELETE,
          new ProcessMessageSubscriptionRecord()
              .setSubscriptionPartitionId(senderPartition)
              .setProcessInstanceKey(processInstanceKey)
              .setElementInstanceKey(elementInstanceKey)
              .setMessageKey(-1)
              .setMessageName(messageName));
    } else {
      interPartitionCommandSender.sendCommand(
          receiverPartitionId,
          ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
          ProcessMessageSubscriptionIntent.DELETE,
          new ProcessMessageSubscriptionRecord()
              .setSubscriptionPartitionId(senderPartition)
              .setProcessInstanceKey(processInstanceKey)
              .setElementInstanceKey(elementInstanceKey)
              .setMessageKey(-1)
              .setMessageName(messageName));
    }

    return true;
  }

  public boolean rejectCorrelateMessageSubscription(
      final long processInstanceKey,
      final DirectBuffer bpmnProcessId,
      final long messageKey,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey) {

    final int receiverPartitionId = Protocol.decodePartitionId(processInstanceKey);
    if (receiverPartitionId == senderPartition) {
      writers.command().appendNewCommand(
          MessageSubscriptionIntent.REJECT,
          new MessageSubscriptionRecord()
              .setProcessInstanceKey(processInstanceKey)
              .setElementInstanceKey(-1L)
              .setBpmnProcessId(bpmnProcessId)
              .setMessageName(messageName)
              .setCorrelationKey(correlationKey)
              .setMessageKey(messageKey)
              .setInterrupting(false));
    } else {
      interPartitionCommandSender.sendCommand(
          receiverPartitionId,
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
    return true;
  }

  public void setWriters(final Writers writers) {
    this.writers = writers;
  }
}
