/*
 * Zeebe Broker Core
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
package io.zeebe.broker.subscription.command;

import io.zeebe.broker.clustering.base.topology.NodeInfo;
import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.clustering.base.topology.TopologyPartitionListenerImpl;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.SubscriptionUtil;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.ActorControl;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntArrayList;

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

  private final ClientTransport subscriptionClient;
  private final IntArrayList partitionIds = new IntArrayList();

  private int partitionId;
  private TopologyPartitionListenerImpl partitionListener;

  public SubscriptionCommandSender(
      final ClusterCfg clusterCfg, final ClientTransport subscriptionClient) {
    this.subscriptionClient = subscriptionClient;
    partitionIds.addAll(clusterCfg.getPartitionIds());
  }

  public void init(
      final TopologyManager topologyManager, final ActorControl actor, final LogStream logStream) {
    this.partitionId = logStream.getPartitionId();

    this.partitionListener = new TopologyPartitionListenerImpl(actor);
    topologyManager.addTopologyPartitionListener(partitionListener);
  }

  public boolean openMessageSubscription(
      final long workflowInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey) {

    final int subscriptionPartitionId =
        SubscriptionUtil.getSubscriptionPartitionId(correlationKey, partitionIds.size());

    openMessageSubscriptionCommand.setSubscriptionPartitionId(subscriptionPartitionId);
    openMessageSubscriptionCommand.setWorkflowInstanceKey(workflowInstanceKey);
    openMessageSubscriptionCommand.setElementInstanceKey(elementInstanceKey);
    openMessageSubscriptionCommand.getMessageName().wrap(messageName);
    openMessageSubscriptionCommand.getCorrelationKey().wrap(correlationKey);

    return sendSubscriptionCommand(subscriptionPartitionId, openMessageSubscriptionCommand);
  }

  public boolean openWorkflowInstanceSubscription(
      final long workflowInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName) {

    final int workflowInstancePartitionId = Protocol.decodePartitionId(workflowInstanceKey);

    openWorkflowInstanceSubscriptionCommand.setSubscriptionPartitionId(partitionId);
    openWorkflowInstanceSubscriptionCommand.setWorkflowInstanceKey(workflowInstanceKey);
    openWorkflowInstanceSubscriptionCommand.setElementInstanceKey(elementInstanceKey);
    openWorkflowInstanceSubscriptionCommand.getMessageName().wrap(messageName);

    return sendSubscriptionCommand(
        workflowInstancePartitionId, openWorkflowInstanceSubscriptionCommand);
  }

  public boolean correlateWorkflowInstanceSubscription(
      final long workflowInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName,
      final DirectBuffer payload) {

    final int workflowInstancePartitionId = Protocol.decodePartitionId(workflowInstanceKey);

    correlateWorkflowInstanceSubscriptionCommand.setSubscriptionPartitionId(partitionId);
    correlateWorkflowInstanceSubscriptionCommand.setWorkflowInstanceKey(workflowInstanceKey);
    correlateWorkflowInstanceSubscriptionCommand.setElementInstanceKey(elementInstanceKey);
    correlateWorkflowInstanceSubscriptionCommand.getMessageName().wrap(messageName);
    correlateWorkflowInstanceSubscriptionCommand.getPayload().wrap(payload);

    return sendSubscriptionCommand(
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

    return sendSubscriptionCommand(subscriptionPartitionId, correlateMessageSubscriptionCommand);
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

    return sendSubscriptionCommand(subscriptionPartitionId, closeMessageSubscriptionCommand);
  }

  public boolean closeWorkflowInstanceSubscription(
      final long workflowInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer messageName) {

    final int workflowInstancePartitionId = Protocol.decodePartitionId(workflowInstanceKey);

    closeWorkflowInstanceSubscriptionCommand.setSubscriptionPartitionId(partitionId);
    closeWorkflowInstanceSubscriptionCommand.setWorkflowInstanceKey(workflowInstanceKey);
    closeWorkflowInstanceSubscriptionCommand.setElementInstanceKey(elementInstanceKey);
    closeWorkflowInstanceSubscriptionCommand.setMessageName(messageName);

    return sendSubscriptionCommand(
        workflowInstancePartitionId, closeWorkflowInstanceSubscriptionCommand);
  }

  private boolean sendSubscriptionCommand(
      final int receiverPartitionId, final BufferWriter command) {

    final Int2ObjectHashMap<NodeInfo> partitionLeaders = partitionListener.getPartitionLeaders();
    final NodeInfo partitionLeader = partitionLeaders.get(receiverPartitionId);
    if (partitionLeader == null) {
      // retry when no leader is known
      return true;
    }

    return subscriptionClient.getOutput().sendMessage(partitionLeader.getNodeId(), command);
  }

  public boolean hasPartitionIds() {
    return partitionIds != null;
  }
}
