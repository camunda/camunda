/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerResponseConsumer;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.RequestDispatchStrategy;
import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.util.StubbedTopologyManager;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.net.ConnectException;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class RequestRetryHandlerTest {

  private static final int PARTITION_COUNT = 3;

  private TestBrokerClient brokerClient;
  private RequestRetryHandler retryHandler;

  @BeforeEach
  void setUp() {
    final var topologyManager = new StubbedTopologyManager(PARTITION_COUNT);
    brokerClient = new TestBrokerClient();
    retryHandler = new RequestRetryHandler(brokerClient, topologyManager);
  }

  @Test
  void shouldSendToFixedPartitionWhenDispatchStrategyIsPresent() {
    // given - a request with a specific dispatch strategy targeting partition 2
    final int targetPartition = 2;
    final var request = new TestBrokerRequest(Optional.of(__ -> targetPartition));
    brokerClient.setResponse(new BrokerResponse<>("result", targetPartition, 1));

    // when
    final var result = new AtomicReference<String>();
    retryHandler.sendRequest(request, (key, response) -> result.set(response), error -> {});

    // then - the request was sent to the fixed partition
    assertThat(request.getPartitionId()).isEqualTo(targetPartition);
    assertThat(result.get()).isEqualTo("result");
  }

  @Test
  void shouldNotRetryOnOtherPartitionsWhenDispatchStrategyIsPresent() {
    // given - a request with a specific dispatch strategy that fails with a retryable error
    final int targetPartition = 2;
    final var request = new TestBrokerRequest(Optional.of(__ -> targetPartition));
    brokerClient.setError(new ConnectException("connection refused"));

    // when
    final var error = new AtomicReference<Throwable>();
    retryHandler.sendRequest(request, (key, response) -> {}, error::set);

    // then - the error is propagated without retrying on other partitions
    assertThat(error.get()).isInstanceOf(ConnectException.class);
    assertThat(brokerClient.sendCount.get()).isEqualTo(1);
  }

  @Test
  void shouldRetryOnOtherPartitionsWhenNoDispatchStrategy() {
    // given - a request without a dispatch strategy that fails with a retryable error
    final var request = new TestBrokerRequest(Optional.empty());
    brokerClient.setError(new ConnectException("connection refused"));

    // when
    final var error = new AtomicReference<Throwable>();
    retryHandler.sendRequest(request, (key, response) -> {}, error::set);

    // then - the request was retried on all partitions
    assertThat(brokerClient.sendCount.get()).isEqualTo(PARTITION_COUNT);
  }

  @Test
  void shouldPropagateStrategyExceptionWhenDispatchStrategyFails() {
    // given - a request whose dispatch strategy throws
    final var request =
        new TestBrokerRequest(
            Optional.of(
                __ -> {
                  throw new RuntimeException("no topology");
                }));

    // when
    final var error = new AtomicReference<Throwable>();
    retryHandler.sendRequest(request, (key, response) -> {}, error::set);

    // then - the exception from the strategy is propagated
    assertThat(error.get()).isInstanceOf(RuntimeException.class).hasMessage("no topology");
    assertThat(brokerClient.sendCount.get()).isEqualTo(0);
  }

  private static final class TestBrokerClient implements BrokerClient {
    final AtomicInteger sendCount = new AtomicInteger(0);
    private BrokerResponse<String> response;
    private Throwable error;

    void setResponse(final BrokerResponse<String> response) {
      this.response = response;
      error = null;
    }

    void setError(final Throwable error) {
      this.error = error;
      response = null;
    }

    @Override
    public Collection<ActorFuture<Void>> start() {
      return java.util.List.of();
    }

    @Override
    public void close() {}

    @Override
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<BrokerResponse<T>> sendRequest(final BrokerRequest<T> request) {
      sendCount.incrementAndGet();
      if (error != null) {
        return CompletableFuture.failedFuture(error);
      }
      return CompletableFuture.completedFuture((BrokerResponse<T>) response);
    }

    @Override
    public <T> CompletableFuture<BrokerResponse<T>> sendRequest(
        final BrokerRequest<T> request, final java.time.Duration requestTimeout) {
      return sendRequest(request);
    }

    @Override
    public <T> CompletableFuture<BrokerResponse<T>> sendRequestWithRetry(
        final BrokerRequest<T> request) {
      return sendRequest(request);
    }

    @Override
    public <T> CompletableFuture<BrokerResponse<T>> sendRequestWithRetry(
        final BrokerRequest<T> request, final java.time.Duration requestTimeout) {
      return sendRequest(request);
    }

    @Override
    public <T> void sendRequestWithRetry(
        final BrokerRequest<T> request,
        final BrokerResponseConsumer<T> responseConsumer,
        final Consumer<Throwable> throwableConsumer) {
      sendRequest(request)
          .whenComplete(
              (resp, err) -> {
                if (err != null) {
                  throwableConsumer.accept(err);
                } else {
                  responseConsumer.accept(resp.getKey(), resp.getResponse());
                }
              });
    }

    @Override
    public BrokerTopologyManager getTopologyManager() {
      return null;
    }

    @Override
    public void subscribeJobAvailableNotification(
        final String topic, final Consumer<String> handler) {}

    @Override
    public void subscribeJobAvailableByPartitionNotification(
        final java.util.function.BiConsumer<String, Integer> handler) {}
  }

  private static class TestBrokerRequest extends BrokerExecuteCommand<String> {
    private final Optional<RequestDispatchStrategy> strategy;

    TestBrokerRequest(final Optional<RequestDispatchStrategy> strategy) {
      super(ValueType.PROCESS_INSTANCE_CREATION, ProcessInstanceCreationIntent.CREATE);
      this.strategy = strategy;
    }

    @Override
    public BufferWriter getRequestWriter() {
      return null;
    }

    @Override
    protected String toResponseDto(final DirectBuffer buffer) {
      return null;
    }

    @Override
    public Optional<RequestDispatchStrategy> requestDispatchStrategy() {
      return strategy;
    }
  }
}
