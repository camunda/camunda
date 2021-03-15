/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.engine.impl;

import io.atomix.cluster.MemberId;
import io.atomix.core.Atomix;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.topology.TopologyPartitionListenerImpl;
import io.zeebe.broker.system.management.deployment.PushDeploymentRequest;
import io.zeebe.broker.system.management.deployment.PushDeploymentResponse;
import io.zeebe.engine.processing.deployment.distribute.DeploymentDistributor;
import io.zeebe.engine.state.mutable.MutableDeploymentState;
import io.zeebe.protocol.impl.encoding.ErrorResponse;
import io.zeebe.protocol.record.ErrorCode;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public final class DeploymentDistributorImpl implements DeploymentDistributor {

  public static final Duration PUSH_REQUEST_TIMEOUT = Duration.ofSeconds(15);
  public static final Duration RETRY_DELAY = Duration.ofMillis(100);
  private static final Logger LOG = Loggers.PROCESS_REPOSITORY_LOGGER;
  private static final String DEPLOYMENT_PUSH_TOPIC = "deployment";
  private final PushDeploymentResponse pushDeploymentResponse = new PushDeploymentResponse();

  private final ErrorResponse errorResponse = new ErrorResponse();

  private final TopologyPartitionListenerImpl partitionListener;
  private final ActorControl actor;
  private final Atomix atomix;

  public DeploymentDistributorImpl(
      final Atomix atomix,
      final TopologyPartitionListenerImpl partitionListener,
      final MutableDeploymentState deploymentsState,
      final ActorControl actor) {
    this.atomix = atomix;
    this.partitionListener = partitionListener;
    this.actor = actor;
  }

  @Override
  public ActorFuture<Void> pushDeploymentToPartition(
      final long key, final int partitionId, final DirectBuffer deploymentBuffer) {
    final var pushedFuture = new CompletableActorFuture<Void>();

    LOG.debug("Distribute deployment {} to partition {}.", key, partitionId);
    final PushDeploymentRequest pushRequest =
        new PushDeploymentRequest().deployment(deploymentBuffer).deploymentKey(key);

    actor.runDelayed(
        PUSH_REQUEST_TIMEOUT,
        () -> {
          final String topic = getDeploymentResponseTopic(pushRequest.deploymentKey(), partitionId);
          if (!pushedFuture.isDone()) {
            LOG.warn(
                "Failed to receive deployment response for partition {} (on topic '{}'). Retrying",
                partitionId,
                topic);

            sendPushDeploymentRequest(partitionId, pushedFuture, pushRequest);
          }
        });

    sendPushDeploymentRequest(partitionId, pushedFuture, pushRequest);

    return pushedFuture;
  }

  private void sendPushDeploymentRequest(
      final int partitionId,
      final CompletableActorFuture<Void> pushedFuture,
      final PushDeploymentRequest pushRequest) {
    final Int2IntHashMap currentPartitionLeaders = partitionListener.getPartitionLeaders();
    if (currentPartitionLeaders.containsKey(partitionId)) {
      final int leader = currentPartitionLeaders.get(partitionId);
      createResponseSubscription(pushRequest.deploymentKey(), partitionId, pushedFuture);
      pushDeploymentToPartition(leader, partitionId, pushRequest);
    }
  }

  private void pushDeploymentToPartition(
      final int partitionLeaderId, final int partition, final PushDeploymentRequest pushRequest) {
    pushRequest.partitionId(partition);
    final byte[] bytes = pushRequest.toBytes();
    final MemberId memberId = new MemberId(Integer.toString(partitionLeaderId));

    final CompletableFuture<byte[]> pushDeploymentFuture =
        atomix
            .getCommunicationService()
            .send(DEPLOYMENT_PUSH_TOPIC, bytes, memberId, PUSH_REQUEST_TIMEOUT);

    pushDeploymentFuture.whenComplete(
        (response, throwable) -> {
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
              handleErrorResponseOnPushDeploymentRequest(
                  partitionLeaderId, partition, pushRequest, responseBuffer);
            }
          }
        });
  }

  private void handleErrorResponseOnPushDeploymentRequest(
      final int partitionLeaderId,
      final int partition,
      final PushDeploymentRequest pushRequest,
      final DirectBuffer responseBuffer) {
    errorResponse.wrap(responseBuffer, 0, responseBuffer.capacity());

    final var errorCode = errorResponse.getErrorCode();
    if (errorCode == ErrorCode.PARTITION_LEADER_MISMATCH) {
      final int responsePartition = errorResponse.getErrorData().getInt(0, ByteOrder.LITTLE_ENDIAN);
      LOG.debug(
          "Received partition leader mismatch error from partition {} for deployment {}. Retrying.",
          responsePartition,
          pushRequest.deploymentKey());

    } else if (errorCode == ErrorCode.RESOURCE_EXHAUSTED) {
      LOG.warn(
          "Received rejected deployment push due to error of type {}: '{}'. Will be retried after a delay",
          errorCode.name(),
          BufferUtil.bufferAsString(errorResponse.getErrorData()));
      return;
    } else {
      LOG.warn(
          "Received rejected deployment push due to error of type {}: '{}'",
          errorCode.name(),
          BufferUtil.bufferAsString(errorResponse.getErrorData()));
    }

    handleRetry(partitionLeaderId, partition, pushRequest);
  }

  private void createResponseSubscription(
      final long deploymentKey,
      final int partitionId,
      final CompletableActorFuture<Void> distributedFuture) {
    final String topic = getDeploymentResponseTopic(deploymentKey, partitionId);

    if (atomix.getEventService().getSubscriptions(topic).isEmpty()) {
      LOG.trace("Setting up deployment subscription for topic {}", topic);
      atomix
          .getEventService()
          .subscribe(
              topic,
              (byte[] response) -> {
                LOG.trace("Receiving deployment response on topic {}", topic);
                final DirectBuffer responseBuffer = new UnsafeBuffer(response);

                if (pushDeploymentResponse.tryWrap(responseBuffer)) {
                  // might be completed due to retry
                  if (!distributedFuture.isDone()) {
                    distributedFuture.complete(null);
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
                return CompletableFuture.completedFuture(null);
              });
    }
  }

  private void handleRetry(
      final int partitionLeaderId, final int partition, final PushDeploymentRequest pushRequest) {
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

  public static String getDeploymentResponseTopic(final long deploymentKey, final int partitionId) {
    return String.format("deployment-response-%d-%d", deploymentKey, partitionId);
  }
}
