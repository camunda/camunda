/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport;

import static io.zeebe.test.util.BufferAssert.assertThatBuffer;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.DirectBufferWriter.writerFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.test.util.io.FailingBufferWriter;
import io.zeebe.test.util.io.FailingBufferWriter.FailingBufferWriterException;
import io.zeebe.test.util.socket.SocketUtil;
import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.transport.util.ControllableServerTransport;
import io.zeebe.transport.util.EchoRequestResponseHandler;
import io.zeebe.transport.util.RecordingChannelListener;
import io.zeebe.util.ByteValue;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.mockito.ArgumentMatchers;

public class ClientTransportTest {
  public static final DirectBuffer BUF1 = BufferUtil.wrapBytes(1, 2, 3, 4);
  public static final BufferWriter WRITER1 = writerFor(BUF1);
  public static final int NODE_ID1 = 1;
  public static final SocketAddress SERVER_ADDRESS1 =
      new SocketAddress(SocketUtil.getNextAddress());
  public static final int NODE_ID2 = 2;
  public static final SocketAddress SERVER_ADDRESS2 =
      new SocketAddress(SocketUtil.getNextAddress());
  public static final int REQUEST_POOL_SIZE = 4;
  public static final ByteValue BUFFER_SIZE = ByteValue.ofKilobytes(16);
  public final AutoCloseableRule closeables = new AutoCloseableRule();
  public final ControlledActorClock clock = new ControlledActorClock();
  public final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(3, clock);
  @Rule public RuleChain ruleChain = RuleChain.outerRule(actorSchedulerRule).around(closeables);
  protected Dispatcher clientReceiveBuffer;
  protected ClientTransport clientTransport;

  @Before
  public void setUp() {
    clientReceiveBuffer =
        Dispatchers.create("clientReceiveBuffer")
            .bufferSize(BUFFER_SIZE)
            .actorScheduler(actorSchedulerRule.get())
            .build();
    closeables.manage(clientReceiveBuffer);

    clientTransport =
        Transports.newClientTransport("test")
            .scheduler(actorSchedulerRule.get())
            .messageReceiveBuffer(clientReceiveBuffer)
            .build();
    closeables.manage(clientTransport);
  }

  protected ControllableServerTransport buildControllableServerTransport() {
    final ControllableServerTransport serverTransport = new ControllableServerTransport();
    closeables.manage(serverTransport);
    return serverTransport;
  }

  protected ServerTransport buildServerTransport(
      final Function<ServerTransportBuilder, ServerTransport> builderConsumer) {
    final Dispatcher serverSendBuffer =
        Dispatchers.create("serverSendBuffer")
            .bufferSize(BUFFER_SIZE)
            .actorScheduler(actorSchedulerRule.get())
            .build();
    closeables.manage(serverSendBuffer);

    final ServerTransportBuilder transportBuilder =
        Transports.newServerTransport().scheduler(actorSchedulerRule.get());

    final ServerTransport serverTransport = builderConsumer.apply(transportBuilder);
    closeables.manage(serverTransport);

    return serverTransport;
  }

  @Test
  public void shouldOpenChannelOnRegistrationOfEndpoint() {
    // given
    final int nodeId = 123;
    final ControllableServerTransport serverTransport = buildControllableServerTransport();
    serverTransport.listenOn(SERVER_ADDRESS1);
    final RecordingChannelListener channelListener = new RecordingChannelListener();
    clientTransport.registerChannelListener(channelListener);

    // when
    clientTransport.registerEndpoint(nodeId, SERVER_ADDRESS1);

    // then
    waitUntil(() -> channelListener.getOpenedConnections().size() == 1);

    assertThat(channelListener.getOpenedConnections()).hasSize(1);
    assertThat(channelListener.getOpenedConnections().get(0))
        .hasFieldOrPropertyWithValue("address", SERVER_ADDRESS1);
  }

