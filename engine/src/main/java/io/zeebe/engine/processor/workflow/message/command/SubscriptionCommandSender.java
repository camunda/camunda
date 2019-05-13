/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor.workflow.message.command;

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
public interface SubscriptionCommandSender {
  boolean openMessageSubscription(
      final int subscriptionPartitionId,
      final long workflowInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey,
      final boolean closeOnCorrelate);

  boolean openWorkflowInstanceSubscription(
      final long workflowInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName,
      final boolean closeOnCorrelate);

  boolean correlateWorkflowInstanceSubscription(
      final long workflowInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName,
      long messageKey,
      final DirectBuffer variables);

  boolean correlateMessageSubscription(
      final int subscriptionPartitionId,
      final long workflowInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName);

  boolean closeMessageSubscription(
      final int subscriptionPartitionId,
      final long workflowInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName);

  boolean closeWorkflowInstanceSubscription(
      final long workflowInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName);

  boolean rejectCorrelateMessageSubscription(
      final long workflowInstanceKey,
      final long elementInstanceKey,
      final long messageKey,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey);
}
