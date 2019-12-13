/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.AtomixCluster;
import io.zeebe.transport.ClientRequest;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.MutableDirectBuffer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class AtomixTransportTest {

  @ClassRule public static final ActorSchedulerRule SCHEDULER_RULE = new ActorSchedulerRule();

  private static AtomixClientOutputAdapter atomixClientOuputAdapter;
  private static AtomixRequestSubscription atomixRequestSubscription;
  private static AtomixCluster cluster;

  @BeforeClass
  public static void setup() {
    cluster =
        AtomixCluster.builder().withPort(26500).withMemberId("0").withClusterId("cluster").build();
    cluster.start().join();
    final var communicationService = cluster.getCommunicationService();
    atomixClientOuputAdapter = new AtomixClientOutputAdapter(communicationService);
    atomixRequestSubscription = new AtomixRequestSubscription(communicationService);
    SCHEDULER_RULE.submitActor(atomixClientOuputAdapter);
  }

  @After
  public void afterTest() {
    atomixRequestSubscription.unsubscribe(0);
  }

  @AfterClass
  public static void tearDown() {
    cluster.stop().join();
  }

  @Test
  public void shouldSubscribeToPartition() {
    // given
    final var incomingRequestFuture = new CompletableFuture<byte[]>();
    atomixRequestSubscription.subscribe(
        0,
        requestBytes -> {
          incomingRequestFuture.complete(requestBytes);
          return CompletableFuture.completedFuture(requestBytes);
        });

    // when
    final var requestFuture =
        atomixClientOuputAdapter.sendRequestWithRetry(
            () -> 0, new Request("messageABC"), Duration.ofSeconds(1));

    // then
    final var response = requestFuture.join();
    assertThat(response.byteArray()).isEqualTo("messageABC".getBytes());
    assertThat(incomingRequestFuture.join()).isEqualTo("messageABC".getBytes());
  }

  @Test
  public void shouldOnInvalidResponseRetryUntilTimeout() {
    // given
    final var retries = new AtomicLong(0);
    atomixRequestSubscription.subscribe(
        0,
        requestBytes -> {
          retries.incrementAndGet();
          return CompletableFuture.completedFuture(requestBytes);
        });

    // when
    final var requestFuture =
        atomixClientOuputAdapter.sendRequestWithRetry(
            () -> 0, (response) -> false, new Request("messageABC"), Duration.ofSeconds(1));

    // then
    assertThatThrownBy(requestFuture::join).hasRootCauseInstanceOf(TimeoutException.class);
    assertThat(retries).hasValueGreaterThan(1);
  }

  @Test
  public void shouldFailResponseWhenRequestHandlerThrowsException() {
    // given
    atomixRequestSubscription.subscribe(
        0,
        requestBytes -> {
          throw new IllegalStateException("expected");
        });

    // when
    final var requestFuture =
        atomixClientOuputAdapter.sendRequestWithRetry(
            () -> 0, new Request("messageABC"), Duration.ofSeconds(1));

    // then
    assertThatThrownBy(requestFuture::join)
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldFailResponseWhenRequestHandlerCompletesExceptionally() {
    // given
    atomixRequestSubscription.subscribe(
        0,
        requestBytes -> {
          final var future = new CompletableFuture<byte[]>();
          future.completeExceptionally(new IllegalStateException("expected"));
          return future;
        });

    // when
    final var requestFuture =
        atomixClientOuputAdapter.sendRequestWithRetry(
            () -> 0, new Request("messageABC"), Duration.ofSeconds(1));

    // then
    assertThatThrownBy(requestFuture::join)
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(CompletionException.class);
  }

  @Test
  public void shouldUnsubscribeFromPartition() {
    // given
    final var incomingRequestFuture = new CompletableFuture<byte[]>();
    atomixRequestSubscription.subscribe(
        0,
        requestBytes -> {
          incomingRequestFuture.complete(requestBytes);
          return CompletableFuture.completedFuture(requestBytes);
        });
    final var requestFuture =
        atomixClientOuputAdapter.sendRequestWithRetry(
            () -> 0, new Request("messageABC"), Duration.ofSeconds(1));

    // when
    atomixRequestSubscription.unsubscribe(0);

    // then
    assertThatThrownBy(requestFuture::join).hasCauseInstanceOf(TimeoutException.class);
    assertThat(incomingRequestFuture).isNotCompleted();
  }

  @Test
  public void shouldTimeoutAfterDurationOnNonExistingRemote() {
    // given

    // when
    final var requestFuture =
        atomixClientOuputAdapter.sendRequestWithRetry(
            () -> 1, new Request("messageABC"), Duration.ofMillis(300));

    // then
    assertThatThrownBy(requestFuture::join).hasCauseInstanceOf(TimeoutException.class);
  }

  @Test
  public void shouldRetryAndSucceedAfterNodeIsResolved() throws InterruptedException {
    // given
    final var nodeIdRef = new AtomicReference<Integer>();
    final var retryLatch = new CountDownLatch(3);
    atomixRequestSubscription.subscribe(0, CompletableFuture::completedFuture);
    final var requestFuture =
        atomixClientOuputAdapter.sendRequestWithRetry(
            () -> {
              retryLatch.countDown();
              return nodeIdRef.get();
            },
            new Request("messageABC"),
            Duration.ofSeconds(5));

    // when
    retryLatch.await();
    nodeIdRef.set(0);

    // then
    final var response = requestFuture.join();
    assertThat(response.byteArray()).isEqualTo("messageABC".getBytes());
  }

  @Test
  public void shouldTimeoutAfterDurationWhenNotSubscribed() {
    // given

    // when
    final var requestFuture =
        atomixClientOuputAdapter.sendRequestWithRetry(
            () -> 1, new Request("messageABC"), Duration.ofMillis(300));

    // then
    assertThatThrownBy(requestFuture::join).hasCauseInstanceOf(TimeoutException.class);
  }

  @Test
  public void shouldRetryAndSucceedAfterNodeSubscribed() throws InterruptedException {
    // given
    final var retryLatch = new CountDownLatch(3);
    final var requestFuture =
        atomixClientOuputAdapter.sendRequestWithRetry(
            () -> {
              retryLatch.countDown();
              return 0;
            },
            new Request("messageABC"),
            Duration.ofSeconds(5));

    // when
    retryLatch.await();
    atomixRequestSubscription.subscribe(0, CompletableFuture::completedFuture);

    // then
    final var response = requestFuture.join();
    assertThat(response.byteArray()).isEqualTo("messageABC".getBytes());
  }

  private static final class Request implements ClientRequest {

    private final String msg;

    public Request(final String msg) {
      this.msg = msg;
    }

    @Override
    public int getPartitionId() {
      return 0;
    }

    @Override
    public int getLength() {
      return msg.length();
    }

    @Override
    public void write(final MutableDirectBuffer buffer, final int offset) {
      buffer.putBytes(offset, msg.getBytes());
    }
  }
}
