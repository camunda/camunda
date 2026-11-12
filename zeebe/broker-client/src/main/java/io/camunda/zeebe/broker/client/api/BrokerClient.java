/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
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
   * @return future which will be completed when a response, rejection, or error from the broker is
   *     received. The future will be completed exceptionally on request dispatch, transport, or
   *     response parsing errors.
   */
  <T> CompletableFuture<BrokerResponse<T>> sendRequest(BrokerRequest<T> request);

  /**
   * Sends a request to the partition if request specifies a partition, otherwise assign a partition
   * send it to it. The request times out after the specified requestTimeout.
   *
   * @param request request to send
   * @param requestTimeout timeout for the request
   * @return future which will be completed when a response, rejection, or error from the broker is
   *     received. The future will be completed exceptionally on request dispatch, transport, or
   *     response parsing errors.
   */
  <T> CompletableFuture<BrokerResponse<T>> sendRequest(
      BrokerRequest<T> request, Duration requestTimeout);

  /**
   * Sends a request to the partition if request specifies a partition, otherwise assign a partition
   * send it to it. If leader for that partition is not reachable the request will be resend until a
   * timeout.
   *
   * @param request request to send
   * @return future which will be completed when a response, rejection, or error from the broker is
   *     received. The future will be completed exceptionally on request dispatch, transport, or
   *     response parsing errors.
   */
  <T> CompletableFuture<BrokerResponse<T>> sendRequestWithRetry(BrokerRequest<T> request);

  /**
   * Sends a request to the partition if request specifies a partition, otherwise assign a partition
   * send it to it. If leader for that partition is not reachable the request will be resend until
   * the given requestTimeout.
   *
   * @param request request to send
   * @param requestTimeout timeout for the request
   * @return future which will be completed when a response, rejection, or error from the broker is
   *     received. The future will be completed exceptionally on request dispatch, transport, or
   *     response parsing errors.
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

  /**
   * Whether a partition of the group has a leader that the topology does not show, but that this
   * client reaches in another way, for example through the mailbox of the database. Such a
   * partition takes requests, so choosing a partition for a request must not skip it. This is the
   * case in a region that leads no partition: its topology holds none of the leaders.
   *
   * <p>Does not block.
   *
   * @return false if there is no other way to reach a leader
   */
  default boolean hasLeaderOutsideTopology(final String partitionGroup, final int partitionId) {
    return false;
  }

  /**
   * Subscribes to job-available notifications on the given topic.
   *
   * @param topic the topic to subscribe to
   * @param subscriber identifies the caller; deduped per (topic, subscriber) pair using identity
   *     equality, so pass {@code this}, not a {@code String}
   * @param handler invoked with the topic's payload on each notification
   */
  void subscribeJobAvailableNotification(String topic, Object subscriber, Consumer<String> handler);
}
