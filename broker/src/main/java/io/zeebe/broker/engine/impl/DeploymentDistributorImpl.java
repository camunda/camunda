/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.engine.impl;

import io.atomix.cluster.MemberId;
import io.atomix.core.Atomix;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.topology.TopologyPartitionListenerImpl;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.broker.system.management.deployment.PushDeploymentRequest;
import io.zeebe.broker.system.management.deployment.PushDeploymentResponse;
import io.zeebe.engine.processor.workflow.deployment.distribute.DeploymentDistributor;
import io.zeebe.engine.processor.workflow.deployment.distribute.PendingDeploymentDistribution;
import io.zeebe.engine.state.deployment.DeploymentsState;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.encoding.ErrorResponse;
import io.zeebe.protocol.record.ErrorCode;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.IntArrayList;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class DeploymentDistributorImpl implements DeploymentDistributor {

  public static final Duration PUSH_REQUEST_TIMEOUT = Duration.ofSeconds(15);
  public static final Duration RETRY_DELAY = Duration.ofMillis(100);
  private static final Logger LOG = Loggers.WORKFLOW_REPOSITORY_LOGGER;
  private final PushDeploymentResponse pushDeploymentResponse = new PushDeploymentResponse();

  private final ErrorResponse errorResponse = new ErrorResponse();

  private final TopologyPartitionListenerImpl partitionListener;
  private final ActorControl actor;

  private final transient Long2ObjectHashMap<ActorFuture<Void>> pendingDeploymentFutures =
      new Long2ObjectHashMap<>();
  private final DeploymentsState deploymentsState;

  private final IntArrayList partitionsToDistributeTo;
  private final Atomix atomix;
  private final Map<String, IntArrayList> deploymentResponses = new HashMap<>();

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

  @Override
  public ActorFuture<Void> pushDeployment(
      final long key, final long position, final DirectBuffer buffer) {
    final ActorFuture<Void> pushedFuture = new CompletableActorFuture<>();
    final PendingDeploymentDistribution pendingDeploymentDistribution =
        new PendingDeploymentDistribution(buffer, position, partitionsToDistributeTo.size());

    deploymentsState.putPendingDeployment(key, pendingDeploymentDistribution);
    pendingDeploymentFutures.put(key, pushedFuture);

    pushDeploymentToPartitions(key);

    return pushedFuture;
  }

  @Override
  public PendingDeploymentDistribution removePendingDeployment(final long key) {
    return deploymentsState.removePendingDeployment(key);
  }

  private void pushDeploymentToPartitions(final long key) {
    if (!partitionsToDistributeTo.isEmpty()) {
      deployOnMultiplePartitions(key);
    } else {
      LOG.trace("No other partitions to distribute deployment {}. Deployment finished", key);
      pendingDeploymentFutures.remove(key).complete(null);
    }
  }

  private void deployOnMultiplePartitions(final long key) {
    LOG.trace("Distribute deployment {} to other partitions.", key);

    final PendingDeploymentDistribution pendingDeploymentDistribution =
        deploymentsState.getPendingDeployment(key);
    final DirectBuffer directBuffer = pendingDeploymentDistribution.getDeployment();

    final PushDeploymentRequest pushRequest =
        new PushDeploymentRequest().deployment(directBuffer).deploymentKey(key);

    final IntArrayList modifiablePartitionsList = new IntArrayList();
    modifiablePartitionsList.addAll(partitionsToDistributeTo);

    prepareToDistribute(modifiablePartitionsList, pushRequest);
  }

  private void prepareToDistribute(
      IntArrayList partitionsToDistributeTo, final PushDeploymentRequest pushRequest) {
    actor.runDelayed(
        PUSH_REQUEST_TIMEOUT,
        () -> {
          final String topic = getDeploymentResponseTopic(pushRequest.deploymentKey());
          final IntArrayList missingResponses = getPartitionResponses(topic);

          if (!missingResponses.isEmpty()) {
            LOG.warn(
                "Failed to receive deployment response for partitions {} (topic '{}'). Retrying",
                missingResponses,
                topic);

            prepareToDistribute(missingResponses, pushRequest);
          }
        });

    distributeDeployment(partitionsToDistributeTo, pushRequest);
  }

  private void distributeDeployment(
      final IntArrayList partitionsToDistribute, final PushDeploymentRequest pushRequest) {
    final IntArrayList remainingPartitions =
        distributeDeploymentToPartitions(partitionsToDistribute, pushRequest);

    if (remainingPartitions.isEmpty()) {
      LOG.debug("Pushed deployment {} to all partitions.", pushRequest.deploymentKey());
      return;
    }

    actor.runDelayed(RETRY_DELAY, () -> distributeDeployment(remainingPartitions, pushRequest));
  }

  private IntArrayList distributeDeploymentToPartitions(
      final IntArrayList remainingPartitions, final PushDeploymentRequest pushRequest) {
    final Int2IntHashMap currentPartitionLeaders = partitionListener.getPartitionLeaders();

    final Iterator<Integer> iterator = remainingPartitions.iterator();
    while (iterator.hasNext()) {
      final Integer partitionId = iterator.next();
      if (currentPartitionLeaders.containsKey(partitionId)) {
        final int leader = currentPartitionLeaders.get(partitionId);
        iterator.remove();
        pushDeploymentToPartition(leader, partitionId, pushRequest);
      }
    }
    return remainingPartitions;
  }

  private void pushDeploymentToPartition(
      final int partitionLeaderId, final int partition, final PushDeploymentRequest pushRequest) {
    pushRequest.partitionId(partition);
    final byte[] bytes = pushRequest.toBytes();
    final MemberId memberId = new MemberId(Integer.toString(partitionLeaderId));

    createResponseSubscription(pushRequest.deploymentKey(), pushRequest);
    final CompletableFuture<byte[]> pushDeploymentFuture =
        atomix.getCommunicationService().send("deployment", bytes, memberId, PUSH_REQUEST_TIMEOUT);

    pushDeploymentFuture.whenComplete(
        (response, throwable) ->
            actor.call(
                () -> {
                  if (throwable != null) {
                    LOG.warn(
                        "Failed to push deployment to node {} for partition {}",
                        partitionLeaderId,
                        partition,
                        throwable);
                    handleRetry(partitionLeaderId, partition, pushRequest);

                  } else {
                    final DirectBuffer responseBuffer = new UnsafeBuffer(response);
                    if (errorResponse.tryWrap(responseBuffer)) {
                      errorResponse.wrap(responseBuffer, 0, responseBuffer.capacity());

                      if (errorResponse.getErrorCode() == ErrorCode.PARTITION_LEADER_MISMATCH) {
                        final int responsePartition =
                            errorResponse.getErrorData().getInt(0, ByteOrder.LITTLE_ENDIAN);
                        LOG.debug(
                            "Received partition leader mismatch error from partition {} for deployment {}. Retrying.",
                            partition,
                            pushRequest.deploymentKey());

                      } else {
                        LOG.warn(
                            "Received rejected deployment push due to error of type {}: '{}'",
                            errorResponse.getErrorCode().name(),
                            BufferUtil.bufferAsString(errorResponse.getErrorData()));
                      }

                      handleRetry(partitionLeaderId, partition, pushRequest);
                    }
                  }
                }));
  }

  private void createResponseSubscription(
      final long deploymentKey, final PushDeploymentRequest pushRequest) {
    final String topic = getDeploymentResponseTopic(pushRequest.deploymentKey());

    if (atomix.getEventService().getSubscriptions(topic).isEmpty()) {
      LOG.trace("Setting up deployment subscription for topic {}", topic);
      atomix
          .getEventService()
          .subscribe(
              topic,
              (byte[] response) -> {
                final CompletableFuture future = new CompletableFuture();
                actor.call(
                    () -> {
                      LOG.trace("Receiving deployment response on topic {}", topic);

                      handleResponse(response, deploymentKey, topic);
                      future.complete(null);
                      return future;
                    });
                return future;
              });
    }
  }

  private void handleResponse(byte[] response, final long deploymentKey, String topic) {
    final DirectBuffer responseBuffer = new UnsafeBuffer(response);

    if (pushDeploymentResponse.tryWrap(responseBuffer)) {
      pushDeploymentResponse.wrap(responseBuffer);
      if (handlePushResponse()) {
        final IntArrayList missingResponses = getPartitionResponses(topic);
        missingResponses.removeInt(pushDeploymentResponse.partitionId());
      }
    } else if (errorResponse.tryWrap(responseBuffer)) {
      errorResponse.wrap(responseBuffer, 0, responseBuffer.capacity());
      LOG.warn(
          "Received rejected deployment push due to error of type {}: '{}'",
          errorResponse.getErrorCode().name(),
          BufferUtil.bufferAsString(errorResponse.getErrorData()));
    } else {
      LOG.warn("Received unknown deployment response on topic {}", topic);
    }
  }

  private void handleRetry(
      int partitionLeaderId, int partition, final PushDeploymentRequest pushRequest) {
    LOG.trace("Retry deployment push to partition {} after {}", partition, RETRY_DELAY);

    actor.runDelayed(
        RETRY_DELAY,
        () -> {
          final Int2IntHashMap partitionLeaders = partitionListener.getPartitionLeaders();
          if (partitionLeaders.containsKey(partition)) {
            pushDeploymentToPartition(partitionLeaders.get(partition), partition, pushRequest);
          } else {
            pushDeploymentToPartition(partitionLeaderId, partition, pushRequest);
          }
        });
  }

  private boolean handlePushResponse() {
    final long deploymentKey = pushDeploymentResponse.deploymentKey();
    final PendingDeploymentDistribution pendingDeploymentDistribution =
        deploymentsState.getPendingDeployment(deploymentKey);
    final boolean pendingDeploymentExists = pendingDeploymentDistribution != null;

    if (pendingDeploymentExists) {
      final long remainingPartitions = pendingDeploymentDistribution.decrementCount();
      deploymentsState.putPendingDeployment(deploymentKey, pendingDeploymentDistribution);

      LOG.trace(
          "Deployment {} was pushed to partition {} successfully.",
          deploymentKey,
          pushDeploymentResponse.partitionId());

      if (remainingPartitions == 0) {
        LOG.debug("Deployment {} distributed to all partitions successfully.", deploymentKey);
        pendingDeploymentFutures.remove(deploymentKey).complete(null);
      }
    } else {
      LOG.trace(
          "Ignoring unexpected push deployment response for deployment key {}", deploymentKey);
    }

    return pendingDeploymentExists;
  }

  public static String getDeploymentResponseTopic(final long deploymentKey) {
    return String.format("deployment-response-%d", deploymentKey);
  }

  private IntArrayList getPartitionResponses(final String topic) {
    return deploymentResponses.computeIfAbsent(
        topic,
        k -> {
          final IntArrayList responses = new IntArrayList();
          responses.addAll(partitionsToDistributeTo);
          return responses;
        });
  }
}
