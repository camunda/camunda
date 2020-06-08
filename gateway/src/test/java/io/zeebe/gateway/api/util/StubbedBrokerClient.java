/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.api.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.cmd.BrokerErrorException;
import io.zeebe.gateway.cmd.BrokerRejectionException;
import io.zeebe.gateway.cmd.BrokerResponseException;
import io.zeebe.gateway.cmd.IllegalBrokerResponseException;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.BrokerResponseConsumer;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManager;
import io.zeebe.gateway.impl.broker.request.BrokerRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class StubbedBrokerClient implements BrokerClient {

  final BrokerTopologyManager topologyManager = new StubbedTopologyManager();
  private Consumer<String> jobsAvailableHandler;

  private final Map<Class<?>, RequestHandler> requestHandlers = new HashMap<>();

  private final List<BrokerRequest> brokerRequests = new ArrayList<>();

  public StubbedBrokerClient() {}

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
    brokerRequests.add(request);
    try {

      final RequestHandler requestHandler = requestHandlers.get(request.getClass());
      final BrokerResponse<T> response = requestHandler.handle(request);

      try {
        if (response.isResponse()) {
          return CompletableFuture.completedFuture(response);
        } else if (response.isRejection()) {
          return CompletableFuture.failedFuture(
              new BrokerRejectionException(response.getRejection()));
        } else if (response.isError()) {
          return CompletableFuture.failedFuture(new BrokerErrorException(response.getError()));
        } else {
          return CompletableFuture.failedFuture(
              new IllegalBrokerResponseException(
                  "Expected broker response to be either response, rejection, or error, but is neither of them"));
        }
      } catch (final RuntimeException e) {
        return CompletableFuture.failedFuture(new BrokerResponseException(e));
      }
    } catch (final Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public <T> CompletableFuture<BrokerResponse<T>> sendRequestWithRetry(
      final BrokerRequest<T> request, final Duration requestTimeout) {
    throw new UnsupportedOperationException("not implemented");
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
      if (response.isResponse()) {
        responseConsumer.accept(response.getKey(), response.getResponse());
      } else if (response.isRejection()) {
        throwableConsumer.accept(new BrokerRejectionException(response.getRejection()));
      } else if (response.isError()) {
        throwableConsumer.accept(new BrokerErrorException(response.getError()));
      } else {
        throwableConsumer.accept(
            new IllegalBrokerResponseException("Unknown response received: " + response));
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
    this.jobsAvailableHandler = handler;
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