  @Test
  public void shouldUseSameChannelForConsecutiveRequestsToSameEndpoint() {
    // given
    final ControllableServerTransport serverTransport = buildControllableServerTransport();
    serverTransport.listenOn(SERVER_ADDRESS1);

    clientTransport.registerEndpointAndAwaitChannel(NODE_ID1, SERVER_ADDRESS1);

    final ClientOutput output = clientTransport.getOutput();
    output.sendRequest(NODE_ID1, WRITER1);
    output.sendRequest(NODE_ID1, WRITER1);

    final AtomicInteger messageCounter = serverTransport.acceptNextConnection(SERVER_ADDRESS1);

    // when
    TestUtil.doRepeatedly(() -> serverTransport.receive(SERVER_ADDRESS1))
        .until((r) -> messageCounter.get() == 2);

    // then
    assertThat(serverTransport.getClientChannels(SERVER_ADDRESS1)).hasSize(1);
  }

  @Test
  public void shouldUseDifferentChannelsForDifferentEndpoints() {
    // given
    final ControllableServerTransport serverTransport = buildControllableServerTransport();
    serverTransport.listenOn(SERVER_ADDRESS1);
    serverTransport.listenOn(SERVER_ADDRESS2);
    final ClientOutput output = clientTransport.getOutput();

    clientTransport.registerEndpointAndAwaitChannel(NODE_ID1, SERVER_ADDRESS1);
    clientTransport.registerEndpointAndAwaitChannel(NODE_ID2, SERVER_ADDRESS2);

    output.sendRequest(NODE_ID1, WRITER1);
    output.sendRequest(NODE_ID2, WRITER1);

    // when
    final AtomicInteger messageCounter1 = serverTransport.acceptNextConnection(SERVER_ADDRESS1);
    final AtomicInteger messageCounter2 = serverTransport.acceptNextConnection(SERVER_ADDRESS2);

    // then
    TestUtil.doRepeatedly(() -> serverTransport.receive(SERVER_ADDRESS1))
        .until((r) -> messageCounter1.get() == 1);
    TestUtil.doRepeatedly(() -> serverTransport.receive(SERVER_ADDRESS2))
        .until((r) -> messageCounter2.get() == 1);

    assertThat(serverTransport.getClientChannels(SERVER_ADDRESS1)).hasSize(1);
    assertThat(serverTransport.getClientChannels(SERVER_ADDRESS2)).hasSize(1);
  }

  @Test
  public void shouldOpenNewChannelOnceChannelIsClosed() {
    // given
    final ControllableServerTransport serverTransport = buildControllableServerTransport();
    serverTransport.listenOn(SERVER_ADDRESS1);

    final RecordingChannelListener channelListener = new RecordingChannelListener();

    clientTransport.registerChannelListener(channelListener);
    clientTransport.registerEndpointAndAwaitChannel(NODE_ID1, SERVER_ADDRESS1);

    serverTransport.acceptNextConnection(SERVER_ADDRESS1);

    serverTransport.getClientChannels(SERVER_ADDRESS1).get(0).close();
    serverTransport.acceptNextConnection(SERVER_ADDRESS1);

    // when
    waitUntil(() -> channelListener.getOpenedConnections().size() == 2);

    // then
    assertThat(serverTransport.getClientChannels(SERVER_ADDRESS1)).hasSize(2);
    assertThat(channelListener.getOpenedConnections()).hasSize(2);
    assertThat(channelListener.getClosedConnections()).hasSize(1);
  }

  @Test
  public void shouldCloseChannelsWhenTransportCloses() {
    // given
    final ControllableServerTransport serverTransport = buildControllableServerTransport();
    serverTransport.listenOn(SERVER_ADDRESS1);

    clientTransport.registerEndpoint(NODE_ID1, SERVER_ADDRESS1);
    serverTransport.acceptNextConnection(SERVER_ADDRESS1);

    waitUntil(() -> serverTransport.getClientChannels(SERVER_ADDRESS1).size() == 1);
    final TransportChannel channel = serverTransport.getClientChannels(SERVER_ADDRESS1).get(0);

    // when
    clientTransport.close();
    serverTransport.receive(
        SERVER_ADDRESS1); // receive once to make server recognize that channel has closed

    // then
    TestUtil.waitUntil(() -> !channel.getNioChannel().isOpen());

    assertThat(serverTransport.getClientChannels(SERVER_ADDRESS1)).hasSize(1);
    assertThat(channel.getNioChannel().isOpen()).isFalse();
  }

