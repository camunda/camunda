/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import io.atomix.primitive.partition.PartitionId;
import io.camunda.zeebe.broker.client.api.BrokerClientMetricsDoc.AdditionalErrorCodes;
import io.camunda.zeebe.broker.client.api.BrokerClientRequestMetrics;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.BrokerResponseException;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.IllegalBrokerResponseException;
import io.camunda.zeebe.broker.client.api.NoTopologyAvailableException;
import io.camunda.zeebe.broker.client.api.PartitionInactiveException;
import io.camunda.zeebe.broker.client.api.PartitionNotFoundException;
import io.camunda.zeebe.broker.client.api.RequestDispatchStrategy;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.ClientRequest;
import io.camunda.zeebe.transport.ClientTransport;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import org.agrona.DirectBuffer;

final class BrokerRequestManager extends Actor {

  private static final TransportRequestSender SENDER_WITH_RETRY =
      (c, s, r, t) -> c.sendRequestWithRetry(s, BrokerRequestManager::responseValidation, r, t);
  private static final TransportRequestSender SENDER_WITHOUT_RETRY = ClientTransport::sendRequest;
  private final ClientTransport clientTransport;
  private final RequestDispatchStrategy dispatchStrategy;
  private final BrokerTopologyManager topologyManager;
  private final Duration requestTimeout;
  private final BrokerClientRequestMetrics metrics;

  BrokerRequestManager(
      final ClientTransport clientTransport,
      final BrokerTopologyManager topologyManager,
      final RequestDispatchStrategy dispatchStrategy,
      final Duration requestTimeout,
      final BrokerClientRequestMetrics metrics) {
    this.clientTransport = clientTransport;
    this.dispatchStrategy = dispatchStrategy;
    this.topologyManager = topologyManager;
    this.requestTimeout = requestTimeout;
    this.metrics = metrics;
  }

  private static boolean responseValidation(final DirectBuffer responseContent) {
    final ErrorResponseHandler errorHandler = new ErrorResponseHandler();
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    headerDecoder.wrap(responseContent, 0);

    if (errorHandler.handlesResponse(headerDecoder)) {
      errorHandler.wrap(
          responseContent,
          headerDecoder.encodedLength(),
          headerDecoder.blockLength(),
          headerDecoder.version());

      final ErrorCode errorCode = errorHandler.getErrorCode();
      // we only want to retry partition leader mismatch all other errors
      // should be directly returned
      return errorCode != ErrorCode.PARTITION_LEADER_MISMATCH;
    } else {
      return true;
    }
  }

  <T> CompletableFuture<BrokerResponse<T>> sendRequestWithRetry(final BrokerRequest<T> request) {
    return sendRequestWithRetry(request, requestTimeout);
  }

  <T> CompletableFuture<BrokerResponse<T>> sendRequest(final BrokerRequest<T> request) {
    return sendRequest(request, requestTimeout);
  }

  <T> CompletableFuture<BrokerResponse<T>> sendRequest(
      final BrokerRequest<T> request, final Duration timeout) {
    return sendRequestInternal(request, SENDER_WITHOUT_RETRY, timeout);
  }

  <T> CompletableFuture<BrokerResponse<T>> sendRequestWithRetry(
      final BrokerRequest<T> request, final Duration requestTimeout) {
    return sendRequestInternal(request, SENDER_WITH_RETRY, requestTimeout);
  }

  private <T> CompletableFuture<BrokerResponse<T>> sendRequestInternal(
      final BrokerRequest<T> request,
      final TransportRequestSender sender,
      final Duration requestTimeout) {
    final CompletableFuture<BrokerResponse<T>> responseFuture = new CompletableFuture<>();
    request.serializeValue();
    actor.run(() -> sendRequestInternal(request, responseFuture, sender, requestTimeout));
    return responseFuture;
  }

  private <T> void sendRequestInternal(
      final BrokerRequest<T> request,
      final CompletableFuture<BrokerResponse<T>> returnFuture,
      final TransportRequestSender sender,
      final Duration requestTimeout) {

    final BrokerAddressProvider nodeIdProvider;
    try {
      nodeIdProvider = determineBrokerNodeIdProvider(request);
    } catch (final PartitionNotFoundException e) {
      returnFuture.completeExceptionally(e);
      metrics.registerFailedRequest(
          request.getPartitionId(), request.getType(), AdditionalErrorCodes.PARTITION_NOT_FOUND);
      return;
    } catch (final NoTopologyAvailableException e) {
      returnFuture.completeExceptionally(e);
      metrics.registerFailedRequest(
          request.getPartitionId(), request.getType(), AdditionalErrorCodes.NO_TOPOLOGY);
      return;
    } catch (final PartitionInactiveException e) {
      returnFuture.completeExceptionally(e);
      metrics.registerFailedRequest(
          request.getPartitionId(), request.getType(), AdditionalErrorCodes.PARTITION_INACTIVE);
      return;
    }

    final ActorFuture<DirectBuffer> responseFuture =
        sender.send(clientTransport, nodeIdProvider, request, requestTimeout);
    final long startTime = System.currentTimeMillis();

    actor.runOnCompletion(
        responseFuture,
        (clientResponse, error) -> {
          RequestResult result = null;
          try {
            if (error == null) {
              final BrokerResponse<T> response = request.getResponse(clientResponse);

              result = handleResponse(response, returnFuture);
              if (result.wasProcessed()) {
                final long elapsedTime = System.currentTimeMillis() - startTime;
                metrics.registerSuccessfulRequest(
                    request.getPartitionId(), request.getType(), elapsedTime);
                return;
              }
            } else {
              returnFuture.completeExceptionally(error);
            }
          } catch (final RuntimeException e) {
            returnFuture.completeExceptionally(new BrokerResponseException(e));
          }

          registerFailure(request, result, error);
        });
  }

