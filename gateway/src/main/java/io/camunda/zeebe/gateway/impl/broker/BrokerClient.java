/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.broker;

import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerTopologyManager;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerRequest;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface BrokerClient extends AutoCloseable {

  /**
   * Starts broker client and associated services.
   *
   * @return a collection of futures for each of the service, which will be completed when the
   *     corresponding service is started.
   */
  Collection<ActorFuture<Void>> start();

  @Override
  void close();

  /**
   * Sends a request to the partition if request specifies a partition, otherwise assign a partition
   * send it to it.
   *
   * @param request request to send
   * @return future which will be completed when a successful response from the broker is received.
   *     The future will be completed exceptionally on error or on receiving BrokerRejection.
   */
  <T> CompletableFuture<BrokerResponse<T>> sendRequest(BrokerRequest<T> request);

  /**
   * Sends a request to the partition if request specifies a partition, otherwise assign a partition
   * send it to it. The request times out after the specified requestTimeout.
   *
   * @param request request to send
   * @param requestTimeout timeout for the request
   * @return future which will be completed when a successful response from the broker is received.
   *     The future will be completed exceptionally on error or on receiving BrokerRejection.
   */
  <T> CompletableFuture<BrokerResponse<T>> sendRequest(
      BrokerRequest<T> request, Duration requestTimeout);

  /**
   * Sends a request to the partition if request specifies a partition, otherwise assign a partition
   * send it to it. If leader for that partition is not reachable the request will be resend until a
   * timeout.
   *
   * @param request request to send
   * @return future which will be completed when a successful response from the broker is
   *     received.The future will be completed exceptionally on error or on receiving
   *     BrokerRejection.
   */
  <T> CompletableFuture<BrokerResponse<T>> sendRequestWithRetry(BrokerRequest<T> request);

  /**
   * Sends a request to the partition if request specifies a partition, otherwise assign a partition
   * send it to it. If leader for that partition is not reachable the request will be resend until
   * the given requestTimeout.
   *
   * @param request request to send
   * @param requestTimeout timeout for the request
   * @return future which will be completed when a successful response from the broker is
   *     received.The future will be completed exceptionally on error or on receiving
   *     BrokerRejection.
   */
  <T> CompletableFuture<BrokerResponse<T>> sendRequestWithRetry(
      BrokerRequest<T> request, Duration requestTimeout);

  /**
   * Sends a request to the partition if request specifies a partition, otherwise assign a partition
   * send it to it. If leader for that partition is not reachable the request will be resend until a
   * timeout.
   *
   * @param request
   * @param responseConsumer consumer that will be invoked when a successful response is received
   * @param throwableConsumer consumer that will be invoked on errors
   */
  <T> void sendRequestWithRetry(
      BrokerRequest<T> request,
      BrokerResponseConsumer<T> responseConsumer,
      Consumer<Throwable> throwableConsumer);

  BrokerTopologyManager getTopologyManager();

  void subscribeJobAvailableNotification(String topic, Consumer<String> handler);
}
