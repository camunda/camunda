/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.job;

import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.zeebe.gateway.EndpointManager;
import io.zeebe.gateway.Loggers;
import io.zeebe.gateway.ResponseMapper;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.PartitionIdIterator;
import io.zeebe.gateway.impl.broker.RequestDispatchStrategy;
import io.zeebe.gateway.impl.broker.RoundRobinDispatchStrategy;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManager;
import io.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

final class ActivateJobsHandler {

  private final Map<String, RequestDispatchStrategy> jobTypeToNextPartitionId =
      new ConcurrentHashMap<>();
  private final BrokerClient brokerClient;
  private final BrokerTopologyManager topologyManager;

  ActivateJobsHandler(final BrokerClient brokerClient) {
    this.brokerClient = brokerClient;
    this.topologyManager = brokerClient.getTopologyManager();
  }

  void activateJobs(
      final int partitionsCount,
      final BrokerActivateJobsRequest request,
      final int maxJobsToActivate,
      final String type,
      final Consumer<ActivateJobsResponse> onResponse,
      final Consumer<Integer> onCompleted) {
    activateJobs(
        request,
        partitionIdIteratorForType(type, partitionsCount),
        maxJobsToActivate,
        type,
        onResponse,
        onCompleted);
  }

  private void activateJobs(
      final BrokerActivateJobsRequest request,
      final PartitionIdIterator partitionIdIterator,
      final int remainingAmount,
      final String jobType,
      final Consumer<ActivateJobsResponse> onResponse,
      final Consumer<Integer> onCompleted) {
    activateJobs(
        request, partitionIdIterator, remainingAmount, jobType, onResponse, onCompleted, false);
  }

  private void activateJobs(
      final BrokerActivateJobsRequest request,
      final PartitionIdIterator partitionIdIterator,
      final int remainingAmount,
      final String jobType,
      final Consumer<ActivateJobsResponse> onResponse,
      final Consumer<Integer> onCompleted,
      final boolean pollPrevPartition) {

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
                      onCompleted,
                      response.getResponse().getTruncated());
                } else {
                  logErrorResponse(partitionIdIterator, jobType, error);
                  activateJobs(
                      request,
                      partitionIdIterator,
                      remainingAmount,
                      jobType,
                      onResponse,
                      onCompleted);
                }
              });
    } else {
      // enough jobs activated or no more partitions left to check
      onCompleted.accept(remainingAmount);
    }
  }

  private void logErrorResponse(
      final PartitionIdIterator partitionIdIterator, final String jobType, final Throwable error) {
    final StatusRuntimeException statusRuntimeException = EndpointManager.convertThrowable(error);
    if (statusRuntimeException.getStatus().getCode() != Code.RESOURCE_EXHAUSTED) {
      Loggers.GATEWAY_LOGGER.warn(
          "Failed to activate jobs for type {} from partition {}",
          jobType,
          partitionIdIterator.getCurrentPartitionId(),
          error);
    }
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
