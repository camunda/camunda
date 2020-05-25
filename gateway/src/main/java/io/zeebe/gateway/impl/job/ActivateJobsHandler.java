/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.job;

import io.grpc.stub.StreamObserver;
import io.zeebe.gateway.Loggers;
import io.zeebe.gateway.RequestMapper;
import io.zeebe.gateway.ResponseMapper;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.RequestDispatchStrategy;
import io.zeebe.gateway.impl.broker.RoundRobinDispatchStrategy;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManager;
import io.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ActivateJobsHandler {

  private final Map<String, RequestDispatchStrategy> jobTypeToNextPartitionId =
      new ConcurrentHashMap<>();
  private final BrokerClient brokerClient;
  private final BrokerTopologyManager topologyManager;

  public ActivateJobsHandler(
      final BrokerClient brokerClient, final BrokerTopologyManager topologyManager) {
    this.brokerClient = brokerClient;
    this.topologyManager = topologyManager;
  }

  public void activateJobs(
      final int partitionsCount,
      final ActivateJobsRequest request,
      final StreamObserver<ActivateJobsResponse> responseObserver) {
    activateJobs(
        RequestMapper.toActivateJobsRequest(request),
        partitionIdIteratorForType(request.getType(), partitionsCount),
        request.getMaxJobsToActivate(),
        request.getType(),
        responseObserver);
  }

  private void activateJobs(
      final BrokerActivateJobsRequest request,
      final PartitionIdIterator partitionIdIterator,
      final int remainingAmount,
      final String jobType,
      final StreamObserver<ActivateJobsResponse> responseObserver) {
    activateJobs(request, partitionIdIterator, remainingAmount, jobType, responseObserver, false);
  }

  private void activateJobs(
      final BrokerActivateJobsRequest request,
      final PartitionIdIterator partitionIdIterator,
      final int remainingAmount,
      final String jobType,
      final StreamObserver<ActivateJobsResponse> responseObserver,
      final boolean pollPrevPartition) {
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
              try {
                responseObserver.onNext(grpcResponse);
              } catch (Exception e) {
                // An exception here usually mean the stream is closed
                Loggers.GATEWAY_LOGGER.warn(
                    "Expected to send activate jobs to the client, but encountered an exception",
                    e);
                return;
              }
            }

            activateJobs(
                request,
                partitionIdIterator,
                remainingAmount - jobsCount,
                jobType,
                responseObserver,
                response.getTruncated());
          },
          error -> {
            Loggers.GATEWAY_LOGGER.warn(
                "Failed to activate jobs for type {} from partition {}",
                jobType,
                partitionIdIterator.getCurrentPartitionId(),
                error);
            activateJobs(request, partitionIdIterator, remainingAmount, jobType, responseObserver);
          },
          response -> false);
    } else {
      // enough jobs activated or no more partitions left to check
      try {
        responseObserver.onCompleted();
      } catch (Exception e) {
        // Cannot close the stream. Nothing to do
        Loggers.GATEWAY_LOGGER.trace(
            "Expected complete activate jobs request successfully, but encountered error", e);
      }
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
