/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.job;

import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.gateway.RequestMapper;
import io.camunda.zeebe.gateway.ResponseMapper;
import io.camunda.zeebe.gateway.cmd.BrokerErrorException;
import io.camunda.zeebe.gateway.cmd.BrokerRejectionException;
import io.camunda.zeebe.gateway.grpc.ServerStreamObserver;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.PartitionIdIterator;
import io.camunda.zeebe.gateway.impl.broker.RequestDispatchStrategy;
import io.camunda.zeebe.gateway.impl.broker.RoundRobinDispatchStrategy;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerTopologyManager;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.util.sched.ActorControl;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Iterates in round-robin fashion over partitions to activate jobs. Uses a map from job type to
 * partition-IDs to determine the next partition to use.
 */
public final class RoundRobinActivateJobsHandler
    implements ActivateJobsHandler, Consumer<ActorControl> {

  private final Map<String, RequestDispatchStrategy> jobTypeToNextPartitionId =
      new ConcurrentHashMap<>();
  private final BrokerClient brokerClient;
  private final BrokerTopologyManager topologyManager;

  private ActorControl actor;

  public RoundRobinActivateJobsHandler(final BrokerClient brokerClient) {
    this.brokerClient = brokerClient;
    topologyManager = brokerClient.getTopologyManager();
  }

  @Override
  public void accept(ActorControl actor) {
    this.actor = actor;
  }

  @Override
  public void activateJobs(
      final ActivateJobsRequest request,
      final ServerStreamObserver<ActivateJobsResponse> responseObserver) {
    final BrokerClusterState topology = brokerClient.getTopologyManager().getTopology();
    if (topology != null) {
      final int partitionsCount = topology.getPartitionsCount();
      activateJobs(
          partitionsCount,
          RequestMapper.toActivateJobsRequest(request),
          request.getMaxJobsToActivate(),
          request.getType(),
          responseObserver::onNext,
          responseObserver::onError,
          (remainingAmount, resourceExhaustedWasPresent) -> responseObserver.onCompleted());
    }
  }

  public void activateJobs(
      final int partitionsCount,
      final BrokerActivateJobsRequest request,
      final int maxJobsToActivate,
      final String type,
      final Consumer<ActivateJobsResponse> onResponse,
      final Consumer<Throwable> onError,
      final BiConsumer<Integer, Boolean> onCompleted) {
    activateJobs(
        request,
        partitionIdIteratorForType(type, partitionsCount),
        maxJobsToActivate,
        type,
        onResponse,
        onError,
        onCompleted);
  }

  private void activateJobs(
      final BrokerActivateJobsRequest request,
      final PartitionIdIterator partitionIdIterator,
      final int remainingAmount,
      final String jobType,
      final Consumer<ActivateJobsResponse> onResponse,
      final Consumer<Throwable> onError,
      final BiConsumer<Integer, Boolean> onCompleted) {
    activateJobs(
        request,
        partitionIdIterator,
        remainingAmount,
        jobType,
        onResponse,
        onError,
        onCompleted,
        false,
        false);
  }

  private void activateJobs(
      final BrokerActivateJobsRequest request,
      final PartitionIdIterator partitionIdIterator,
      final int remainingAmount,
      final String jobType,
      final Consumer<ActivateJobsResponse> onResponse,
      final Consumer<Throwable> onError,
      final BiConsumer<Integer, Boolean> onCompleted,
      final boolean pollPrevPartition,
      final boolean resourceExhaustedWasPresent) {

    if (remainingAmount > 0 && (pollPrevPartition || partitionIdIterator.hasNext())) {
      final int partitionId =
          pollPrevPartition
              ? partitionIdIterator.getCurrentPartitionId()
              : partitionIdIterator.next();

      // partitions to check and jobs to activate left
      request.setPartitionId(partitionId);
      request.setMaxJobsToActivate(remainingAmount);
      brokerClient
          .sendRequest(request)
          .whenComplete(
              (response, error) -> {
                if (error == null) {
                  final ActivateJobsResponse grpcResponse =
                      ResponseMapper.toActivateJobsResponse(
                          response.getKey(), response.getResponse());
                  final int jobsCount = grpcResponse.getJobsCount();
                  if (jobsCount > 0) {
                    onResponse.accept(grpcResponse);
                  }

                  activateJobs(
                      request,
                      partitionIdIterator,
                      remainingAmount - jobsCount,
                      jobType,
                      onResponse,
                      onError,
                      onCompleted,
                      response.getResponse().getTruncated(),
                      resourceExhaustedWasPresent);
                } else {
                  final boolean wasResourceExhausted = wasResourceExhausted(error);
                  if (isRejection(error)) {
                    onError.accept(error);
                    return;
                  } else if (!wasResourceExhausted) {
                    logErrorResponse(partitionIdIterator, jobType, error);
                  }

                  activateJobs(
                      request,
                      partitionIdIterator,
                      remainingAmount,
                      jobType,
                      onResponse,
                      onError,
                      onCompleted,
                      false,
                      wasResourceExhausted);
                }
              });
    } else {
      // enough jobs activated or no more partitions left to check
      onCompleted.accept(remainingAmount, resourceExhaustedWasPresent);
    }
  }

  private boolean isRejection(final Throwable error) {
    return error != null && BrokerRejectionException.class.isAssignableFrom(error.getClass());
  }

  private boolean wasResourceExhausted(final Throwable error) {
    if (error instanceof BrokerErrorException) {
      final BrokerErrorException brokerError = (BrokerErrorException) error;
      return brokerError.getError().getCode() == ErrorCode.RESOURCE_EXHAUSTED;
    }

    return false;
  }

  private void logErrorResponse(
      final PartitionIdIterator partitionIdIterator, final String jobType, final Throwable error) {
    Loggers.GATEWAY_LOGGER.warn(
        "Failed to activate jobs for type {} from partition {}",
        jobType,
        partitionIdIterator.getCurrentPartitionId(),
        error);
  }

  private PartitionIdIterator partitionIdIteratorForType(
      final String jobType, final int partitionsCount) {
    final RequestDispatchStrategy nextPartitionSupplier =
        jobTypeToNextPartitionId.computeIfAbsent(
            jobType, t -> new RoundRobinDispatchStrategy(topologyManager));
    return new PartitionIdIterator(
        nextPartitionSupplier.determinePartition(), partitionsCount, topologyManager);
  }
}
