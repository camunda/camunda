/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerResponseConsumer;
import io.camunda.zeebe.broker.client.api.BrokerResponseException;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public final class StubbedBrokerClient implements BrokerClient {

  final BrokerTopologyManager topologyManager = new StubbedTopologyManager();
  private final Map<String, Consumer<String>> jobsAvailableHandlers = new HashMap<>();

  private final Map<Class<?>, RequestHandler<?, ?>> requestHandlers = new HashMap<>();

  private final List<BrokerRequest<?>> brokerRequests = new ArrayList<>();

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
    ensurePartitionGroupSet(request);
    final CompletableFuture<BrokerResponse<T>> future = new CompletableFuture<>();
    brokerRequests.add(request);

    try {
      final RequestHandler requestHandler = requestHandlers.get(request.getClass());
      future.complete(requestHandler.handle(request));
    } catch (final TimeoutException e) {
      // Preserve timeout semantics so endpoint tests can assert gateway timeout mappings.
      future.completeExceptionally(e);
    } catch (final Exception e) {
      future.completeExceptionally(new BrokerResponseException(e));
    }

    return future;
  }

  @Override
  public <T> CompletableFuture<BrokerResponse<T>> sendRequestWithRetry(
      final BrokerRequest<T> request, final Duration requestTimeout) {
    return sendRequestWithRetry(request).orTimeout(requestTimeout.toNanos(), TimeUnit.NANOSECONDS);
  }

  @Override
  public <T> void sendRequestWithRetry(
      final BrokerRequest<T> request,
      final BrokerResponseConsumer<T> responseConsumer,
      final Consumer<Throwable> throwableConsumer) {
    ensurePartitionGroupSet(request);
    brokerRequests.add(request);
    try {
      final RequestHandler requestHandler = requestHandlers.get(request.getClass());
      final BrokerResponse<T> response = requestHandler.handle(request);
      try {
        if (response.isResponse()) {
          responseConsumer.accept(response.getKey(), response.getResponse());
        } else {
          throwableConsumer.accept(response.toException());
        }
      } catch (final RuntimeException e) {
        throwableConsumer.accept(new BrokerResponseException(e));
      }
    } catch (final TimeoutException e) {
      // Preserve timeout semantics so endpoint tests can assert gateway timeout mappings.
      throwableConsumer.accept(e);
    } catch (final Exception e) {
      throwableConsumer.accept(new BrokerResponseException(e));
    }
  }

  /**
   * Mirrors the guard in the production {@code BrokerRequestManager}, so tests using this stub
   * catch senders that forget to set the physical tenant instead of silently passing.
   */
  private static void ensurePartitionGroupSet(final BrokerRequest<?> request) {
    if (!request.hasPartitionGroup()) {
      throw new IllegalStateException(
          "Cannot send request '%s': no partition group (physical tenant) was set. Requests are not implicitly routed to the default tenant; call setPartitionGroup explicitly, even when targeting the default tenant."
              .formatted(request.getType()));
    }
  }

  @Override
  public BrokerTopologyManager getTopologyManager() {
    return topologyManager;
  }

  @Override
  public void subscribeJobAvailableNotification(
      final String topic, final Object subscriber, final Consumer<String> handler) {
    jobsAvailableHandlers.put(topic, handler);
  }

  public <RequestT extends BrokerRequest<?>, ResponseT extends BrokerResponse<?>>
      void registerHandler(
          final Class<?> requestType, final RequestHandler<RequestT, ResponseT> requestHandler) {
    requestHandlers.put(requestType, requestHandler);
  }

  /** Notifies on the legacy, prefix-less topic, as if raised by the default physical tenant. */
  public void notifyJobsAvailable(final String type) {
    notifyJobsAvailable("jobsAvailable", type);
  }

  public void notifyJobsAvailable(final String topic, final String type) {
    final var handler = jobsAvailableHandlers.get(topic);
    if (handler != null) {
      handler.accept(type);
    }
  }

  public <T extends BrokerRequest<?>> T getSingleBrokerRequest() {
    assertThat(brokerRequests).hasSize(1);
    return (T) brokerRequests.get(0);
  }

  public List<BrokerRequest<?>> getBrokerRequests() {
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