  @Test
  public void shouldTimeoutRequestWhenChannelNotAvailable() {
    // given
    clientTransport.registerEndpoint(NODE_ID1, SERVER_ADDRESS1);
    final ClientOutput output = clientTransport.getOutput();

    // when
    final ActorFuture<ClientResponse> responseFuture =
        output.sendRequest(NODE_ID1, WRITER1, Duration.ofMillis(500));

    // then
    assertThatThrownBy(responseFuture::join).hasMessageContaining("Request timed out after PT0.5S");
  }

  @Test
  public void shouldOpenRequestWhenClientRequestPoolCapacityIsExceeded() {
    // given
    final ClientOutput clientOutput = clientTransport.getOutput();
    clientTransport.registerEndpoint(NODE_ID1, SERVER_ADDRESS1);

    for (int i = 0; i < REQUEST_POOL_SIZE; i++) {
      clientOutput.sendRequest(NODE_ID1, WRITER1);
    }

    // when
    final ActorFuture<ClientResponse> responseFuture = clientOutput.sendRequest(NODE_ID1, WRITER1);

    // then
    assertThat(responseFuture).isNotNull();
  }

  @Test
  public void shouldBeAbleToPostponeReceivedMessage() {
    // given
    final AtomicInteger numInvocations = new AtomicInteger(0);
    final AtomicBoolean consumeMessage = new AtomicBoolean(false);

    final ClientInputMessageSubscription subscription =
        clientTransport
            .openSubscription(
                "foo",
                (output, remoteAddress, buffer, offset, length) -> {
                  numInvocations.incrementAndGet();
                  return consumeMessage.getAndSet(true);
                })
            .join();

    // when simulating a received message
    doRepeatedly(() -> clientReceiveBuffer.offer(BUF1)).until(p -> p >= 0);

    // then handler has been invoked twice, once when the message was postponed, and once when it
    // was consumed
    doRepeatedly(subscription::poll).until(i -> i != 0);
    assertThat(numInvocations.get()).isEqualTo(2);
  }

  @Test
  public void shouldNotBlockAllRequestsWhenOneRemoteIsNotReachable() {
    // given
    final ControllableServerTransport serverTransport = buildControllableServerTransport();
    serverTransport.listenOn(SERVER_ADDRESS1);

    clientTransport.registerEndpointAndAwaitChannel(NODE_ID1, SERVER_ADDRESS1);
    clientTransport.registerEndpoint(NODE_ID2, SERVER_ADDRESS2);

    final AtomicInteger messageCounter = serverTransport.acceptNextConnection(SERVER_ADDRESS1);

    final ClientOutput output = clientTransport.getOutput();

    // when
    output.sendRequest(NODE_ID2, WRITER1);
    output.sendRequest(NODE_ID1, WRITER1);

    // then blocked request 1 should not block sending request 2
    doRepeatedly(() -> serverTransport.receive(SERVER_ADDRESS1))
        .until(i -> messageCounter.get() > 0);
    assertThat(messageCounter.get()).isGreaterThanOrEqualTo(0);
  }

