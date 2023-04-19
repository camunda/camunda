/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.api.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.cmd.BrokerErrorException;
import io.camunda.zeebe.gateway.cmd.BrokerRejectionException;
import io.camunda.zeebe.gateway.cmd.BrokerResponseException;
import io.camunda.zeebe.gateway.cmd.IllegalBrokerResponseException;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.BrokerResponseConsumer;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerTopologyManager;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerRequest;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class StubbedBrokerClient implements BrokerClient {

  final BrokerTopologyManager topologyManager = new StubbedTopologyManager();
  private Consumer<String> jobsAvailableHandler;

  private final Map<Class<?>, RequestHandler> requestHandlers = new HashMap<>();

  private final List<BrokerRequest> brokerRequests = new ArrayList<>();

  public StubbedBrokerClient() {}

  @Override
  public Collection<ActorFuture<Void>> start() {
    return null;
  }

  @Override
  public void close() {}

  @Override
  public <T> CompletableFuture<BrokerResponse<T>> sendRequest(final BrokerRequest<T> request) {
    return sendRequestWithRetry(request);
  }

  @Override
  public <T> CompletableFuture<BrokerResponse<T>> sendRequest(
      final BrokerRequest<T> request, final Duration requestTimeout) {
    return sendRequestWithRetry(request);
  }

  @Override
  public <T> CompletableFuture<BrokerResponse<T>> sendRequestWithRetry(
      final BrokerRequest<T> request) {
    final CompletableFuture<BrokerResponse<T>> future = new CompletableFuture<>();
    sendRequestWithRetry(
        request,
        (key, response) ->
            future.complete(new BrokerResponse<>(response, Protocol.decodePartitionId(key), key)),
        future::completeExceptionally);
    return future;
  }

  @Override
  public <T> CompletableFuture<BrokerResponse<T>> sendRequestWithRetry(
      final BrokerRequest<T> request, final Duration requestTimeout) {
    final CompletableFuture<BrokerResponse<T>> result = new CompletableFuture<>();

    sendRequestWithRetry(
        request,
        (key, response) ->
            result.complete(new BrokerResponse<>(response, Protocol.decodePartitionId(key), key)),
        result::completeExceptionally);

    return result.orTimeout(requestTimeout.toNanos(), TimeUnit.NANOSECONDS);
  }

  @Override
  public <T> void sendRequestWithRetry(
      final BrokerRequest<T> request,
      final BrokerResponseConsumer<T> responseConsumer,
      final Consumer<Throwable> throwableConsumer) {
    brokerRequests.add(request);
    try {
      final RequestHandler requestHandler = requestHandlers.get(request.getClass());
      final BrokerResponse<T> response = requestHandler.handle(request);
      try {
        if (response.isResponse()) {
          responseConsumer.accept(response.getKey(), response.getResponse());
        } else if (response.isRejection()) {
          throwableConsumer.accept(new BrokerRejectionException(response.getRejection()));
        } else if (response.isError()) {
          throwableConsumer.accept(new BrokerErrorException(response.getError()));
        } else {
          throwableConsumer.accept(
              new IllegalBrokerResponseException(
                  "Expected broker response to be either response, rejection, or error, but is neither of them []"));
        }
      } catch (final RuntimeException e) {
        throwableConsumer.accept(new BrokerResponseException(e));
      }
    } catch (final Exception e) {
      throwableConsumer.accept(new BrokerResponseException(e));
    }
  }

  @Override
  public BrokerTopologyManager getTopologyManager() {
    return topologyManager;
  }

  @Override
  public void subscribeJobAvailableNotification(
      final String topic, final Consumer<String> handler) {
    jobsAvailableHandler = handler;
  }

  public <RequestT extends BrokerRequest<?>, ResponseT extends BrokerResponse<?>>
      void registerHandler(
          final Class<?> requestType, final RequestHandler<RequestT, ResponseT> requestHandler) {
    requestHandlers.put(requestType, requestHandler);
  }

  public void notifyJobsAvailable(final String type) {
    jobsAvailableHandler.accept(type);
  }

  public <T extends BrokerRequest<?>> T getSingleBrokerRequest() {
    assertThat(brokerRequests).hasSize(1);
    return (T) brokerRequests.get(0);
  }

  public List<BrokerRequest> getBrokerRequests() {
    return brokerRequests;
  }

  public interface RequestStub<
          RequestT extends BrokerRequest<?>, ResponseT extends BrokerResponse<?>>
      extends RequestHandler<RequestT, ResponseT> {
    void registerWith(StubbedBrokerClient gateway);
  }

  @FunctionalInterface
  public interface RequestHandler<
      RequestT extends BrokerRequest<?>, ResponseT extends BrokerResponse<?>> {
    ResponseT handle(RequestT request) throws Exception;
  }
}
