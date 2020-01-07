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
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.MessagingException;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.zeebe.transport.ClientRequest;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RequestHandler;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerTransport;
import io.zeebe.transport.TransportFactory;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class AtomixDifferentServerTransportTest {

  @ClassRule public static final ActorSchedulerRule SCHEDULER_RULE = new ActorSchedulerRule();
  private static final Supplier<String> NODE_ADDRESS_SUPPLIER = () -> "0.0.0.0:26500";
  private static final String NODE_ADDRESS = "0.0.0.0:26500";

  private static ClientTransport clientTransport;
  private static ServerTransport serverTransport;
  private static AtomixCluster cluster;
  private static NettyMessagingService nettyMessagingService;

  @BeforeClass
  public static void setup() {
    cluster =
        AtomixCluster.builder()
            .withAddress(Address.from("0.0.0.0:26501"))
            .withMemberId("0")
            .withClusterId("cluster")
            .build();
    cluster.start().join();
    final var transportFactory = new TransportFactory(SCHEDULER_RULE.get());
    final var messagingService = cluster.getMessagingService();
    clientTransport = transportFactory.createClientTransport(messagingService);

    nettyMessagingService =
        new NettyMessagingService("cluster", Address.from(NODE_ADDRESS), new MessagingConfig());
    nettyMessagingService.start().join();
    serverTransport = transportFactory.createServerTransport(0, nettyMessagingService);
  }

  @After
  public void afterTest() {
    serverTransport.unsubscribe(0);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    serverTransport.close();
    nettyMessagingService.stop().join();
    clientTransport.close();
    cluster.stop().join();
  }

  @Test
  public void shouldSubscribeToPartition() {
    // given
    final var incomingRequestFuture = new CompletableFuture<byte[]>();
    serverTransport.subscribe(0, new DirectlyResponder(incomingRequestFuture::complete)).join();

    // when
    final var requestFuture =
        clientTransport.sendRequestWithRetry(
            NODE_ADDRESS_SUPPLIER, new Request("messageABC"), Duration.ofSeconds(1));

    // then
    final var response = requestFuture.join();
    assertThat(response.byteArray()).isEqualTo("messageABC".getBytes());
    assertThat(incomingRequestFuture.join()).isEqualTo("messageABC".getBytes());
  }

  @Test
  public void shouldOnInvalidResponseRetryUntilTimeout() {
    // given
    final var retries = new AtomicLong(0);
    serverTransport.subscribe(0, new DirectlyResponder(bytes -> retries.getAndIncrement())).join();

    // when
    final var requestFuture =
        clientTransport.sendRequestWithRetry(
            NODE_ADDRESS_SUPPLIER,
            (response) -> false,
            new Request("messageABC"),
            Duration.ofMillis(200));

    // then
    assertThatThrownBy(requestFuture::join).hasRootCauseInstanceOf(TimeoutException.class);
    assertThat(retries).hasValueGreaterThan(1);
  }

  @Test
  public void shouldFailResponseWhenRequestHandlerThrowsException() {
    // given
    serverTransport
        .subscribe(
            0,
            new DirectlyResponder(
                bytes -> {
                  throw new IllegalStateException("expected");
                }))
        .join();

    // when
    final var requestFuture =
        clientTransport.sendRequestWithRetry(
            NODE_ADDRESS_SUPPLIER, new Request("messageABC"), Duration.ofSeconds(1));

    // then
    assertThatThrownBy(requestFuture::join)
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(MessagingException.RemoteHandlerFailure.class);
  }

  @Test
  public void shouldUnsubscribeFromPartition() {
    // given
    final var incomingRequestFuture = new CompletableFuture<byte[]>();
    serverTransport.subscribe(0, new DirectlyResponder(incomingRequestFuture::complete)).join();

    // when
    serverTransport.unsubscribe(0).join();
    final var requestFuture =
        clientTransport.sendRequestWithRetry(
            NODE_ADDRESS_SUPPLIER, new Request("messageABC"), Duration.ofMillis(200));

    // then
    assertThatThrownBy(requestFuture::join).hasCauseInstanceOf(TimeoutException.class);
    assertThat(incomingRequestFuture).isNotCompleted();
  }

  @Test
  public void shouldTimeoutAfterDurationOnNonExistingRemote() {
    // given

    // when
    final var requestFuture =
        clientTransport.sendRequestWithRetry(
            () -> "0.0.0.0:26501", new Request("messageABC"), Duration.ofMillis(300));

    // then
    assertThatThrownBy(requestFuture::join).hasCauseInstanceOf(TimeoutException.class);
  }

  @Test
  public void shouldRetryAndSucceedAfterNodeIsResolved() throws InterruptedException {
    // given
    final var nodeAddressRef = new AtomicReference<String>();
    final var retryLatch = new CountDownLatch(3);
    serverTransport.subscribe(0, new DirectlyResponder()).join();
    final var requestFuture =
        clientTransport.sendRequestWithRetry(
            () -> {
              retryLatch.countDown();
              return nodeAddressRef.get();
            },
            new Request("messageABC"),
            Duration.ofSeconds(5));

    // when
    retryLatch.await();
    nodeAddressRef.set(NODE_ADDRESS);

    // then
    final var response = requestFuture.join();
    assertThat(response.byteArray()).isEqualTo("messageABC".getBytes());
  }

  @Test
  public void shouldTimeoutAfterDurationWhenNotSubscribed() {
    // given

    // when
    final var requestFuture =
        clientTransport.sendRequestWithRetry(
            () -> "0.0.0.0:26501", new Request("messageABC"), Duration.ofMillis(300));

    // then
    assertThatThrownBy(requestFuture::join).hasCauseInstanceOf(TimeoutException.class);
  }

  @Test
  public void shouldRetryAndSucceedAfterNodeSubscribed() throws InterruptedException {
    // given
    final var retryLatch = new CountDownLatch(3);
    final var requestFuture =
        clientTransport.sendRequestWithRetry(
            () -> {
              retryLatch.countDown();
              return NODE_ADDRESS;
            },
            new Request("messageABC"),
            Duration.ofSeconds(5));

    // when
    retryLatch.await();
    serverTransport.subscribe(0, new DirectlyResponder());

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

  private static class DirectlyResponder implements RequestHandler {

    private final Consumer<byte[]> requestConsumer;

    DirectlyResponder() {
      this(bytes -> {});
    }

    DirectlyResponder(final Consumer<byte[]> requestConsumer) {
      this.requestConsumer = requestConsumer;
    }

    @Override
    public void onRequest(
        final ServerOutput serverOutput,
        final int partitionId,
        final long requestId,
        final DirectBuffer buffer,
        final int offset,
        final int length) {
      final var serverResponse =
          new ServerResponseImpl()
              .buffer(buffer, 0, length)
              .setRequestId(requestId)
              .setPartitionId(partitionId);
      requestConsumer.accept(buffer.byteArray());
      serverOutput.sendResponse(serverResponse);
    }
  }
}
