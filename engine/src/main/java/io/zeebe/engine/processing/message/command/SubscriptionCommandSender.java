/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.message.command;

import io.zeebe.protocol.Protocol;
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

  private final OpenMessageSubscriptionCommand openMessageSubscriptionCommand =
      new OpenMessageSubscriptionCommand();

  private final OpenProcessInstanceSubscriptionCommand openProcessInstanceSubscriptionCommand =
      new OpenProcessInstanceSubscriptionCommand();

  private final CorrelateProcessInstanceSubscriptionCommand
      correlateProcessInstanceSubscriptionCommand =
          new CorrelateProcessInstanceSubscriptionCommand();

  private final CorrelateMessageSubscriptionCommand correlateMessageSubscriptionCommand =
      new CorrelateMessageSubscriptionCommand();

  private final CloseMessageSubscriptionCommand closeMessageSubscriptionCommand =
      new CloseMessageSubscriptionCommand();

  private final CloseProcessInstanceSubscriptionCommand closeProcessInstanceSubscriptionCommand =
      new CloseProcessInstanceSubscriptionCommand();

  private final RejectCorrelateMessageSubscriptionCommand
      rejectCorrelateMessageSubscriptionCommand = new RejectCorrelateMessageSubscriptionCommand();
  private final PartitionCommandSender partitionCommandSender;
  private final int senderPartition;

  public SubscriptionCommandSender(
      final int senderPartition, final PartitionCommandSender partitionCommandSender) {
    this.senderPartition = senderPartition;
    this.partitionCommandSender = partitionCommandSender;
  }

  public boolean openMessageSubscription(
      final int subscriptionPartitionId,
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey,
      final boolean closeOnCorrelate) {
    openMessageSubscriptionCommand.setSubscriptionPartitionId(subscriptionPartitionId);
    openMessageSubscriptionCommand.setProcessInstanceKey(processInstanceKey);
    openMessageSubscriptionCommand.setElementInstanceKey(elementInstanceKey);
    openMessageSubscriptionCommand.getBpmnProcessId().wrap(bpmnProcessId);
    openMessageSubscriptionCommand.getMessageName().wrap(messageName);
    openMessageSubscriptionCommand.getCorrelationKey().wrap(correlationKey);
    openMessageSubscriptionCommand.setCloseOnCorrelate(closeOnCorrelate);

    return partitionCommandSender.sendCommand(
        subscriptionPartitionId, openMessageSubscriptionCommand);
  }

  public boolean openProcessInstanceSubscription(
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName,
      final boolean closeOnCorrelate) {

    final int processInstancePartitionId = Protocol.decodePartitionId(processInstanceKey);

    openProcessInstanceSubscriptionCommand.setSubscriptionPartitionId(senderPartition);
    openProcessInstanceSubscriptionCommand.setProcessInstanceKey(processInstanceKey);
    openProcessInstanceSubscriptionCommand.setElementInstanceKey(elementInstanceKey);
    openProcessInstanceSubscriptionCommand.getMessageName().wrap(messageName);
    openProcessInstanceSubscriptionCommand.setCloseOnCorrelate(closeOnCorrelate);

    return partitionCommandSender.sendCommand(
        processInstancePartitionId, openProcessInstanceSubscriptionCommand);
  }

  public boolean correlateProcessInstanceSubscription(
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer messageName,
      final long messageKey,
      final DirectBuffer variables,
      final DirectBuffer correlationKey) {

    final int processInstancePartitionId = Protocol.decodePartitionId(processInstanceKey);

    correlateProcessInstanceSubscriptionCommand.setSubscriptionPartitionId(senderPartition);
    correlateProcessInstanceSubscriptionCommand.setProcessInstanceKey(processInstanceKey);
    correlateProcessInstanceSubscriptionCommand.setElementInstanceKey(elementInstanceKey);
    correlateProcessInstanceSubscriptionCommand.getBpmnProcessId().wrap(bpmnProcessId);
    correlateProcessInstanceSubscriptionCommand.setMessageKey(messageKey);
    correlateProcessInstanceSubscriptionCommand.getMessageName().wrap(messageName);
    correlateProcessInstanceSubscriptionCommand.getVariables().wrap(variables);
    correlateProcessInstanceSubscriptionCommand.getCorrelationKey().wrap(correlationKey);

    return partitionCommandSender.sendCommand(
        processInstancePartitionId, correlateProcessInstanceSubscriptionCommand);
  }

  public boolean correlateMessageSubscription(
      final int subscriptionPartitionId,
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer messageName) {

    correlateMessageSubscriptionCommand.setSubscriptionPartitionId(subscriptionPartitionId);
    correlateMessageSubscriptionCommand.setProcessInstanceKey(processInstanceKey);
    correlateMessageSubscriptionCommand.setElementInstanceKey(elementInstanceKey);
    correlateMessageSubscriptionCommand.getBpmnProcessId().wrap(bpmnProcessId);
    correlateMessageSubscriptionCommand.getMessageName().wrap(messageName);

    return partitionCommandSender.sendCommand(
        subscriptionPartitionId, correlateMessageSubscriptionCommand);
  }

  public boolean closeMessageSubscription(
      final int subscriptionPartitionId,
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName) {

    closeMessageSubscriptionCommand.setSubscriptionPartitionId(subscriptionPartitionId);
    closeMessageSubscriptionCommand.setProcessInstanceKey(processInstanceKey);
    closeMessageSubscriptionCommand.setElementInstanceKey(elementInstanceKey);
    closeMessageSubscriptionCommand.setMessageName(messageName);

    return partitionCommandSender.sendCommand(
        subscriptionPartitionId, closeMessageSubscriptionCommand);
  }

  public boolean closeProcessInstanceSubscription(
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName) {

    final int processInstancePartitionId = Protocol.decodePartitionId(processInstanceKey);

    closeProcessInstanceSubscriptionCommand.setSubscriptionPartitionId(senderPartition);
    closeProcessInstanceSubscriptionCommand.setProcessInstanceKey(processInstanceKey);
    closeProcessInstanceSubscriptionCommand.setElementInstanceKey(elementInstanceKey);
    closeProcessInstanceSubscriptionCommand.setMessageName(messageName);

    return partitionCommandSender.sendCommand(
        processInstancePartitionId, closeProcessInstanceSubscriptionCommand);
  }

  public boolean rejectCorrelateMessageSubscription(
      final long processInstanceKey,
      final DirectBuffer bpmnProcessId,
      final long messageKey,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey) {

    final int processInstancePartitionId = Protocol.decodePartitionId(processInstanceKey);

    rejectCorrelateMessageSubscriptionCommand.setSubscriptionPartitionId(senderPartition);
    rejectCorrelateMessageSubscriptionCommand.setProcessInstanceKey(processInstanceKey);
    rejectCorrelateMessageSubscriptionCommand.getBpmnProcessId().wrap(bpmnProcessId);
    rejectCorrelateMessageSubscriptionCommand.setMessageKey(messageKey);
    rejectCorrelateMessageSubscriptionCommand.getMessageName().wrap(messageName);
    rejectCorrelateMessageSubscriptionCommand.getCorrelationKey().wrap(correlationKey);

    return partitionCommandSender.sendCommand(
        processInstancePartitionId, rejectCorrelateMessageSubscriptionCommand);
  }
}
