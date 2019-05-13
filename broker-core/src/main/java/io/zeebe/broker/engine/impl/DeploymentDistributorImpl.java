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
package io.zeebe.broker.engine.impl;

import io.atomix.cluster.MemberId;
import io.atomix.core.Atomix;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.topology.NodeInfo;
import io.zeebe.broker.clustering.base.topology.TopologyPartitionListenerImpl;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.broker.system.management.deployment.NotLeaderResponse;
import io.zeebe.broker.system.management.deployment.PushDeploymentRequest;
import io.zeebe.broker.system.management.deployment.PushDeploymentResponse;
import io.zeebe.engine.processor.workflow.deployment.distribute.DeploymentDistributor;
import io.zeebe.engine.processor.workflow.deployment.distribute.PendingDeploymentDistribution;
import io.zeebe.engine.state.deployment.DeploymentsState;
import io.zeebe.protocol.Protocol;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntArrayList;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class DeploymentDistributorImpl implements DeploymentDistributor {

  private static final Logger LOG = Loggers.WORKFLOW_REPOSITORY_LOGGER;
  public static final Duration PUSH_REQUEST_TIMEOUT = Duration.ofSeconds(15);
  public static final Duration RETRY_DELAY = Duration.ofMillis(100);

  private final PushDeploymentRequest pushDeploymentRequest = new PushDeploymentRequest();
  private final PushDeploymentResponse pushDeploymentResponse = new PushDeploymentResponse();

  private final NotLeaderResponse notLeaderResponse = new NotLeaderResponse();

  private final TopologyPartitionListenerImpl partitionListener;
  private final ActorControl actor;

  private final transient Long2ObjectHashMap<ActorFuture<Void>> pendingDeploymentFutures =
      new Long2ObjectHashMap<>();
  private final DeploymentsState deploymentsState;

  private final IntArrayList partitionsToDistributeTo;
  private final Atomix atomix;

  public DeploymentDistributorImpl(
      final ClusterCfg clusterCfg,
      final Atomix atomix,
      final TopologyPartitionListenerImpl partitionListener,
      final DeploymentsState deploymentsState,
      final ActorControl actor) {
    this.atomix = atomix;
    this.partitionListener = partitionListener;
    this.actor = actor;
    this.deploymentsState = deploymentsState;
    partitionsToDistributeTo = partitionsToDistributeTo(clusterCfg);
  }

  private IntArrayList partitionsToDistributeTo(final ClusterCfg clusterCfg) {
    final IntArrayList list = new IntArrayList();

    list.addAll(clusterCfg.getPartitionIds());
    list.removeInt(Protocol.DEPLOYMENT_PARTITION);

    return list;
  }

  public ActorFuture<Void> pushDeployment(
      final long key, final long position, final DirectBuffer buffer) {
    final ActorFuture<Void> pushedFuture = new CompletableActorFuture<>();

    final PendingDeploymentDistribution pendingDeploymentDistribution =
        new PendingDeploymentDistribution(buffer, position);
    deploymentsState.putPendingDeployment(key, pendingDeploymentDistribution);
    pendingDeploymentFutures.put(key, pushedFuture);

    pushDeploymentToPartitions(key);

    return pushedFuture;
  }

  public PendingDeploymentDistribution removePendingDeployment(final long key) {
    return deploymentsState.removePendingDeployment(key);
  }

  private void pushDeploymentToPartitions(final long key) {
    if (!partitionsToDistributeTo.isEmpty()) {
      deployOnMultiplePartitions(key);
    } else {
      LOG.trace("No other partitions to distribute deployment.");
      LOG.trace("Deployment finished.");
      pendingDeploymentFutures.remove(key).complete(null);
    }
  }

  private void deployOnMultiplePartitions(final long key) {
    LOG.trace("Distribute deployment to other partitions.");

    final PendingDeploymentDistribution pendingDeploymentDistribution =
        deploymentsState.getPendingDeployment(key);
    final DirectBuffer directBuffer = pendingDeploymentDistribution.getDeployment();
    pendingDeploymentDistribution.setDistributionCount(partitionsToDistributeTo.size());

    pushDeploymentRequest.reset();
    pushDeploymentRequest.deployment(directBuffer).deploymentKey(key);

    final IntArrayList modifiablePartitionsList = new IntArrayList();
    modifiablePartitionsList.addAll(partitionsToDistributeTo);

    distributeDeployment(modifiablePartitionsList);
  }

  private void distributeDeployment(final IntArrayList partitionsToDistribute) {
    final IntArrayList remainingPartitions =
        distributeDeploymentToPartitions(partitionsToDistribute);

    if (remainingPartitions.isEmpty()) {
      LOG.trace("Pushed deployment to all partitions");
      return;
    }

    actor.runDelayed(RETRY_DELAY, () -> distributeDeployment(remainingPartitions));
  }

  private IntArrayList distributeDeploymentToPartitions(final IntArrayList remainingPartitions) {
    final Int2ObjectHashMap<NodeInfo> currentPartitionLeaders =
        partitionListener.getPartitionLeaders();

    final Iterator<Integer> iterator = remainingPartitions.iterator();
    while (iterator.hasNext()) {
      final Integer partitionId = iterator.next();
      final NodeInfo leader = currentPartitionLeaders.get(partitionId);
      if (leader != null) {
        iterator.remove();
        pushDeploymentToPartition(leader.getNodeId(), partitionId);
      }
    }
    return remainingPartitions;
  }

  private void pushDeploymentToPartition(final int partitionLeaderId, final int partition) {
    pushDeploymentRequest.partitionId(partition);
    final byte[] bytes = pushDeploymentRequest.toBytes();

    final MemberId memberId = new MemberId(Integer.toString(partitionLeaderId));
    final CompletableFuture<byte[]> pushDeploymentFuture =
        atomix.getCommunicationService().send("deployment", bytes, memberId, PUSH_REQUEST_TIMEOUT);

    pushDeploymentFuture.whenComplete(
        (response, throwable) ->
            actor.call(
                () -> {
                  if (throwable == null) {
                    handleResponse(response, partitionLeaderId, partition);
                  } else {
                    LOG.warn(
                        "Failed to push deployment to node {} for partition {}",
                        partitionLeaderId,
                        partition,
                        throwable);
                    handleRetry(partitionLeaderId, partition);
                  }
                }));
  }

  private void handleResponse(byte[] response, int partitionLeaderId, int partition) {
    final DirectBuffer responseBuffer = new UnsafeBuffer(response);

    if (pushDeploymentResponse.tryWrap(responseBuffer)) {
      pushDeploymentResponse.wrap(responseBuffer);
      handlePushResponse();
    } else if (notLeaderResponse.tryWrap(responseBuffer)) {
      LOG.warn(
          "Node {} rejected deployment on partition {} as not leader",
          partitionLeaderId,
          partition);
      handleRetry(partitionLeaderId, partition);
    } else {
      LOG.warn(
          "Received unknown deployment response from node {} for partition {}",
          partitionLeaderId,
          partition);
      handleRetry(partitionLeaderId, partition);
    }
  }

  private void handleRetry(int partitionLeaderId, int partition) {
    LOG.debug(
        "Retry deployment to push to partition {} after {}ms", partition, RETRY_DELAY.toMillis());
    actor.runDelayed(
        RETRY_DELAY,
        () -> {
          final Int2ObjectHashMap<NodeInfo> partitionLeaders =
              partitionListener.getPartitionLeaders();
          final NodeInfo currentLeader = partitionLeaders.get(partition);
          if (currentLeader != null) {
            pushDeploymentToPartition(currentLeader.getNodeId(), partition);
          } else {
            pushDeploymentToPartition(partitionLeaderId, partition);
          }
        });
  }

  private void handlePushResponse() {
    final long deploymentKey = pushDeploymentResponse.deploymentKey();
    final PendingDeploymentDistribution pendingDeploymentDistribution =
        deploymentsState.getPendingDeployment(deploymentKey);

    final long remainingPartitions = pendingDeploymentDistribution.decrementCount();
    if (remainingPartitions == 0) {
      LOG.debug("Deployment pushed to all partitions successfully.");
      pendingDeploymentFutures.remove(deploymentKey).complete(null);
    } else {
      LOG.trace(
          "Deployment was pushed to partition {} successfully.",
          pushDeploymentResponse.partitionId());
    }
  }
}