  @Test
  public void shouldNotCreateChannelsWhenRemoteAddressIsRetired() throws InterruptedException {
    // given
    final int nodeId = 123;
    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    final RecordingChannelListener channelListener = new RecordingChannelListener();
    clientTransport.registerChannelListener(channelListener).join();

    clientTransport.registerEndpoint(nodeId, SERVER_ADDRESS1);

    waitUntil(
        () ->
            channelListener.getOpenedConnections().stream().anyMatch(this::containsServerAddress1));

    // when
    clientTransport.retireEndpoint(nodeId);
    clientTransport.closeAllChannels().join();

    // then
    waitUntil(
        () ->
            channelListener.getClosedConnections().stream().anyMatch(this::containsServerAddress1));
    Thread.sleep(1000L); // timeout for potential reconnection of channel

    // no new channel was connected
    assertThat(channelListener.getOpenedConnections()).hasSize(1);
  }

  @Test
  public void shouldNotCreateChannelsWhenRemoteAddressIsDeactivated() throws InterruptedException {
    // given
    final int nodeId = 123;
    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    final RecordingChannelListener channelListener = new RecordingChannelListener();
    clientTransport.registerChannelListener(channelListener).join();

    clientTransport.registerEndpoint(nodeId, SERVER_ADDRESS1);

    waitUntil(
        () ->
            channelListener.getOpenedConnections().stream().anyMatch(this::containsServerAddress1));

    // when
    clientTransport.deactivateEndpoint(nodeId);
    clientTransport.closeAllChannels().join();

    // then
    waitUntil(
        () ->
            channelListener.getClosedConnections().stream().anyMatch(this::containsServerAddress1));
    Thread.sleep(1000L); // timeout for potential reconnection of channel

    // no new channel was connected
    assertThat(channelListener.getOpenedConnections()).hasSize(1);
  }

  @Test
  public void shouldReopenChannelAfterReactivation() {
    // given
    final int nodeId = 123;
    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    final RecordingChannelListener channelListener = new RecordingChannelListener();
    clientTransport.registerChannelListener(channelListener).join();

    clientTransport.registerEndpoint(nodeId, SERVER_ADDRESS1);
    waitUntil(
        () ->
            channelListener.getOpenedConnections().stream().anyMatch(this::containsServerAddress1));

    clientTransport.deactivateEndpoint(nodeId);
    clientTransport.closeAllChannels().join();

    // when
    clientTransport.registerEndpoint(nodeId, SERVER_ADDRESS1);

    // then
    waitUntil(() -> channelListener.getOpenedConnections().size() >= 2);
    assertThat(channelListener.getOpenedConnections())
        .extracting("address")
        .contains(SERVER_ADDRESS1, SERVER_ADDRESS1);
  }

  @Test
  public void shouldMakeRequestWithRetries() {
    // given
    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    clientTransport.registerEndpoint(NODE_ID1, SERVER_ADDRESS1);

    // when
    final ActorFuture<ClientResponse> request =
        clientTransport.getOutput().sendRequest(NODE_ID1, WRITER1, Duration.ofSeconds(10));

    // then
    final ClientResponse response = request.join();
    assertThatBuffer(response.getResponseBuffer()).hasBytes(BUF1);
  }

  @Test
  public void shouldThrowSynchronousExceptionOnRequestWithRetriesWithFailingWriter() {
    // given
    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    clientTransport.registerEndpoint(NODE_ID1, SERVER_ADDRESS1);

    // when/then
    assertThatThrownBy(
            () -> clientTransport.getOutput().sendRequest(NODE_ID1, new FailingBufferWriter()))
        .isInstanceOf(FailingBufferWriterException.class);
  }

  /**
   * Ensures that the provided BufferWriter is not invoked asynchronously as it may not contain
   * valid data at this point anymore.
   */
  @Test
  public void shouldSerializeRequestWithRetriesOnlyOnce() throws InterruptedException {
    // given
    final BufferWriter writer = mock(BufferWriter.class);
    when(writer.getLength()).thenReturn(16);

    clientTransport.registerEndpoint(NODE_ID1, SERVER_ADDRESS1);
    final ActorFuture<ClientResponse> response =
        clientTransport.getOutput().sendRequest(NODE_ID1, writer);

    // when
    Thread.sleep(1000L); // should make a couple of send attempts in this second
    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    // then the request was not serialized more than once
    response.join();
    verify(writer, times(1)).write(ArgumentMatchers.any(), ArgumentMatchers.anyInt());
  }