  private <T> void registerFailure(
      final BrokerRequest<T> request, final RequestResult result, final Throwable error) {
    if (result != null && result.getErrorCode() == ErrorCode.RESOURCE_EXHAUSTED) {
      return;
    }

    final Enum<?> code;

    if (result != null && result.getErrorCode() != ErrorCode.NULL_VAL) {
      code = Objects.requireNonNullElse(result.getErrorCode(), AdditionalErrorCodes.UNKNOWN);
    } else if (error != null && error.getClass().equals(TimeoutException.class)) {
      code = AdditionalErrorCodes.TIMEOUT;
    } else {
      code = AdditionalErrorCodes.UNKNOWN;
    }

    metrics.registerFailedRequest(request.getPartitionId(), request.getType(), code);
  }

  /**
   * Returns a successful RequestResult, if the request was successfully processed or rejected.
   * Otherwise, it returns a RequestResult with the returned error code or with {@link
   * ErrorCode#NULL_VAL} if something unexpected occurred.
   */
  private <T> RequestResult handleResponse(
      final BrokerResponse<T> response, final CompletableFuture<BrokerResponse<T>> responseFuture) {
    try {
      if (response.isResponse()) {
        responseFuture.complete(response);
        return RequestResult.processed();
      } else if (response.isRejection()) {
        responseFuture.completeExceptionally(new BrokerRejectionException(response.getRejection()));
        return RequestResult.processed();
      } else if (response.isError()) {
        responseFuture.completeExceptionally(new BrokerErrorException(response.getError()));
        return RequestResult.failed(response.getError().getCode());
      } else {
        responseFuture.completeExceptionally(
            new IllegalBrokerResponseException(
                "Expected broker response to be either response, rejection, or error, but is neither of them"));
      }
    } catch (final RuntimeException e) {
      responseFuture.completeExceptionally(new BrokerResponseException(e));
    }

    return RequestResult.failed(ErrorCode.NULL_VAL);
  }

  private BrokerAddressProvider determineBrokerNodeIdProvider(final BrokerRequest<?> request) {
    if (request.getBrokerId().isPresent()) {
      return new BrokerAddressProvider(clusterState -> request.getBrokerId().orElseThrow());
    } else if (request.addressesSpecificPartition()) {
      final BrokerClusterState topology = topologyManager.getTopology();
      if (topology != null && !topology.getPartitions().contains(request.getPartitionId())) {
        throw new PartitionNotFoundException(request.getPartitionId());
      }
      throwIfPartitionInactive(request.getFullPartitionId());
      // already know partition id
      return new BrokerAddressProvider(request.getFullPartitionId());
    } else if (request.requiresPartitionId()) {
      final var strategy = request.requestDispatchStrategy().orElse(dispatchStrategy);

      // select next partition id for request
      int partitionId = strategy.determinePartition(request.getPartitionGroup(), topologyManager);
      if (partitionId == BrokerClusterState.PARTITION_ID_NULL) {
        // could happen if the topology is not set yet, let's just try with partition 0 but we
        // should find a better solution
        // https://github.com/zeebe-io/zeebe/issues/2013
        partitionId = Protocol.DEPLOYMENT_PARTITION;
      }
      request.setPartitionId(partitionId);

      throwIfPartitionInactive(request.getFullPartitionId());

      return new BrokerAddressProvider(request.getFullPartitionId());
    } else {
      // random broker
      return new BrokerAddressProvider(request.getPartitionGroup());
    }
  }

  private void throwIfPartitionInactive(final PartitionId partitionId) {
    final BrokerClusterState topology = topologyManager.getTopology();
    if (topology == null) {
      throw new NoTopologyAvailableException();
    }

    final var inactiveNodes = topology.getInactiveNodesForPartition(partitionId);
    final var someNodesInactive = inactiveNodes != null && !inactiveNodes.isEmpty();
    final var noPartitionLeader =
        topology.getLeaderForPartition(partitionId) == BrokerClusterState.NODE_ID_NULL;
    if (someNodesInactive && noPartitionLeader) {
      throw new PartitionInactiveException(partitionId);
    }
  }

  private static class RequestResult {
    private final boolean processed;
    private final ErrorCode errorCode;

    RequestResult(final boolean processed, final ErrorCode errorCode) {
      this.processed = processed;
      this.errorCode = errorCode;
    }

    boolean wasProcessed() {
      return processed;
    }

    public ErrorCode getErrorCode() {
      return errorCode;
    }

    static RequestResult processed() {
      return new RequestResult(true, null);
    }

    static RequestResult failed(final ErrorCode code) {
      return new RequestResult(false, code);
    }
  }

  private interface TransportRequestSender {

    ActorFuture<DirectBuffer> send(
        ClientTransport transport,
        Supplier<String> nodeAddressSupplier,
        ClientRequest clientRequest,
        Duration timeout);
  }

  private class BrokerAddressProvider implements Supplier<String> {

    private final ToIntFunction<BrokerClusterState> nodeIdSelector;

    BrokerAddressProvider(final String partitionGroup) {
      this((state) -> state.getRandomBroker(partitionGroup));
    }

    BrokerAddressProvider(final PartitionId partitionId) {
      this(state -> state.getLeaderForPartition(partitionId));
    }

    BrokerAddressProvider(final ToIntFunction<BrokerClusterState> nodeIdSelector) {
      this.nodeIdSelector = nodeIdSelector;
    }

    @Override
    public String get() {
      final BrokerClusterState topology = topologyManager.getTopology();
      if (topology != null) {
        return topology.getBrokerAddress(nodeIdSelector.applyAsInt(topology));
      } else {
        return null;
      }
    }
  }
}
