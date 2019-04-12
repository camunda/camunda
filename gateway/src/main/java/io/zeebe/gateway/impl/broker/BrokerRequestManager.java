/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gateway.impl.broker;

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
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.impl.SubscriptionUtil;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.ClientResponse;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;

public class BrokerRequestManager extends Actor {

  private final ClientOutput clientOutput;
  private final RequestDispatchStrategy dispatchStrategy;
  private final BrokerTopologyManagerImpl topologyManager;
  private final Duration requestTimeout;

  public BrokerRequestManager(
      ClientOutput clientOutput,
      BrokerTopologyManagerImpl topologyManager,
      RequestDispatchStrategy dispatchStrategy,
      Duration requestTimeout) {
    this.clientOutput = clientOutput;
    this.dispatchStrategy = dispatchStrategy;
    this.topologyManager = topologyManager;
    this.requestTimeout = requestTimeout;
  }

  private static boolean shouldRetryRequest(final DirectBuffer responseContent) {
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
      return errorCode == ErrorCode.PARTITION_LEADER_MISMATCH;
    } else {
      return false;
    }
  }

  public <T> ActorFuture<BrokerResponse<T>> sendRequest(BrokerRequest<T> request) {
    final ActorFuture<BrokerResponse<T>> responseFuture = new CompletableActorFuture<>();

    sendRequest(
        request,
        (response, error) -> {
          if (error == null) {
            responseFuture.complete(response);
          } else {
            responseFuture.completeExceptionally(error);
          }
        });

    return responseFuture;
  }

  public <T> void sendRequest(
      BrokerRequest<T> request,
      BrokerResponseConsumer<T> responseConsumer,
      Consumer<Throwable> throwableConsumer) {
    sendRequest(
        request,
        responseConsumer,
        rejection -> throwableConsumer.accept(new BrokerRejectionException(rejection)),
        error -> throwableConsumer.accept(new BrokerErrorException(error)),
        throwableConsumer);
  }

  private <T> void sendRequest(
      BrokerRequest<T> request,
      BrokerResponseConsumer<T> responseConsumer,
      Consumer<BrokerRejection> rejectionConsumer,
      Consumer<BrokerError> errorConsumer,
      Consumer<Throwable> throwableConsumer) {

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
          } catch (RuntimeException e) {
            throwableConsumer.accept(new BrokerResponseException(e));
          }
        });
  }

  private <T> void sendRequest(
      BrokerRequest<T> request, BiConsumer<BrokerResponse<T>, Throwable> responseConsumer) {
    request.serializeValue();
    actor.run(() -> sendRequestInternal(request, responseConsumer));
  }

  private <T> void sendRequestInternal(
      BrokerRequest<T> request, BiConsumer<BrokerResponse<T>, Throwable> responseConsumer) {
    final BrokerNodeIdProvider nodeIdProvider = determineBrokerNodeIdProvider(request);

    final ActorFuture<ClientResponse> responseFuture =
        clientOutput.sendRequestWithRetry(
            nodeIdProvider, BrokerRequestManager::shouldRetryRequest, request, requestTimeout);

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
            } catch (RuntimeException e) {
              responseConsumer.accept(null, new ClientResponseException(e));
            }
          });
    } else {
      responseConsumer.accept(null, new ClientOutOfMemoryException());
    }
  }

  private BrokerNodeIdProvider determineBrokerNodeIdProvider(BrokerRequest<?> request) {
    if (request.addressesSpecificPartition()) {
      // already know partition id
      return new BrokerNodeIdProvider(request.getPartitionId());
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
      return new BrokerNodeIdProvider(request.getPartitionId());
    } else {
      // random broker;
      return new BrokerNodeIdProvider();
    }
  }

  private void determinePartitionIdForPublishMessageRequest(BrokerPublishMessageRequest request) {
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

  private class BrokerNodeIdProvider implements Supplier<Integer> {
    private final Function<BrokerClusterState, Integer> nodeIdSelector;

    BrokerNodeIdProvider() {
      this(BrokerClusterState::getRandomBroker);
    }

    BrokerNodeIdProvider(final int partitionId) {
      this(state -> state.getLeaderForPartition(partitionId));
    }

    BrokerNodeIdProvider(final Function<BrokerClusterState, Integer> nodeIdSelector) {
      this.nodeIdSelector = nodeIdSelector;
    }

    @Override
    public Integer get() {
      final BrokerClusterState topology = topologyManager.getTopology();
      if (topology != null) {
        return nodeIdSelector.apply(topology);
      } else {
        return null;
      }
    }
  }
}