  @Test
  public void shouldSendMultipleRequests() {
    // given
    final BufferWriter writer = mock(BufferWriter.class);
    when(writer.getLength()).thenReturn(16);

    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    clientTransport.registerEndpoint(NODE_ID1, SERVER_ADDRESS1);

    for (int i = 0; i < 10; i++) {
      clientTransport.getOutput().sendRequest(NODE_ID1, writer).join();
    }
  }

  @Test
  public void shouldSendMultipleRequestsAsync() {
    // given
    final BufferWriter writer = mock(BufferWriter.class);
    when(writer.getLength()).thenReturn(16);

    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    clientTransport.registerEndpoint(NODE_ID1, SERVER_ADDRESS1);

    final List<ActorFuture<ClientResponse>> responseList = new ArrayList<>();

    for (int i = 0; i < 10; i++) {
      responseList.add(clientTransport.getOutput().sendRequest(NODE_ID1, writer));
    }

    responseList.forEach(ActorFuture::join);
  }

  @Test
  public void shouldProvideResponseProperties() {
    // given
    final AtomicLong capturedRequestId = new AtomicLong();

    final EchoRequestResponseHandler requestHandler =
        new EchoRequestResponseHandler() {
          @Override
          public boolean onRequest(
              final ServerOutput output,
              final RemoteAddress remoteAddress,
              final DirectBuffer buffer,
              final int offset,
              final int length,
              final long requestId) {
            capturedRequestId.set(requestId);

            return super.onRequest(output, remoteAddress, buffer, offset, length, requestId);
          }
        };

    buildServerTransport(
        b -> b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress()).build(null, requestHandler));

    clientTransport.registerEndpoint(NODE_ID1, SERVER_ADDRESS1);

    // when
    final ClientResponse response =
        clientTransport.getOutput().sendRequest(NODE_ID1, WRITER1).join();

