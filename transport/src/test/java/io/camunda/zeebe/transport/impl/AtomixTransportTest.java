/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.MessagingException;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.transport.ClientRequest;
import io.camunda.zeebe.transport.ClientTransport;
import io.camunda.zeebe.transport.RequestHandler;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.transport.ServerTransport;
import io.camunda.zeebe.transport.TransportFactory;
import io.camunda.zeebe.util.sched.testing.ActorSchedulerRule;
import java.net.ConnectException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AtomixTransportTest {

  @ClassRule public static final ActorSchedulerRule SCHEDULER_RULE = new ActorSchedulerRule();

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
  private static final Duration REQUEST_TIMEOUT_NO_SUCCESS = Duration.ofMillis(200);

  private static Supplier<String> nodeAddressSupplier;
  private static AtomixCluster cluster;
  private static String serverAddress;
  private static TransportFactory transportFactory;
  private static NettyMessagingService nettyMessagingService;

  @Parameter(0)
  public String testName;

  @Parameter(1)
  public Function<AtomixCluster, ClientTransport> clientTransportFunction;

  @Parameter(2)
  public Function<AtomixCluster, ServerTransport> serverTransportFunction;

  private ClientTransport clientTransport;
  private ServerTransport serverTransport;

  @Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {
            "use same messaging service",
            (Function<AtomixCluster, ClientTransport>)
                (cluster) -> {
                  final var messagingService = cluster.getMessagingService();
                  return transportFactory.createClientTransport(messagingService);
                },
            (Function<AtomixCluster, ServerTransport>)
                (cluster) -> {
                  final var messagingService = cluster.getMessagingService();
                  return transportFactory.createServerTransport(0, messagingService);
                }
          },
          {
            "use different messaging service",
            (Function<AtomixCluster, ClientTransport>)
                (cluster) -> {
                  final var messagingService = cluster.getMessagingService();
                  return transportFactory.createClientTransport(messagingService);
                },
            (Function<AtomixCluster, ServerTransport>)
                (cluster) -> {
                  if (nettyMessagingService == null) {
                    // do only once
                    final var socketAddress = SocketUtil.getNextAddress();
                    serverAddress = socketAddress.getHostName() + ":" + socketAddress.getPort();
                    nodeAddressSupplier = () -> serverAddress;
                    nettyMessagingService =
                        new NettyMessagingService(
                            "cluster", Address.from(serverAddress), new MessagingConfig());
                    nettyMessagingService.start().join();
                  }

                  return transportFactory.createServerTransport(0, nettyMessagingService);
                }
          }
        });
  }

  @BeforeClass
  public static void setup() {
    final var socketAddress = SocketUtil.getNextAddress();
    serverAddress = socketAddress.getHostName() + ":" + socketAddress.getPort();
    nodeAddressSupplier = () -> serverAddress;

    cluster =
        AtomixCluster.builder()
            .withAddress(Address.from(serverAddress))
            .withMemberId("0")
            .withClusterId("cluster")
            .build();
    cluster.start().join();
    transportFactory = new TransportFactory(SCHEDULER_RULE.get());
  }

  @Before
  public void beforeTest() {
    clientTransport = clientTransportFunction.apply(cluster);
    serverTransport = serverTransportFunction.apply(cluster);
  }

  @After
  public void afterTest() throws Exception {
    serverTransport.close();
    clientTransport.close();
  }

  @AfterClass
  public static void tearDown() {
    if (nettyMessagingService != null) {
      nettyMessagingService.stop().join();
      nettyMessagingService = null;
    }
    cluster.stop().join();
    cluster = null;
  }

  @Test
  public void shouldSubscribeToPartition() {
    // given
    final var incomingRequestFuture = new CompletableFuture<byte[]>();
    serverTransport
        .subscribe(0, RequestType.COMMAND, new DirectlyResponder(incomingRequestFuture::complete))
        .join();

    // when
    final var requestFuture =
        clientTransport.sendRequestWithRetry(
            nodeAddressSupplier, new Request("messageABC"), REQUEST_TIMEOUT);

    // then
    final var response = requestFuture.join();
    assertThat(response.byteArray()).isEqualTo("messageABC".getBytes());
    assertThat(incomingRequestFuture.join()).isEqualTo("messageABC".getBytes());
  }

  @Test
  public void shouldRetryOnInvalidResponse() throws Exception {
    // given
    final var retryLatch = new CountDownLatch(2);
    serverTransport
        .subscribe(0, RequestType.COMMAND, new DirectlyResponder(bytes -> retryLatch.countDown()))
        .join();

    // when
    clientTransport.sendRequestWithRetry(
        nodeAddressSupplier, (response) -> false, new Request("messageABC"), REQUEST_TIMEOUT);

    // then
    final var success = retryLatch.await(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    assertThat(success).isTrue();
    assertThat(retryLatch.getCount()).isEqualTo(0);
  }

  @Test
  public void shouldFailResponseWhenRequestHandlerThrowsException() {
    // given
    serverTransport
        .subscribe(
            0,
            RequestType.COMMAND,
            new DirectlyResponder(
                bytes -> {
                  throw new IllegalStateException("expected");
                }))
        .join();

    // when
    final var requestFuture =
        clientTransport.sendRequestWithRetry(
            nodeAddressSupplier, new Request("messageABC"), REQUEST_TIMEOUT);

    // then
    assertThatThrownBy(requestFuture::join)
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(MessagingException.RemoteHandlerFailure.class);
  }

  @Test
  public void shouldUnsubscribeFromPartition() {
    // given
    final var incomingRequestFuture = new CompletableFuture<byte[]>();
    serverTransport
        .subscribe(0, RequestType.COMMAND, new DirectlyResponder(incomingRequestFuture::complete))
        .join();

    // when
    serverTransport.unsubscribe(0, RequestType.COMMAND).join();

    final var requestFuture =
        clientTransport.sendRequestWithRetry(
            nodeAddressSupplier, new Request("messageABC"), REQUEST_TIMEOUT_NO_SUCCESS);

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
            () -> "0.0.0.0:26499", new Request("messageABC"), REQUEST_TIMEOUT_NO_SUCCESS);

    // then
    assertThatThrownBy(requestFuture::join).hasCauseInstanceOf(TimeoutException.class);
  }

  @Test
  public void shouldNotRetryWhenNoRemoteFoundWhenSpecified() {
    // given

    // when
    final var requestFuture =
        clientTransport.sendRequest(() -> null, new Request("messageABC"), REQUEST_TIMEOUT);

    // then
    assertThatThrownBy(requestFuture::join)
        .hasCauseInstanceOf(ConnectException.class)
        .hasMessageContaining("no remote address found");
  }

  @Test
  public void shouldRetryAndSucceedAfterNodeIsResolved() throws InterruptedException {
    // given
    final var nodeAddressRef = new AtomicReference<String>();
    final var retryLatch = new CountDownLatch(3);
    serverTransport.subscribe(0, RequestType.COMMAND, new DirectlyResponder()).join();

    final var requestFuture =
        clientTransport.sendRequestWithRetry(
            () -> {
              retryLatch.countDown();
              return nodeAddressRef.get();
            },
            new Request("messageABC"),
            REQUEST_TIMEOUT);

    // when
    retryLatch.await(REQUEST_TIMEOUT.dividedBy(2).toMillis(), TimeUnit.MILLISECONDS);
    nodeAddressRef.set(serverAddress);

    // then
    final var response = requestFuture.join();
    assertThat(response.byteArray()).isEqualTo("messageABC".getBytes());
  }

  @Test
  public void shouldRetryAndSucceedAfterResponseIsValid() throws InterruptedException {
    // given
    final var retryLatch = new CountDownLatch(2);
    serverTransport
        .subscribe(0, RequestType.COMMAND, new DirectlyResponder(bytes -> retryLatch.countDown()))
        .join();

    final var responseValidation = new AtomicBoolean(false);
    final var requestFuture =
        clientTransport.sendRequestWithRetry(
            nodeAddressSupplier,
            (responseToValidate) -> responseValidation.get(),
            new Request("messageABC"),
            REQUEST_TIMEOUT);

    // when
    retryLatch.await(REQUEST_TIMEOUT.dividedBy(2).toMillis(), TimeUnit.MILLISECONDS);
    responseValidation.set(true);

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
            () -> "0.0.0.0:26499", new Request("messageABC"), REQUEST_TIMEOUT_NO_SUCCESS);

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
              return serverAddress;
            },
            new Request("messageABC"),
            REQUEST_TIMEOUT);

    // when
    retryLatch.await(REQUEST_TIMEOUT.dividedBy(2).toMillis(), TimeUnit.MILLISECONDS);
    serverTransport.subscribe(0, RequestType.COMMAND, new DirectlyResponder()).join();

    // then
    final var response = requestFuture.join();
    assertThat(response.byteArray()).isEqualTo("messageABC".getBytes());
  }

  @Test
  public void shouldOnlyHandleRequestsOfSubscribedTypes() {
    // given
    serverTransport.subscribe(0, RequestType.COMMAND, new DirectlyResponder()).join();
    serverTransport.subscribe(0, RequestType.UNKNOWN, new FailingResponder()).join();

    // when
    final var requestFuture =
        clientTransport.sendRequest(
            () -> serverAddress, new Request("messageABC"), REQUEST_TIMEOUT);

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
    public RequestType getRequestType() {
      return RequestType.COMMAND;
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

  private static final class FailingResponder implements RequestHandler {

    @Override
    public void onRequest(
        final ServerOutput serverOutput,
        final int partitionId,
        final long requestId,
        final DirectBuffer buffer,
        final int offset,
        final int length) {
      fail("Expected request to not be handled by this handler, but it was");
    }
  }
}
