/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl.broker;

import io.zeebe.gateway.Loggers;
import io.zeebe.gateway.cmd.BrokerErrorException;
import io.zeebe.gateway.cmd.NoTopologyAvailableException;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManager;
import io.zeebe.gateway.impl.broker.request.BrokerRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.record.ErrorCode;
import java.net.ConnectException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * When a requests to a partition fails, request will be retried with a different partition until
 * all partitions are tried. The request is retried only for specific errors such as connection
 * errors or resource exhausted errors. The request is not retried for time outs.
 */
public final class RequestRetryHandler {

  private final BrokerClient brokerClient;
  private final RequestDispatchStrategy roundRobinDispatchStrategy;
  private final BrokerTopologyManager topologyManager;

  public RequestRetryHandler(
      final BrokerClient brokerClient, final BrokerTopologyManager topologyManager) {
    this.brokerClient = brokerClient;
    roundRobinDispatchStrategy = new RoundRobinDispatchStrategy(topologyManager);
    this.topologyManager = topologyManager;
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

    sendRequestWithRetry(
        request,
        requestSender,
        partitionIdIteratorForType(topology.getPartitionsCount()),
        responseConsumer,
        throwableConsumer,
        new ArrayList<>());
  }

  private <BrokerResponseT> void sendRequestWithRetry(
      final BrokerRequest<BrokerResponseT> request,
      final Function<
              BrokerRequest<BrokerResponseT>, CompletableFuture<BrokerResponse<BrokerResponseT>>>
          requestSender,
      final PartitionIdIterator partitionIdIterator,
      final BrokerResponseConsumer<BrokerResponseT> responseConsumer,
      final Consumer<Throwable> throwableConsumer,
      final Collection<Throwable> errors) {

    if (partitionIdIterator.hasNext()) {
      final int partitionId = partitionIdIterator.next();

      // partitions to check
      request.setPartitionId(partitionId);
      requestSender
          .apply(request)
          .whenComplete(
              (response, error) -> {
                if (error == null) {
                  responseConsumer.accept(response.getKey(), response.getResponse());
                } else if (shouldRetryWithNextPartition(error)) {
                  Loggers.GATEWAY_LOGGER.trace(
                      "Failed to create process on partition {}",
                      partitionIdIterator.getCurrentPartitionId(),
                      error);
                  errors.add(error);
                  sendRequestWithRetry(
                      request,
                      requestSender,
                      partitionIdIterator,
                      responseConsumer,
                      throwableConsumer,
                      errors);
                } else {
                  throwableConsumer.accept(error);
                }
              });
    } else {
      // no partition left to check
      final var exception = new RequestRetriesExhaustedException();
      errors.forEach(exception::addSuppressed);
      throwableConsumer.accept(exception);
    }
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

  private PartitionIdIterator partitionIdIteratorForType(final int partitionsCount) {
    final int nextPartitionId = roundRobinDispatchStrategy.determinePartition();
    return new PartitionIdIterator(nextPartitionId, partitionsCount, topologyManager);
  }
}
