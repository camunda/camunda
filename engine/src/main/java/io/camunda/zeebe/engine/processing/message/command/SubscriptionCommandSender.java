/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message.command;

import io.camunda.zeebe.engine.transport.InterPartitionCommandSender;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
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
      final DirectBuffer tenantId,
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
            .setInterrupting(closeOnCorrelate)
            .setTenantId(tenantId));
    return true;
  }

  public boolean openProcessMessageSubscription(
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName,
      final DirectBuffer tenantId,
      final boolean closeOnCorrelate) {

    interPartitionCommandSender.sendCommand(
        Protocol.decodePartitionId(processInstanceKey),
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
        ProcessMessageSubscriptionIntent.CREATE,
        new ProcessMessageSubscriptionRecord()
            .setSubscriptionPartitionId(senderPartition)
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setMessageKey(-1)
            .setMessageName(messageName)
            .setTenantId(tenantId)
            .setInterrupting(closeOnCorrelate));
    return true;
  }

  public boolean correlateProcessMessageSubscription(
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer messageName,
      final long messageKey,
      final DirectBuffer variables,
      final DirectBuffer correlationKey,
      final DirectBuffer tenantId) {

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
            .setCorrelationKey(correlationKey)
            .setTenantId(tenantId));
    return true;
  }

  public boolean correlateMessageSubscription(
      final int subscriptionPartitionId,
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer messageName,
      final DirectBuffer tenantId) {
    interPartitionCommandSender.sendCommand(
        subscriptionPartitionId,
        ValueType.MESSAGE_SUBSCRIPTION,
        MessageSubscriptionIntent.CORRELATE,
        new MessageSubscriptionRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setBpmnProcessId(bpmnProcessId)
            .setMessageKey(-1)
            .setMessageName(messageName)
            .setTenantId(tenantId));
    return true;
  }

  public boolean closeMessageSubscription(
      final int subscriptionPartitionId,
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName,
      final DirectBuffer tenantId) {

    interPartitionCommandSender.sendCommand(
        subscriptionPartitionId,
        ValueType.MESSAGE_SUBSCRIPTION,
        MessageSubscriptionIntent.DELETE,
        new MessageSubscriptionRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setMessageKey(-1L)
            .setMessageName(messageName)
            .setTenantId(tenantId));
    return true;
  }

  public boolean closeProcessMessageSubscription(
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName,
      final DirectBuffer tenantId) {
    interPartitionCommandSender.sendCommand(
        Protocol.decodePartitionId(processInstanceKey),
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
        ProcessMessageSubscriptionIntent.DELETE,
        new ProcessMessageSubscriptionRecord()
            .setSubscriptionPartitionId(senderPartition)
            .setProcessInstanceKey(processInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setMessageKey(-1)
            .setMessageName(messageName)
            .setTenantId(tenantId));
    return true;
  }

  public boolean rejectCorrelateMessageSubscription(
      final long processInstanceKey,
      final DirectBuffer bpmnProcessId,
      final long messageKey,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey,
      final DirectBuffer tenantId) {

    interPartitionCommandSender.sendCommand(
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
            .setTenantId(tenantId)
            .setInterrupting(false));
    return true;
  }
}
