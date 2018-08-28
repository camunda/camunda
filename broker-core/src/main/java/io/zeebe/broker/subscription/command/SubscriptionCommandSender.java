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

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.broker.clustering.base.topology.NodeInfo;
import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.clustering.base.topology.TopologyPartitionListenerImpl;
import io.zeebe.broker.system.management.topics.FetchCreatedTopicsRequest;
import io.zeebe.broker.system.management.topics.FetchCreatedTopicsResponse;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.protocol.impl.SubscriptionUtil;
import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntArrayList;

/**
 * Send commands via the subscription endpoint. The commands are send as single messages (instead of request-response).
 * To ensure that a command is received, each command has an ACK command which is send by the receiver.
 *
 * <pre>
 *+-------------------------------------------------------------------------------------------+
 *|                                  Message Partition                                        |
 *|                                                                                           |
 *+-----------^----------------+---------------------------+----------------------^-----------+
 *            |                |                           |                      |
 *    +-------+------+  +------+--------+       +----------+---------+  +---------+---------+
 *    | Open Message |  | Open Workflow |       | Correlate Workflow |  | Correlate Message |
 *    | Subscription |  | Instance Sub  |       | Instance Sub       |  | Subscription      |
 *    +-------+------+  +------+--------+       +----------+---------+  +---------+---------+
 *            |                |                           |                      |
 * +----------+----------------v---------------------------v----------------------+-----------+
 * |                                                                                          |
 * |                              Workflow Instance Partition                                 |
 * +------------------------------------------------------------------------------------------+
 * <pre>
 */
public class SubscriptionCommandSender {

  private final FetchCreatedTopicsRequest fetchCreatedTopicsRequest =
      new FetchCreatedTopicsRequest();

  private final FetchCreatedTopicsResponse fetchCreatedTopicsResponse =
      new FetchCreatedTopicsResponse();

  private final OpenMessageSubscriptionCommand openMessageSubscriptionCommand =
      new OpenMessageSubscriptionCommand();

  private final OpenWorkflowInstanceSubscriptionCommand openWorkflowInstanceSubscriptionCommand =
      new OpenWorkflowInstanceSubscriptionCommand();

  private final CorrelateWorkflowInstanceSubscriptionCommand
      correlateWorkflowInstanceSubscriptionCommand =
          new CorrelateWorkflowInstanceSubscriptionCommand();

  private final CorrelateMessageSubscriptionCommand correlateMessageSubscriptionCommand =
      new CorrelateMessageSubscriptionCommand();

  private final ClientTransport managementClient;
  private final ClientTransport subscriptionClient;

  private ActorControl actor;
  private String topicName;
  private int partitionId;

  private IntArrayList partitionIds;
  private TopologyPartitionListenerImpl partitionListener;

  public SubscriptionCommandSender(
      ClientTransport managementClient, ClientTransport subscriptionClient) {
    this.managementClient = managementClient;
    this.subscriptionClient = subscriptionClient;
  }

  public void init(TopologyManager topologyManager, ActorControl actor, LogStream logStream) {
    this.actor = actor;
    this.topicName = bufferAsString(logStream.getTopicName());
    this.partitionId = logStream.getPartitionId();

    this.partitionListener = new TopologyPartitionListenerImpl(actor);
    topologyManager.addTopologyPartitionListener(partitionListener);
  }

  public boolean openMessageSubscription(
      long workflowInstanceKey,
      long activityInstanceKey,
      DirectBuffer messageName,
      DirectBuffer correlationKey) {

    final int subscriptionPartitionId = getSubscriptionPartitionId(correlationKey);

    openMessageSubscriptionCommand.setSubscriptionPartitionId(subscriptionPartitionId);
    openMessageSubscriptionCommand.setWorkflowInstancePartitionId(partitionId);
    openMessageSubscriptionCommand.setWorkflowInstanceKey(workflowInstanceKey);
    openMessageSubscriptionCommand.setActivityInstanceKey(activityInstanceKey);
    openMessageSubscriptionCommand.getMessageName().wrap(messageName);
    openMessageSubscriptionCommand.getCorrelationKey().wrap(correlationKey);

    return sendSubscriptionCommand(subscriptionPartitionId, openMessageSubscriptionCommand);
  }

