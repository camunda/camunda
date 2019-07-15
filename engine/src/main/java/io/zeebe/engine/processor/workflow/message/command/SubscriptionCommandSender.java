/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.message.command;

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
 *    | Open Message |  | Open Workflow |       | Correlate Workflow |  | Correlate Message |       | Close Message |  | Close Workflow |
 *    | Subscription |  | Instance Sub  |       | Instance Sub       |  | Subscription      |       | Subscription  |  | Instance Sub   |
 *    +-------+------+  +------+--------+       +----------+---------+  +---------+---------+       +-------+-------+  +-------+--------+
 *            |                |                           |                      |                         |                  |
 * +----------+----------------v---------------------------v----------------------+-------------------------+------------------v----------+
 * |                                                                                                                                      |
 * |                                                   Workflow Instance Partition                                                        |
 * +--------------------------------------------------------------------------------------------------------------------------------------+
 * <pre>
 */
public class SubscriptionCommandSender {

  private final OpenMessageSubscriptionCommand openMessageSubscriptionCommand =
      new OpenMessageSubscriptionCommand();

  private final OpenWorkflowInstanceSubscriptionCommand openWorkflowInstanceSubscriptionCommand =
      new OpenWorkflowInstanceSubscriptionCommand();

  private final CorrelateWorkflowInstanceSubscriptionCommand
      correlateWorkflowInstanceSubscriptionCommand =
          new CorrelateWorkflowInstanceSubscriptionCommand();

  private final CorrelateMessageSubscriptionCommand correlateMessageSubscriptionCommand =
      new CorrelateMessageSubscriptionCommand();

  private final CloseMessageSubscriptionCommand closeMessageSubscriptionCommand =
      new CloseMessageSubscriptionCommand();

  private final CloseWorkflowInstanceSubscriptionCommand closeWorkflowInstanceSubscriptionCommand =
      new CloseWorkflowInstanceSubscriptionCommand();

  private final RejectCorrelateMessageSubscriptionCommand
      rejectCorrelateMessageSubscriptionCommand = new RejectCorrelateMessageSubscriptionCommand();
  private final PartitionCommandSender partitionCommandSender;
  private final int senderPartition;

  public SubscriptionCommandSender(
      int senderPartition, PartitionCommandSender partitionCommandSender) {
    this.senderPartition = senderPartition;
    this.partitionCommandSender = partitionCommandSender;
  }

  public boolean openMessageSubscription(
      final int subscriptionPartitionId,
      final long workflowInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey,
      final boolean closeOnCorrelate) {
    openMessageSubscriptionCommand.setSubscriptionPartitionId(subscriptionPartitionId);
    openMessageSubscriptionCommand.setWorkflowInstanceKey(workflowInstanceKey);
    openMessageSubscriptionCommand.setElementInstanceKey(elementInstanceKey);
    openMessageSubscriptionCommand.getMessageName().wrap(messageName);
    openMessageSubscriptionCommand.getCorrelationKey().wrap(correlationKey);
    openMessageSubscriptionCommand.setCloseOnCorrelate(closeOnCorrelate);

    return partitionCommandSender.sendCommand(
        subscriptionPartitionId, openMessageSubscriptionCommand);
  }

  public boolean openWorkflowInstanceSubscription(
      final long workflowInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName,
      final boolean closeOnCorrelate) {

    final int workflowInstancePartitionId = Protocol.decodePartitionId(workflowInstanceKey);

    openWorkflowInstanceSubscriptionCommand.setSubscriptionPartitionId(senderPartition);
    openWorkflowInstanceSubscriptionCommand.setWorkflowInstanceKey(workflowInstanceKey);
    openWorkflowInstanceSubscriptionCommand.setElementInstanceKey(elementInstanceKey);
    openWorkflowInstanceSubscriptionCommand.getMessageName().wrap(messageName);
    openWorkflowInstanceSubscriptionCommand.setCloseOnCorrelate(closeOnCorrelate);

    return partitionCommandSender.sendCommand(
        workflowInstancePartitionId, openWorkflowInstanceSubscriptionCommand);
  }

