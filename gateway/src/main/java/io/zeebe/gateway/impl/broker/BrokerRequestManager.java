/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.broker;

import io.opentracing.Tracer;
import io.zeebe.gateway.cmd.BrokerErrorException;
import io.zeebe.gateway.cmd.BrokerRejectionException;
import io.zeebe.gateway.cmd.BrokerResponseException;
import io.zeebe.gateway.cmd.ClientOutOfMemoryException;
import io.zeebe.gateway.cmd.ClientResponseException;
import io.zeebe.gateway.cmd.IllegalBrokerResponseException;
import io.zeebe.gateway.cmd.NoTopologyAvailableException;
import io.zeebe.gateway.impl.ErrorResponseHandler;
import io.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManagerImpl;
import io.zeebe.gateway.impl.broker.request.BrokerPublishMessageRequest;
import io.zeebe.gateway.impl.broker.request.BrokerRequest;
import io.zeebe.gateway.impl.broker.response.BrokerError;
import io.zeebe.gateway.impl.broker.response.BrokerRejection;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.SubscriptionUtil;
import io.zeebe.protocol.record.ErrorCode;
import io.zeebe.protocol.record.MessageHeaderDecoder;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;

public final class BrokerRequestManager extends Actor {

  private final ClientTransport clientTransport;
  private final RequestDispatchStrategy dispatchStrategy;
  private final BrokerTopologyManagerImpl topologyManager;
  private final Duration requestTimeout;
  private final Tracer tracer;

  public BrokerRequestManager(
      final ClientTransport clientTransport,
      final BrokerTopologyManagerImpl topologyManager,
      final RequestDispatchStrategy dispatchStrategy,
      final Duration requestTimeout,
      final Tracer tracer) {
    this.clientTransport = clientTransport;
    this.dispatchStrategy = dispatchStrategy;
    this.topologyManager = topologyManager;
    this.requestTimeout = requestTimeout;
    this.tracer = tracer;
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

  public <T> ActorFuture<BrokerResponse<T>> sendRequest(final BrokerRequest<T> request) {
    return sendRequest(request, this.requestTimeout);
  }

  public <T> ActorFuture<BrokerResponse<T>> sendRequest(
      final BrokerRequest<T> request, final Duration requestTimeout) {
    final ActorFuture<BrokerResponse<T>> responseFuture = new CompletableActorFuture<>();

    sendRequest(
        request,
        (response, error) -> {
          if (error == null) {
            responseFuture.complete(response);
          } else {
            responseFuture.completeExceptionally(error);
          }
        },
        requestTimeout);

    return responseFuture;
  }

  public <T> void sendRequest(
      final BrokerRequest<T> request,
      final BrokerResponseConsumer<T> responseConsumer,
      final Consumer<Throwable> throwableConsumer) {
    sendRequest(request, responseConsumer, throwableConsumer, this.requestTimeout);
  }

  public <T> void sendRequest(
      final BrokerRequest<T> request,
      final BrokerResponseConsumer<T> responseConsumer,
      final Consumer<Throwable> throwableConsumer,
      final Duration requestTimeout) {
    sendRequest(
        request,
        responseConsumer,
        rejection -> throwableConsumer.accept(new BrokerRejectionException(rejection)),
        error -> throwableConsumer.accept(new BrokerErrorException(error)),
        throwableConsumer,
        requestTimeout);
  }

  private <T> void sendRequest(
      final BrokerRequest<T> request,
      final BrokerResponseConsumer<T> responseConsumer,
      final Consumer<BrokerRejection> rejectionConsumer,
      final Consumer<BrokerError> errorConsumer,
      final Consumer<Throwable> throwableConsumer,
      final Duration requestTimeout) {

    sendRequest(
        request,
        (response, error) -> {
          try {
            if (error == null) {
              if (response.isResponse()) {
                responseConsumer.accept(response.getKey(), response.getResponse());
              } else if (response.isRejection()) {
                rejectionConsumer.accept(response.getRejection());
              } else if (response.isError()) {
                errorConsumer.accept(response.getError());
              } else {
                throwableConsumer.accept(
                    new IllegalBrokerResponseException(
                        "Expected broker response to be either response, rejection, or error, but is neither of them"));
              }
            } else {
              throwableConsumer.accept(error);
            }
          } catch (final RuntimeException e) {
            throwableConsumer.accept(new BrokerResponseException(e));
          }
        },
        requestTimeout);
  }

  private <T> void sendRequest(
      final BrokerRequest<T> request,
      final BiConsumer<BrokerResponse<T>, Throwable> responseConsumer,
      final Duration requestTimeout) {
    request.serializeValue();
    actor.run(() -> sendRequestInternal(request, responseConsumer, requestTimeout));
  }

  private <T> void sendRequestInternal(
      final BrokerRequest<T> request,
      final BiConsumer<BrokerResponse<T>, Throwable> responseConsumer,
      final Duration requestTimeout) {
    final BrokerAddressProvider nodeIdProvider = determineBrokerNodeIdProvider(request);
    request.injectTrace(tracer);

    final ActorFuture<DirectBuffer> responseFuture =
        clientTransport.sendRequestWithRetry(
            nodeIdProvider, BrokerRequestManager::responseValidation, request, requestTimeout);

    if (responseFuture != null) {
      actor.runOnCompletion(
          responseFuture,
          (clientResponse, error) -> {
            try {
              if (error == null) {
                final BrokerResponse<T> response = request.getResponse(clientResponse);
                responseConsumer.accept(response, null);
              } else {
                responseConsumer.accept(null, error);
              }
            } catch (final RuntimeException e) {
              responseConsumer.accept(null, new ClientResponseException(e));
            }
          });
    } else {
      responseConsumer.accept(null, new ClientOutOfMemoryException());
    }
  }

  private BrokerAddressProvider determineBrokerNodeIdProvider(final BrokerRequest<?> request) {
    if (request.addressesSpecificPartition()) {
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

  private class BrokerAddressProvider implements Supplier<String> {
    private final Function<BrokerClusterState, Integer> nodeIdSelector;

    BrokerAddressProvider() {
      this(BrokerClusterState::getRandomBroker);
    }

    BrokerAddressProvider(final int partitionId) {
      this(state -> state.getLeaderForPartition(partitionId));
    }

    BrokerAddressProvider(final Function<BrokerClusterState, Integer> nodeIdSelector) {
      this.nodeIdSelector = nodeIdSelector;
    }

    @Override
    public String get() {
      final BrokerClusterState topology = topologyManager.getTopology();
      if (topology != null) {
        return topology.getBrokerAddress(nodeIdSelector.apply(topology));
      } else {
        return null;
      }
    }
  }
}
