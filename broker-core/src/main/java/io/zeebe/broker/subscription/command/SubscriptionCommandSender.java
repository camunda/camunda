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
import io.zeebe.broker.clustering.base.topology.TopologyPartitionListenerImpl;
import io.zeebe.broker.system.management.topics.FetchCreatedTopicsRequest;
import io.zeebe.broker.system.management.topics.FetchCreatedTopicsResponse;
import io.zeebe.protocol.impl.SubscriptionUtil;
import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.TransportMessage;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntArrayList;

public class SubscriptionCommandSender {

  private final FetchCreatedTopicsRequest fetchCreatedTopicsRequest =
      new FetchCreatedTopicsRequest();

  private final FetchCreatedTopicsResponse fetchCreatedTopicsResponse =
      new FetchCreatedTopicsResponse();

  private final OpenMessageSubscriptionCommand openMessageSubscriptionCommand =
      new OpenMessageSubscriptionCommand();

  private final CorrelateWorkflowInstanceSubscriptionCommand
      correlateWorkflowInstanceSubscriptionCommand =
          new CorrelateWorkflowInstanceSubscriptionCommand();

  private final TransportMessage subscriptionMessage = new TransportMessage();

  private final TopologyPartitionListenerImpl partitionListener;

  private final ActorControl actor;
  private final ClientTransport managementClient;
  private final ClientTransport subscriptionClient;
  private final String topicName;
  private final int partitionId;

  private IntArrayList partitionIds;

  public SubscriptionCommandSender(
      ActorControl actor,
      ClientTransport managementClient,
      ClientTransport subscriptionClient,
      String topicName,
      int partitionId) {

    this.actor = actor;
    this.managementClient = managementClient;
    this.subscriptionClient = subscriptionClient;
    this.topicName = topicName;
    this.partitionId = partitionId;

    this.partitionListener = new TopologyPartitionListenerImpl(actor);
  }

  public boolean openMessageSubscription(
      long workflowInstanceKey,
      long activityInstanceKey,
      DirectBuffer messageName,
      DirectBuffer correlationKey) {

    if (partitionIds == null) {
      throw new IllegalStateException("no partition ids available");
    }

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
    final int hashCode = SubscriptionUtil.getSubscriptionHashCode(correlationKey);
    final int index = Math.abs(hashCode % partitionIds.size());
    return partitionIds.getInt(index);
  }

  public boolean correlateWorkflowInstanceSubscription(
      int workflowInstancePartitionId,
      long workflowInstanceKey,
      long activityInstanceKey,
      DirectBuffer messageName,
      DirectBuffer payload) {

    correlateWorkflowInstanceSubscriptionCommand.setWorkflowInstancePartitionId(
        workflowInstancePartitionId);
    correlateWorkflowInstanceSubscriptionCommand.setWorkflowInstanceKey(workflowInstanceKey);
    correlateWorkflowInstanceSubscriptionCommand.setActivityInstanceKey(activityInstanceKey);
    correlateWorkflowInstanceSubscriptionCommand.getMessageName().wrap(messageName);
    correlateWorkflowInstanceSubscriptionCommand.getPayload().wrap(payload);
    subscriptionMessage.writer(correlateWorkflowInstanceSubscriptionCommand);

    return sendSubscriptionCommand(
        workflowInstancePartitionId, correlateWorkflowInstanceSubscriptionCommand);
  }

  private boolean sendSubscriptionCommand(
      final int receiverPartitionId, final BufferWriter command) {

    final Int2ObjectHashMap<NodeInfo> partitionLeaders = partitionListener.getPartitionLeaders();
    final NodeInfo partitionLeader = partitionLeaders.get(receiverPartitionId);
    if (partitionLeader == null) {
      // retry when no leader is known
      return false;
    }

    final SocketAddress subscriptionApiAddress = partitionLeader.getSubscriptionApiAddress();
    final RemoteAddress remoteAddress =
        subscriptionClient.registerRemoteAddress(subscriptionApiAddress);
    subscriptionMessage.remoteAddress(remoteAddress);
    subscriptionMessage.writer(command);

    return subscriptionClient.getOutput().sendMessage(subscriptionMessage);
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
    final SocketAddress systemPartitionLeader = partitionListener.getSystemPartitionLeader();
    final RemoteAddress remoteAddress =
        managementClient.registerRemoteAddress(systemPartitionLeader);

    return managementClient
        .getOutput()
        .sendRequestWithRetry(
            () -> remoteAddress,
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

  public TopologyPartitionListenerImpl getPartitionListener() {
    return partitionListener;
  }
}
