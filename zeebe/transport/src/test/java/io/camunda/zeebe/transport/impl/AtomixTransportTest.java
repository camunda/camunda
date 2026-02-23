/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.transport.ClientRequest;
import io.camunda.zeebe.transport.ClientTransport;
import io.camunda.zeebe.transport.RequestHandler;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.transport.ServerTransport;
import io.camunda.zeebe.transport.TransportFactory;
import io.camunda.zeebe.transport.impl.AtomixServerTransport.TopicSupplier;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.ConnectException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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
import org.agrona.concurrent.SnowflakeIdGenerator;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.jupiter.api.AutoClose;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AtomixTransportTest {

  @ClassRule public static final ActorSchedulerRule SCHEDULER_RULE = new ActorSchedulerRule();

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
  private static final Duration REQUEST_TIMEOUT_NO_SUCCESS = Duration.ofMillis(200);
  private static final String TOPIC_PREFIX = "default";
  private static final List<TopicSupplier> TOPIC_SUPPLIERS =
      List.of(TopicSupplier.withLegacyTopicName(), TopicSupplier.withPrefix(TOPIC_PREFIX));

  private static Supplier<String> nodeAddressSupplier;
  private static AtomixCluster cluster;
  private static String serverAddress;
  private static TransportFactory transportFactory;
  private static NettyMessagingService nettyMessagingService;
  @AutoClose private static MeterRegistry meterRegistry = new SimpleMeterRegistry();

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
    final SnowflakeIdGenerator requestIdGenerator = new SnowflakeIdGenerator(0);
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
                  return transportFactory.createServerTransport(
                      messagingService, requestIdGenerator, TOPIC_SUPPLIERS);
                }
          },
          {
            "use default-{topic}-prefixed topics in client requests",
            (Function<AtomixCluster, ClientTransport>)
                (cluster) -> {
                  final var messagingService = cluster.getMessagingService();
                  return transportFactory.createClientTransport(
                      messagingService, TopicSupplier.withPrefix(TOPIC_PREFIX));
                },
            (Function<AtomixCluster, ServerTransport>)
                (cluster) -> {
                  final var messagingService = cluster.getMessagingService();
                  return transportFactory.createServerTransport(
                      messagingService, requestIdGenerator, TOPIC_SUPPLIERS);
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
                  createNettyMessagingServiceIfNull();
                  return transportFactory.createServerTransport(
                      nettyMessagingService, requestIdGenerator, TOPIC_SUPPLIERS);
                }
          },
          {
            "use different messaging service and prefixed client requests",
            (Function<AtomixCluster, ClientTransport>)
                (cluster) -> {
                  final var messagingService = cluster.getMessagingService();
                  return transportFactory.createClientTransport(
                      messagingService, TopicSupplier.withPrefix(TOPIC_PREFIX));
                },
            (Function<AtomixCluster, ServerTransport>)
                (cluster) -> {
                  createNettyMessagingServiceIfNull();
                  return transportFactory.createServerTransport(
                      nettyMessagingService, requestIdGenerator, TOPIC_SUPPLIERS);
                }
          }
        });
  }

  static void createNettyMessagingServiceIfNull() {
    if (nettyMessagingService == null) {
      // do only once
      final var socketAddress = SocketUtil.getNextAddress();
      serverAddress = socketAddress.getHostName() + ":" + socketAddress.getPort();
      nodeAddressSupplier = () -> serverAddress;
      nettyMessagingService =
          new NettyMessagingService(
              "cluster", Address.from(serverAddress), new MessagingConfig(), meterRegistry);
      nettyMessagingService.start().join();
    }
  }

  @BeforeClass
  public static void setup() {
    final var socketAddress = SocketUtil.getNextAddress();
    serverAddress = socketAddress.getHostName() + ":" + socketAddress.getPort();
    nodeAddressSupplier = () -> serverAddress;

    cluster =
        AtomixCluster.builder(meterRegistry)
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

  @Test
  public void shouldCreateUniqueRequestsIds() {
    final DirectlyResponder directlyResponder = new DirectlyResponder();

    serverTransport.subscribe(0, RequestType.COMMAND, directlyResponder).join();

    // when
    final var requestFuture1 =
        clientTransport.sendRequestWithRetry(
            nodeAddressSupplier, new Request("messageABC"), REQUEST_TIMEOUT);
    requestFuture1.join();
    final long requestId1 = directlyResponder.serverResponse.getRequestId();

    final var requestFuture2 =
        clientTransport.sendRequestWithRetry(
            nodeAddressSupplier, new Request("messageABC"), REQUEST_TIMEOUT);
    requestFuture2.join();
    final long requestId2 = directlyResponder.serverResponse.getRequestId();

    // then
    assertThat(requestId1).isNotEqualByComparingTo(requestId2);
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
    public int write(final MutableDirectBuffer buffer, final int offset) {
      final var bytes = msg.getBytes();
      buffer.putBytes(offset, bytes);
      return bytes.length;
    }
  }

  private static class DirectlyResponder implements RequestHandler {

    private final Consumer<byte[]> requestConsumer;
    private ServerResponseImpl serverResponse;

    DirectlyResponder() {
      requestConsumer = (bytes -> {});
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
      serverResponse =
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