  public boolean correlateWorkflowInstanceSubscription(
      final long workflowInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName,
      final long messageKey,
      final DirectBuffer variables) {

    final int workflowInstancePartitionId = Protocol.decodePartitionId(workflowInstanceKey);

    correlateWorkflowInstanceSubscriptionCommand.setSubscriptionPartitionId(senderPartition);
    correlateWorkflowInstanceSubscriptionCommand.setWorkflowInstanceKey(workflowInstanceKey);
    correlateWorkflowInstanceSubscriptionCommand.setElementInstanceKey(elementInstanceKey);
    correlateWorkflowInstanceSubscriptionCommand.setMessageKey(messageKey);
    correlateWorkflowInstanceSubscriptionCommand.getMessageName().wrap(messageName);
    correlateWorkflowInstanceSubscriptionCommand.getVariables().wrap(variables);

    return partitionCommandSender.sendCommand(
        workflowInstancePartitionId, correlateWorkflowInstanceSubscriptionCommand);
  }

  public boolean correlateMessageSubscription(
      final int subscriptionPartitionId,
      final long workflowInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName) {

    correlateMessageSubscriptionCommand.setSubscriptionPartitionId(subscriptionPartitionId);
    correlateMessageSubscriptionCommand.setWorkflowInstanceKey(workflowInstanceKey);
    correlateMessageSubscriptionCommand.setElementInstanceKey(elementInstanceKey);
    correlateMessageSubscriptionCommand.getMessageName().wrap(messageName);

    return partitionCommandSender.sendCommand(
        subscriptionPartitionId, correlateMessageSubscriptionCommand);
  }

  public boolean closeMessageSubscription(
      final int subscriptionPartitionId,
      final long workflowInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName) {

    closeMessageSubscriptionCommand.setSubscriptionPartitionId(subscriptionPartitionId);
    closeMessageSubscriptionCommand.setWorkflowInstanceKey(workflowInstanceKey);
    closeMessageSubscriptionCommand.setElementInstanceKey(elementInstanceKey);
    closeMessageSubscriptionCommand.setMessageName(messageName);

    return partitionCommandSender.sendCommand(
        subscriptionPartitionId, closeMessageSubscriptionCommand);
  }

  public boolean closeWorkflowInstanceSubscription(
      final long workflowInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName) {

    final int workflowInstancePartitionId = Protocol.decodePartitionId(workflowInstanceKey);

    closeWorkflowInstanceSubscriptionCommand.setSubscriptionPartitionId(senderPartition);
    closeWorkflowInstanceSubscriptionCommand.setWorkflowInstanceKey(workflowInstanceKey);
    closeWorkflowInstanceSubscriptionCommand.setElementInstanceKey(elementInstanceKey);
    closeWorkflowInstanceSubscriptionCommand.setMessageName(messageName);

    return partitionCommandSender.sendCommand(
        workflowInstancePartitionId, closeWorkflowInstanceSubscriptionCommand);
  }

  public boolean rejectCorrelateMessageSubscription(
      final long workflowInstanceKey,
      final long elementInstanceKey,
      final long messageKey,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey) {

    final int workflowInstancePartitionId = Protocol.decodePartitionId(workflowInstanceKey);

    rejectCorrelateMessageSubscriptionCommand.setSubscriptionPartitionId(senderPartition);
    rejectCorrelateMessageSubscriptionCommand.setWorkflowInstanceKey(workflowInstanceKey);
    rejectCorrelateMessageSubscriptionCommand.setMessageKey(messageKey);
    rejectCorrelateMessageSubscriptionCommand.getMessageName().wrap(messageName);
    rejectCorrelateMessageSubscriptionCommand.getCorrelationKey().wrap(correlationKey);

    return partitionCommandSender.sendCommand(
        workflowInstancePartitionId, rejectCorrelateMessageSubscriptionCommand);
  }
}