    // then
    assertThat(response.getRemoteAddress().getAddress()).isEqualTo(SERVER_ADDRESS1);
    assertThat(response.getRequestId()).isEqualTo(capturedRequestId.get());
    assertThat(response.getResponseBuffer()).isEqualTo(BUF1);
  }

  @Test
  public void shouldTimeoutRequest() {
    // given
    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, (output, remoteAddress, buffer, offset, length, requestId) -> false));

    final BufferWriter writer = mock(BufferWriter.class);
    when(writer.getLength()).thenReturn(16);

    clientTransport.registerEndpoint(NODE_ID1, SERVER_ADDRESS1);

    // when
    final ActorFuture<ClientResponse> clientRequestActorFuture =
        clientTransport.getOutput().sendRequest(NODE_ID1, writer, Duration.ofSeconds(10));

    // then
    doRepeatedly(() -> clock.addTime(Duration.ofSeconds(10)))
        .until((v) -> clientRequestActorFuture.isDone());

    assertThatThrownBy(clientRequestActorFuture::join)
        .hasMessageContaining("Request timed out after PT10S");
  }

  @Test
  public void shouldRetrySendRequestIfChannelIsNotOpen() {
    // given
    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    // when

    // don't wait until the channel is opened
    clientTransport.registerEndpoint(NODE_ID1, SERVER_ADDRESS1);

    final ActorFuture<ClientResponse> responseFuture =
        clientTransport.getOutput().sendRequest(NODE_ID1, WRITER1, Duration.ofSeconds(2));

    // then
    final ClientResponse response = responseFuture.join();
    assertThatBuffer(response.getResponseBuffer()).hasBytes(BUF1);
  }

  @Test
  public void shouldCloseTransportWhileWaitingForResponse() throws Exception {
    // given
    final AtomicBoolean requestReceived = new AtomicBoolean(false);
    final AtomicBoolean transportClosed = new AtomicBoolean(false);

    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(
                    null,
                    (output, remoteAddress, buffer, offset, length, requestId) -> {
                      requestReceived.set(true);
                      return false;
                    }));

    clientTransport.registerEndpoint(NODE_ID1, SERVER_ADDRESS1);

    final BufferWriter writer = mock(BufferWriter.class);
    when(writer.getLength()).thenReturn(16);

    clientTransport.getOutput().sendRequest(NODE_ID1, writer);

    final Thread closerThread =
        new Thread(
            () -> {
              clientTransport.close();
              transportClosed.set(true);
            });
    waitUntil(requestReceived::get);

    // when
    closerThread.start();

    // then
    closerThread.join(1000L);
    assertThat(transportClosed).isTrue();
  }

  /**
   * It is important that all the controller actors are closed before transports and its surrounding
   * (send buffer) are closed. If this is not the case, the controller may write to the closed send
   * buffer and provoke memory access violations.
   */
  @Test
  public void shouldNotCloseTransportWhileRequestControllerIsActive() throws InterruptedException {
    // given
    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    final Object monitor = new Object();
    final AtomicBoolean isWaiting = new AtomicBoolean(false);

    final Predicate<DirectBuffer> blockingInspector =
        buf -> {
          synchronized (monitor) {
            isWaiting.compareAndSet(false, true);
            try {
              monitor.wait();
            } catch (final InterruptedException e) {
              throw new RuntimeException(e);
            }
            return false;
          }
        };

    clientTransport.registerEndpoint(NODE_ID1, SERVER_ADDRESS1);

    final ActorFuture<ClientResponse> responseFuture =
        clientTransport
            .getOutput()
            .sendRequestWithRetry(
                () -> NODE_ID1, blockingInspector, WRITER1, Duration.ofSeconds(30));

    waitUntil(isWaiting::get);

    // when
    final ActorFuture<Void> closeFuture = clientTransport.closeAsync();

    // then
    Thread.sleep(1000); // transport should not close in this time

    assertThat(closeFuture).isNotDone();
    assertThat(responseFuture).isNotDone();

    // and when
    synchronized (monitor) {
      monitor.notifyAll();
    }

    // then
    waitUntil(closeFuture::isDone);

    assertThat(closeFuture).isDone();
    assertThat(responseFuture).isDone();
  }

  @Test
  public void shouldCloseTransportWithUnreachableRemote() {
    // given
    clientTransport.registerEndpoint(1, new SocketAddress(SocketUtil.getNextAddress()));

    // when
    try {
      clientTransport.closeAsync().get(10, TimeUnit.SECONDS);
    } catch (final InterruptedException | ExecutionException | TimeoutException e) {
      fail("Could not close transport in time", e);
    }
  }

  @Test
  public void shouldSendRequestToNodeId() {
    // given
    final int nodeId = 123;
    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    clientTransport.registerEndpoint(nodeId, SERVER_ADDRESS1);

    // when
    final ActorFuture<ClientResponse> request =
        clientTransport.getOutput().sendRequest(nodeId, WRITER1);

    // then
    final ClientResponse response = request.join();
    assertThatBuffer(response.getResponseBuffer()).hasBytes(BUF1);
  }

  @Test
  public void shouldSendRequestToNodeIdWithRetries() {
    // given
    final int nodeId = 123;
    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    clientTransport.registerEndpoint(nodeId, SERVER_ADDRESS1);

    // when
    final ActorFuture<ClientResponse> request =
        clientTransport.getOutput().sendRequest(nodeId, WRITER1, Duration.ofSeconds(10));

    // then
    final ClientResponse response = request.join();
    assertThatBuffer(response.getResponseBuffer()).hasBytes(BUF1);
  }

  @Test
  public void shouldTimeoutRequestToUnknownNodeIdWithRetries() {
    // given
    final ClientOutput output = clientTransport.getOutput();

    // when
    final ActorFuture<ClientResponse> responseFuture =
        output.sendRequest(123, WRITER1, Duration.ofMillis(500));

    // then
    assertThatThrownBy(responseFuture::join).hasMessageContaining("Request timed out after PT0.5S");
  }

  @Test
  public void shouldRetryAfterTimeoutWhenNodeIdSupplierReturnsNull() {
    // given
    final int nodeId = 123;
    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    clientTransport.registerEndpointAndAwaitChannel(NODE_ID1, SERVER_ADDRESS1);

    final AtomicBoolean attemptToggle = new AtomicBoolean();
    final Supplier<Integer> nodeIdSupplier =
        () -> {
          if (attemptToggle.getAndSet(true)) {
            return nodeId;
          } else {
            clientTransport.registerEndpoint(nodeId, SERVER_ADDRESS1);
            return null;
          }
        };

    // when
    final ActorFuture<ClientResponse> responseFuture =
        clientTransport
            .getOutput()
            .sendRequestWithRetry(nodeIdSupplier, b -> false, WRITER1, Duration.ofSeconds(2));

    final ClientResponse response = responseFuture.join();
    assertThatBuffer(response.getResponseBuffer()).hasBytes(BUF1);
  }

  @Test
  public void shouldNotCreateChannelsWhenEndpointIsDeactivated() throws InterruptedException {
    // given
    final int nodeId = 123;
    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    final RecordingChannelListener channelListener = new RecordingChannelListener();
    clientTransport.registerChannelListener(channelListener).join();

    clientTransport.registerEndpoint(nodeId, SERVER_ADDRESS1);

    waitUntil(
        () ->
            channelListener.getOpenedConnections().stream().anyMatch(this::containsServerAddress1));

    // when
    clientTransport.deactivateEndpoint(nodeId);
    clientTransport.closeAllChannels().join();

    // then
    waitUntil(
        () ->
            channelListener.getClosedConnections().stream().anyMatch(this::containsServerAddress1));
    Thread.sleep(1000L); // timeout for potential reconnection of channel

    // no new channel was connected
    assertThat(channelListener.getOpenedConnections()).hasSize(1);
  }

  @Test
  public void shouldReopenChannelAfterEndpointReactivation() {
    // given
    final int nodeId = 123;
    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    final RecordingChannelListener channelListener = new RecordingChannelListener();
    clientTransport.registerChannelListener(channelListener).join();

    clientTransport.registerEndpoint(nodeId, SERVER_ADDRESS1);
    waitUntil(
        () ->
            channelListener.getOpenedConnections().stream().anyMatch(this::containsServerAddress1));

    clientTransport.deactivateEndpoint(nodeId);
    clientTransport.closeAllChannels().join();

    // when
    clientTransport.registerEndpoint(nodeId, SERVER_ADDRESS1);

    // then
    waitUntil(() -> channelListener.getOpenedConnections().size() >= 2);
    final long openedConnections =
        channelListener.getOpenedConnections().stream()
            .filter(this::containsServerAddress1)
            .count();
    assertThat(openedConnections).isGreaterThanOrEqualTo(2);
  }

  private boolean containsServerAddress1(final RemoteAddress remoteAddress) {
    return SERVER_ADDRESS1.equals(remoteAddress.getAddress());
  }

  protected class SendMessagesHandler implements ServerMessageHandler {
    final int numMessagesToSend;
    int messagesSent;
    final BufferWriter writer;

    public SendMessagesHandler(final int numMessagesToSend, final DirectBuffer messageToSend) {
      this.numMessagesToSend = numMessagesToSend;
      messagesSent = 0;
      writer = writerFor(messageToSend);
    }

    @Override
    public boolean onMessage(
        final ServerOutput output,
        final RemoteAddress remoteAddress,
        final DirectBuffer buffer,
        final int offset,
        final int length) {

      final int remoteStreamId = remoteAddress.getStreamId();
      for (int i = messagesSent; i < numMessagesToSend; i++) {
        if (output.sendMessage(remoteStreamId, writer)) {
          messagesSent++;
        } else {
          return false;
        }
      }

      return true;
    }
  }
}
