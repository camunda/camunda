/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.scaling;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotRequest.DeleteSnapshotForBootstrapRequest;
import io.camunda.zeebe.broker.transport.snapshotapi.SnapshotBrokerRequest;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.gateway.impl.broker.request.scaling.BrokerPartitionBootstrappedRequest;
import io.camunda.zeebe.gateway.impl.broker.request.scaling.BrokerPartitionScaleUpRequest;
import io.camunda.zeebe.gateway.impl.broker.request.scaling.GetScaleUpProgress;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrokerClientPartitionScalingExecutor implements PartitionScalingChangeExecutor {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(BrokerClientPartitionScalingExecutor.class);
  private final BrokerClient brokerClient;
  private final ConcurrencyControl concurrencyControl;

  public BrokerClientPartitionScalingExecutor(
      final BrokerClient brokerClient, final ConcurrencyControl concurrencyControl) {
    this.brokerClient = brokerClient;
    this.concurrencyControl = concurrencyControl;
  }

  @Override
  public ActorFuture<Void> initiateScaleUp(final int desiredPartitionCount) {
    final var result = concurrencyControl.<Void>createFuture();

    brokerClient.sendRequestWithRetry(
        new BrokerPartitionScaleUpRequest(desiredPartitionCount),
        (key, response) -> {
          result.complete(null);
        },
        error -> {
          if (error instanceof final BrokerRejectionException rejection
              && rejection.getRejection().type() == RejectionType.ALREADY_EXISTS) {
            LOGGER.debug("Scale up request already succeeded before", rejection);
            result.complete(null);
          } else {
            result.completeExceptionally(error);
          }
        });

    return result;
  }

  @Override
  public ActorFuture<Void> awaitRedistributionCompletion(
      final int desiredPartitionCount,
      final Set<Integer> redistributedPartitions,
      final Duration timeout) {
    return awaitRedistributionProgress(desiredPartitionCount, redistributedPartitions)
        .andThen(ignored -> deleteSnapshot(), concurrencyControl);
  }

  @Override
  public ActorFuture<Void> notifyPartitionBootstrapped(final int partitionId) {
    final ActorFuture<BrokerResponse<ScaleRecord>> bootstrapFuture =
        concurrencyControl.createFuture();

    brokerClient
        .sendRequestWithRetry(new BrokerPartitionBootstrappedRequest(partitionId))
        .whenComplete(bootstrapFuture);
    return bootstrapFuture.andThen(
        response -> {
          LOGGER.debug("Received response from partition bootstrap request {}", response);
          if (response.isError()) {
            return CompletableActorFuture.completedExceptionally(
                new BrokerErrorException(response.getError()));
          } else if (response.isRejection()) {
            return CompletableActorFuture.completedExceptionally(
                new BrokerRejectionException(response.getRejection()));
          } else {
            return CompletableActorFuture.completed();
          }
        },
        concurrencyControl);
  }

  @Override
  public ActorFuture<RoutingState> getRoutingState() {
    final var result = concurrencyControl.<RoutingState>createFuture();
    brokerClient.sendRequestWithRetry(
        new GetScaleUpProgress(),
        (key, record) -> result.complete(RoutingStateConverter.fromScaleRecord(record)),
        result::completeExceptionally);
    return result;
  }

  private ActorFuture<Void> awaitRedistributionProgress(
      final int desiredPartitionCount, final Set<Integer> redistributedPartitions) {
    // awaits for deployment partition
    final var partitions =
        IntStream.rangeClosed(Protocol.DEPLOYMENT_PARTITION, desiredPartitionCount)
            .boxed()
            .toList();
    return CompletableActorFuture.traverseIgnoring(
        partitions,
        i -> awaitRedistributionProgress(desiredPartitionCount, redistributedPartitions, i),
        concurrencyControl);
  }

  private ActorFuture<Void> awaitRedistributionProgress(
      final int desiredPartitionCount,
      final Set<Integer> redistributedPartitions,
      final int partitionId) {
    final var result = concurrencyControl.<Void>createFuture();
    brokerClient.sendRequestWithRetry(
        new GetScaleUpProgress(partitionId),
        (key, response) -> {
          if (response.getDesiredPartitionCount() != desiredPartitionCount) {
            LOGGER.warn("Invalid GetScaleUpProgress response: got {}", response);
            result.completeExceptionally(
                new RuntimeException(
                    "Invalid GetScaleUpProgress response: expected to await redistribution completion for partition count %d, but got %d"
                        .formatted(desiredPartitionCount, response.getDesiredPartitionCount())));
            return;
          }
          final Set<Integer> currentlyRedistributedPartitions =
              new TreeSet<>(response.getRedistributedPartitions());
          if (currentlyRedistributedPartitions.containsAll(redistributedPartitions)) {
            result.complete(null);
          } else {
            final var missingPartitions = new TreeSet<>(redistributedPartitions);
            missingPartitions.removeAll(currentlyRedistributedPartitions);
            result.completeExceptionally(
                new RuntimeException(
                    "Redistribution not completed yet: waiting for these partitions: %s"
                        .formatted(missingPartitions)));
          }
        },
        error -> {
          if (error instanceof final BrokerRejectionException rejection
              && rejection.getRejection().type() == RejectionType.INVALID_ARGUMENT) {
            LOGGER.debug("Await redistribution request is invalid", rejection);
            result.complete(null);
          } else {
            result.completeExceptionally(error);
          }
        });

    return result;
  }

  private ActorFuture<Void> deleteSnapshot() {
    LOGGER.debug("Sending delete snapshot request for deployment partition");
    final var request =
        new SnapshotBrokerRequest(
            new DeleteSnapshotForBootstrapRequest(Protocol.DEPLOYMENT_PARTITION));
    final var future = concurrencyControl.<Void>createFuture();
    brokerClient.sendRequestWithRetry(
        request,
        (key, response) -> future.complete(null),
        error -> {
          if (error instanceof final BrokerRejectionException rejection
              && rejection.getRejection().type() == RejectionType.INVALID_ARGUMENT) {
            // complete the future, retrying will not fix the issue and no cluster change operation
            // can occur
            LOGGER.debug("Await redistribution request is invalid", rejection);
            future.complete(null);
          } else {
            future.completeExceptionally(error);
          }
        });
    return future;
  }
}
