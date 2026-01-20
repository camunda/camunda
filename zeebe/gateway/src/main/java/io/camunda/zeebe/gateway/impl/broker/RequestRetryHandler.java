/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerResponseConsumer;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.NoTopologyAvailableException;
import io.camunda.zeebe.broker.client.api.RequestDispatchStrategy;
import io.camunda.zeebe.broker.client.api.RequestRetriesExhaustedException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.broker.client.impl.PartitionIdIterator;
import io.camunda.zeebe.protocol.record.ErrorCode;
import java.net.ConnectException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
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

  private final RequestDispatchStrategy roundRobinDispatchStrategy =
      RequestDispatchStrategy.roundRobin();

  private final BrokerClient brokerClient;
  private final BrokerTopologyManager topologyManager;

  public RequestRetryHandler(
      final BrokerClient brokerClient, final BrokerTopologyManager topologyManager) {
    this.brokerClient = brokerClient;
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
        partitionIdIteratorForType(request.getPartitionGroup(), topology.getPartitionsCount()),
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
                  LOGGER.trace(
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

  private PartitionIdIterator partitionIdIteratorForType(
      final String partitionGroup, final int partitionsCount) {
    final int nextPartitionId =
        roundRobinDispatchStrategy.determinePartition(partitionGroup, topologyManager);
    return new PartitionIdIterator(
        partitionGroup, nextPartitionId, partitionsCount, topologyManager);
  }
}
