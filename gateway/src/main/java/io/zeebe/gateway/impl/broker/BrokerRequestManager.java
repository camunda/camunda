/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.broker;

import io.zeebe.gateway.cmd.BrokerErrorException;
import io.zeebe.gateway.cmd.BrokerRejectionException;
import io.zeebe.gateway.cmd.BrokerResponseException;
import io.zeebe.gateway.cmd.ClientOutOfMemoryException;
import io.zeebe.gateway.cmd.ClientResponseException;
import io.zeebe.gateway.cmd.IllegalBrokerResponseException;
import io.zeebe.gateway.cmd.NoTopologyAvailableException;
import io.zeebe.gateway.cmd.PartitionNotFoundException;
import io.zeebe.gateway.impl.ErrorResponseHandler;
import io.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManagerImpl;
import io.zeebe.gateway.impl.broker.request.BrokerPublishMessageRequest;
import io.zeebe.gateway.impl.broker.request.BrokerRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.SubscriptionUtil;
import io.zeebe.protocol.record.ErrorCode;
import io.zeebe.protocol.record.MessageHeaderDecoder;
import io.zeebe.transport.ClientRequest;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import org.agrona.DirectBuffer;

final class BrokerRequestManager extends Actor {

  private static final TransportRequestSender SENDER_WITH_RETRY =
      (c, s, r, t) -> c.sendRequestWithRetry(s, BrokerRequestManager::responseValidation, r, t);
  private static final TransportRequestSender SENDER_WITHOUT_RETRY = ClientTransport::sendRequest;
  private final ClientTransport clientTransport;
  private final RequestDispatchStrategy dispatchStrategy;
  private final BrokerTopologyManagerImpl topologyManager;
  private final Duration requestTimeout;

  BrokerRequestManager(
      final ClientTransport clientTransport,
      final BrokerTopologyManagerImpl topologyManager,
      final RequestDispatchStrategy dispatchStrategy,
      final Duration requestTimeout) {
    this.clientTransport = clientTransport;
    this.dispatchStrategy = dispatchStrategy;
    this.topologyManager = topologyManager;
    this.requestTimeout = requestTimeout;
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
    return sendRequestWithRetry(request, this.requestTimeout);
  }

  <T> CompletableFuture<BrokerResponse<T>> sendRequest(final BrokerRequest<T> request) {
    return sendRequest(request, this.requestTimeout);
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
      return;
    }

    final ActorFuture<DirectBuffer> responseFuture =
        sender.send(clientTransport, nodeIdProvider, request, requestTimeout);

    if (responseFuture != null) {
      actor.runOnCompletion(
          responseFuture,
          (clientResponse, error) -> {
            try {
              if (error == null) {
                final BrokerResponse<T> response = request.getResponse(clientResponse);
                handleResponse(response, returnFuture);
              } else {
                returnFuture.completeExceptionally(error);
              }
            } catch (final RuntimeException e) {
              returnFuture.completeExceptionally(new ClientResponseException(e));
            }
          });
    } else {
      returnFuture.completeExceptionally(new ClientOutOfMemoryException());
    }
  }

  private <T> void handleResponse(
      final BrokerResponse<T> response, final CompletableFuture<BrokerResponse<T>> responseFuture) {
    try {
      if (response.isResponse()) {
        responseFuture.complete(response);
      } else if (response.isRejection()) {
        responseFuture.completeExceptionally(new BrokerRejectionException(response.getRejection()));
      } else if (response.isError()) {
        responseFuture.completeExceptionally(new BrokerErrorException(response.getError()));
      } else {
        responseFuture.completeExceptionally(
            new IllegalBrokerResponseException(
                "Expected broker response to be either response, rejection, or error, but is neither of them"));
      }
    } catch (final RuntimeException e) {
      responseFuture.completeExceptionally(new BrokerResponseException(e));
    }
  }

  private BrokerAddressProvider determineBrokerNodeIdProvider(final BrokerRequest<?> request) {
    if (request.addressesSpecificPartition()) {
      final BrokerClusterState topology = topologyManager.getTopology();
      if (topology != null && !topology.getPartitions().contains(request.getPartitionId())) {
        throw new PartitionNotFoundException();
      }
      // already know partition id
      return new BrokerAddressProvider(request.getPartitionId());
    } else if (request.requiresPartitionId()) {
      if (request instanceof BrokerPublishMessageRequest) {
        determinePartitionIdForPublishMessageRequest((BrokerPublishMessageRequest) request);
      } else {
        // select next partition id for request
        int partitionId = dispatchStrategy.determinePartition();
        if (partitionId == BrokerClusterState.PARTITION_ID_NULL) {
          // could happen if the topology is not set yet, let's just try with partition 0 but we
          // should find a better solution
          // https://github.com/zeebe-io/zeebe/issues/2013
          partitionId = Protocol.DEPLOYMENT_PARTITION;
        }
        request.setPartitionId(partitionId);
      }
      return new BrokerAddressProvider(request.getPartitionId());
    } else {
      // random broker
      return new BrokerAddressProvider();
    }
  }

  private void determinePartitionIdForPublishMessageRequest(
      final BrokerPublishMessageRequest request) {
    final BrokerClusterState topology = topologyManager.getTopology();
    if (topology != null) {
      final int partitionsCount = topology.getPartitionsCount();

      final int partitionId =
          SubscriptionUtil.getSubscriptionPartitionId(request.getCorrelationKey(), partitionsCount);

      request.setPartitionId(partitionId);
    } else {
      // should not happen as the the broker request manager fetches topology before publish message
      // request if not present
      throw new NoTopologyAvailableException(
          String.format(
              "Expected to pick partition for message with correlation key '%s', but no topology is available",
              request.getCorrelationKey()));
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

    BrokerAddressProvider() {
      this(BrokerClusterState::getRandomBroker);
    }

    BrokerAddressProvider(final int partitionId) {
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
