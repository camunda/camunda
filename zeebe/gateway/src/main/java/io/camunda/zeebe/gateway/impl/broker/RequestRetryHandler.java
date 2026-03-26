/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerResponseConsumer;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.NoTopologyAvailableException;
import io.camunda.zeebe.broker.client.api.RequestDispatchStrategy;
import io.camunda.zeebe.broker.client.api.RequestRetriesExhaustedException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.protocol.record.ErrorCode;
import java.net.ConnectException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * When a requests to a partition fails, request will be retried with a different partition until
 * all partitions are tried. The request is retried only for specific errors such as connection
 * errors or resource exhausted errors. The request is not retried for time outs.
 *
 * <p>Use carefully! Only certain requests can be retried on other partitions, and this class will
 * overwrite the specific partition previously assigned to a request!
 */
public final class RequestRetryHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(RequestRetryHandler.class);

  private final RequestDispatchStrategy dispatchStrategy;
  private final BrokerClient brokerClient;
  private final BrokerTopologyManager topologyManager;

  public RequestRetryHandler(
      final BrokerClient brokerClient, final BrokerTopologyManager topologyManager) {
    this(brokerClient, topologyManager, RequestDispatchStrategy.roundRobin());
  }

  public RequestRetryHandler(
      final BrokerClient brokerClient,
      final BrokerTopologyManager topologyManager,
      final RequestDispatchStrategy dispatchStrategy) {
    this.brokerClient = brokerClient;
    this.topologyManager = topologyManager;
    this.dispatchStrategy = dispatchStrategy;
  }

  public <BrokerResponseT> void sendRequest(
      final BrokerRequest<BrokerResponseT> request,
      final BrokerResponseConsumer<BrokerResponseT> responseConsumer,
      final Consumer<Throwable> throwableConsumer) {
    final Function<
            BrokerRequest<BrokerResponseT>, CompletableFuture<BrokerResponse<BrokerResponseT>>>
        requestSender = brokerClient::sendRequest;
    sendRequestInternal(request, requestSender, responseConsumer, throwableConsumer);
  }

  public <BrokerResponseT> void sendRequest(
      final BrokerRequest<BrokerResponseT> request,
      final BrokerResponseConsumer<BrokerResponseT> responseConsumer,
      final Consumer<Throwable> throwableConsumer,
      final Duration requestTimeout) {
    final Function<
            BrokerRequest<BrokerResponseT>, CompletableFuture<BrokerResponse<BrokerResponseT>>>
        requestSender = r -> brokerClient.sendRequest(r, requestTimeout);
    sendRequestInternal(request, requestSender, responseConsumer, throwableConsumer);
  }

  private <BrokerResponseT> void sendRequestInternal(
      final BrokerRequest<BrokerResponseT> request,
      final Function<
              BrokerRequest<BrokerResponseT>, CompletableFuture<BrokerResponse<BrokerResponseT>>>
          requestSender,
      final BrokerResponseConsumer<BrokerResponseT> responseConsumer,
      final Consumer<Throwable> throwableConsumer) {
    final var topology = topologyManager.getTopology();
    if (topology == null || topology.getPartitionsCount() == 0) {
      throwableConsumer.accept(new NoTopologyAvailableException());
      return;
    }

    // When the request specifies its own dispatch strategy (e.g., hash-based routing for business
    // ID or correlation key), we must respect that strategy and send the request to that specific
    // partition. Retrying on other partitions would violate the routing guarantee needed for
    // per-partition uniqueness validation.
    final var requestStrategy = request.requestDispatchStrategy();
    if (requestStrategy.isPresent()) {
      sendRequestToFixedPartition(
          request, requestSender, requestStrategy.get(), responseConsumer, throwableConsumer);
      return;
    }

    sendRequestWithRetry(
        request,
        requestSender,
        topology.getPartitionsCount(),
        new HashSet<>(),
        responseConsumer,
        throwableConsumer,
        new ArrayList<>());
  }

  /**
   * Sends a request to a fixed partition determined by the given dispatch strategy, without
   * retrying on other partitions. This is used when the request must be routed to a specific
   * partition (e.g., based on a business ID or correlation key hash) to preserve routing
   * guarantees.
   */
  private <BrokerResponseT> void sendRequestToFixedPartition(
      final BrokerRequest<BrokerResponseT> request,
      final Function<
              BrokerRequest<BrokerResponseT>, CompletableFuture<BrokerResponse<BrokerResponseT>>>
          requestSender,
      final RequestDispatchStrategy strategy,
      final BrokerResponseConsumer<BrokerResponseT> responseConsumer,
      final Consumer<Throwable> throwableConsumer) {
    try {
      request.setPartitionId(strategy.determinePartition(topologyManager));
    } catch (final Exception e) {
      throwableConsumer.accept(e);
      return;
    }
    requestSender
        .apply(request)
        .whenComplete(
            (response, error) -> {
              if (error == null) {
                responseConsumer.accept(response.getKey(), response.getResponse());
              } else {
                throwableConsumer.accept(error);
              }
            });
  }

  private <BrokerResponseT> void sendRequestWithRetry(
      final BrokerRequest<BrokerResponseT> request,
      final Function<
              BrokerRequest<BrokerResponseT>, CompletableFuture<BrokerResponse<BrokerResponseT>>>
          requestSender,
      final int partitionCount,
      final Set<Integer> triedPartitions,
      final BrokerResponseConsumer<BrokerResponseT> responseConsumer,
      final Consumer<Throwable> throwableConsumer,
      final Collection<Throwable> errors) {

    final int partitionId = determineNextPartition(triedPartitions, partitionCount);
    if (partitionId == BrokerClusterState.PARTITION_ID_NULL) {
      final var exception = new RequestRetriesExhaustedException();
      errors.forEach(exception::addSuppressed);
      throwableConsumer.accept(exception);
      return;
    }

    triedPartitions.add(partitionId);
    request.setPartitionId(partitionId);
    requestSender
        .apply(request)
        .whenComplete(
            (response, error) -> {
              if (error == null) {
                responseConsumer.accept(response.getKey(), response.getResponse());
              } else if (shouldRetryWithNextPartition(error)) {
                LOGGER.trace("Failed to create process on partition {}", partitionId, error);
                errors.add(error);
                sendRequestWithRetry(
                    request,
                    requestSender,
                    partitionCount,
                    triedPartitions,
                    responseConsumer,
                    throwableConsumer,
                    errors);
              } else {
                throwableConsumer.accept(error);
              }
            });
  }

  /**
   * Determines the next partition to try using the dispatch strategy, skipping partitions that have
   * already been tried. Uses the dispatch strategy (round-robin) for each attempt so that
   * concurrent retries scatter across partitions instead of all cascading to the same next
   * partition.
   */
  private int determineNextPartition(
      final Set<Integer> triedPartitions, final int partitionsCount) {
    final var seen = new HashSet<Integer>();
    while (seen.size() < partitionsCount) {
      final int partition = dispatchStrategy.determinePartition(topologyManager);
      if (partition == BrokerClusterState.PARTITION_ID_NULL) {
        return BrokerClusterState.PARTITION_ID_NULL;
      }
      if (!triedPartitions.contains(partition)) {
        return partition;
      }
      seen.add(partition);
    }
    return BrokerClusterState.PARTITION_ID_NULL;
  }

  private boolean shouldRetryWithNextPartition(final Throwable error) {
    if (error instanceof ConnectException) {
      return true;
    } else if (error instanceof BrokerErrorException) {
      final ErrorCode code = ((BrokerErrorException) error).getError().getCode();
      return code == ErrorCode.PARTITION_LEADER_MISMATCH || code == ErrorCode.RESOURCE_EXHAUSTED;
    }
    return false;
  }
}
