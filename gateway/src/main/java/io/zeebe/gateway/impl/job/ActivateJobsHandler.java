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
import io.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class ActivateJobsHandler {

  private final Map<String, Integer> jobTypeToNextPartitionId = new HashMap<>();
  private final BrokerClient brokerClient;

  public ActivateJobsHandler(final BrokerClient brokerClient) {
    this.brokerClient = brokerClient;
  }

  public void activateJobs(
      final int partitionsCount,
      final BrokerActivateJobsRequest request,
      final int maxJobsToActivate,
      final String type,
      final Consumer<ActivateJobsResponse> onResponse,
      final BiConsumer<Integer, Boolean> onCompleted) {
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
      final BiConsumer<Integer, Boolean> onCompleted) {
    activateJobs(
        request,
        partitionIdIterator,
        remainingAmount,
        jobType,
        onResponse,
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
      brokerClient.sendRequest(
          request,
          (key, response) -> {
            final ActivateJobsResponse grpcResponse =
                ResponseMapper.toActivateJobsResponse(key, response);
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
                response.getTruncated(),
                resourceExhaustedWasPresent);
          },
          error -> {
            logErrorResponse(partitionIdIterator, jobType, error);

            final boolean wasResourceExhausted = wasResourceExhausted(error);

            activateJobs(
                request,
                partitionIdIterator,
                remainingAmount,
                jobType,
                onResponse,
                onCompleted,
                false,
                wasResourceExhausted);
          });
    } else {
      // enough jobs activated or no more partitions left to check
      jobTypeToNextPartitionId.put(jobType, partitionIdIterator.getCurrentPartitionId());
      onCompleted.accept(remainingAmount, resourceExhaustedWasPresent);
    }
  }

  private boolean wasResourceExhausted(final Throwable error) {
    final StatusRuntimeException statusRuntimeException = EndpointManager.convertThrowable(error);
    return statusRuntimeException.getStatus().getCode() == Code.RESOURCE_EXHAUSTED;
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
    final Integer nextPartitionId = jobTypeToNextPartitionId.computeIfAbsent(jobType, t -> 0);
    return new PartitionIdIterator(nextPartitionId, partitionsCount);
  }
}