  private int getSubscriptionPartitionId(DirectBuffer correlationKey) {
    if (partitionIds == null) {
      throw new IllegalStateException("no partition ids available");
    }

    final int hashCode = SubscriptionUtil.getSubscriptionHashCode(correlationKey);
    final int index = Math.abs(hashCode % partitionIds.size());
    return partitionIds.getInt(index);
  }

  public boolean openWorkflowInstanceSubscription(
      int workflowInstancePartitionId,
      long workflowInstanceKey,
      long activityInstanceKey,
      DirectBuffer messageName) {

    openWorkflowInstanceSubscriptionCommand.setSubscriptionPartitionId(partitionId);
    openWorkflowInstanceSubscriptionCommand.setWorkflowInstancePartitionId(
        workflowInstancePartitionId);
    openWorkflowInstanceSubscriptionCommand.setWorkflowInstanceKey(workflowInstanceKey);
    openWorkflowInstanceSubscriptionCommand.setActivityInstanceKey(activityInstanceKey);
    openWorkflowInstanceSubscriptionCommand.getMessageName().wrap(messageName);

    return sendSubscriptionCommand(
        workflowInstancePartitionId, openWorkflowInstanceSubscriptionCommand);
  }

  public boolean correlateWorkflowInstanceSubscription(
      int workflowInstancePartitionId,
      long workflowInstanceKey,
      long activityInstanceKey,
      DirectBuffer messageName,
      DirectBuffer payload) {

    correlateWorkflowInstanceSubscriptionCommand.setSubscriptionPartitionId(partitionId);
    correlateWorkflowInstanceSubscriptionCommand.setWorkflowInstancePartitionId(
        workflowInstancePartitionId);
    correlateWorkflowInstanceSubscriptionCommand.setWorkflowInstanceKey(workflowInstanceKey);
    correlateWorkflowInstanceSubscriptionCommand.setActivityInstanceKey(activityInstanceKey);
    correlateWorkflowInstanceSubscriptionCommand.getMessageName().wrap(messageName);
    correlateWorkflowInstanceSubscriptionCommand.getPayload().wrap(payload);

    return sendSubscriptionCommand(
        workflowInstancePartitionId, correlateWorkflowInstanceSubscriptionCommand);
  }

  public boolean correlateMessageSubscription(
      int subscriptionPartitionId,
      long workflowInstanceKey,
      long activityInstanceKey,
      DirectBuffer messageName) {

    correlateMessageSubscriptionCommand.setSubscriptionPartitionId(subscriptionPartitionId);
    correlateMessageSubscriptionCommand.setWorkflowInstancePartitionId(partitionId);
    correlateMessageSubscriptionCommand.setWorkflowInstanceKey(workflowInstanceKey);
    correlateMessageSubscriptionCommand.setActivityInstanceKey(activityInstanceKey);
    correlateMessageSubscriptionCommand.getMessageName().wrap(messageName);

    return sendSubscriptionCommand(subscriptionPartitionId, correlateMessageSubscriptionCommand);
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

  public ActorFuture<Void> fetchCreatedTopics() {
    // the fetching will be removed when the partitions are known on startup
    final CompletableActorFuture<Void> onCompleted = new CompletableActorFuture<>();

    actor.runOnCompletion(
        sendFetchCreatedTopicsRequest(),
        (response, failure) -> {
          if (failure == null) {
            handleFetchCreatedTopicsResponse(response.getResponseBuffer());
            onCompleted.complete(null);
          } else {
            onCompleted.completeExceptionally(failure);
          }
        });

    return onCompleted;
  }

  private ActorFuture<ClientResponse> sendFetchCreatedTopicsRequest() {
    return managementClient
        .getOutput()
        .sendRequestWithRetry(
            partitionListener::getSystemPartitionLeaderId,
            b -> !fetchCreatedTopicsResponse.tryWrap(b),
            fetchCreatedTopicsRequest,
            Duration.ofSeconds(15));
  }

  private void handleFetchCreatedTopicsResponse(DirectBuffer response) {
    fetchCreatedTopicsResponse.wrap(response);
    fetchCreatedTopicsResponse
        .getTopics()
        .forEach(
            topic -> {
              if (topic.getTopicName().equals(topicName)) {
                partitionIds = topic.getPartitionIds();
              }
            });
  }
}
