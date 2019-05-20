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
      int subscriptionPartitionId,
      long workflowInstanceKey,
      long elementInstanceKey,
      DirectBuffer messageName,
      DirectBuffer correlationKey,
      boolean closeOnCorrelate);

  boolean openWorkflowInstanceSubscription(
      long workflowInstanceKey,
      long elementInstanceKey,
      DirectBuffer messageName,
      boolean closeOnCorrelate);

  boolean correlateWorkflowInstanceSubscription(
      long workflowInstanceKey,
      long elementInstanceKey,
      DirectBuffer messageName,
      long messageKey,
      DirectBuffer variables);

  boolean correlateMessageSubscription(
      int subscriptionPartitionId,
      long workflowInstanceKey,
      long elementInstanceKey,
      DirectBuffer messageName);

  boolean closeMessageSubscription(
      int subscriptionPartitionId,
      long workflowInstanceKey,
      long elementInstanceKey,
      DirectBuffer messageName);

  boolean closeWorkflowInstanceSubscription(
      long workflowInstanceKey, long elementInstanceKey, DirectBuffer messageName);

  boolean rejectCorrelateMessageSubscription(
      long workflowInstanceKey,
      long elementInstanceKey,
      long messageKey,
      DirectBuffer messageName,
      DirectBuffer correlationKey);
}
