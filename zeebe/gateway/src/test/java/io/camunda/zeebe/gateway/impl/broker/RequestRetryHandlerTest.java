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
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerResponseConsumer;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.RequestDispatchStrategy;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.broker.client.impl.RoundRobinDispatchStrategy;
import io.camunda.zeebe.gateway.api.util.StubbedTopologyManager;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
  private StubbedTopologyManager topologyManager;

  @BeforeEach
  void setUp() {
    topologyManager = new StubbedTopologyManager(PARTITION_COUNT);
    brokerClient = new TestBrokerClient();
    // Use a deterministic round-robin starting at offset 0 → partitions 1, 2, 3, 1, ...
    final var dispatchStrategy = new RoundRobinDispatchStrategy(0);
    retryHandler = new RequestRetryHandler(brokerClient, topologyManager, dispatchStrategy);
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
  void shouldRetryOnNextRoundRobinPartitionOnResourceExhausted() {
    // given - a broker client that fails with RESOURCE_EXHAUSTED on the first attempt
    final var request = new TestBrokerRequest(Optional.empty());
    final var exhaustedError =
        new BrokerErrorException(new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure"));
    brokerClient.failOnPartitionThenSucceed(exhaustedError);

    // when
    final var result = new AtomicReference<String>();
    final var error = new AtomicReference<Throwable>();
    retryHandler.sendRequest(request, (key, response) -> result.set(response), error::set);

    // then - first attempt hit partition 1, retry hit partition 2 (round-robin from offset 0)
    assertThat(error.get()).isNull();
    assertThat(result.get()).isEqualTo("result");
    assertThat(brokerClient.partitionsHit).containsExactly(1, 2);
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
    final List<Integer> partitionsHit = new ArrayList<>();
    private BrokerResponse<String> response;
    private Throwable error;
    private Throwable failFirstError;

    void setResponse(final BrokerResponse<String> response) {
      this.response = response;
      error = null;
    }

    void setError(final Throwable error) {
      this.error = error;
      response = null;
    }

    void failOnPartitionThenSucceed(final Throwable firstError) {
      failFirstError = firstError;
      response = new BrokerResponse<>("result", 1, 1);
      error = null;
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
      final int count = sendCount.incrementAndGet();
      partitionsHit.add(request.getPartitionId());
      if (error != null) {
        return CompletableFuture.failedFuture(error);
      }
      if (failFirstError != null && count == 1) {
        return CompletableFuture.failedFuture(failFirstError);
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
