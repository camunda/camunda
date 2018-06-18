/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.transport;

import static io.zeebe.test.util.BufferAssert.assertThatBuffer;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.*;

import io.zeebe.dispatcher.*;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.test.util.io.FailingBufferWriter;
import io.zeebe.test.util.io.FailingBufferWriter.FailingBufferWriterException;
import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.transport.impl.TransportHeaderDescriptor;
import io.zeebe.transport.util.*;
import io.zeebe.util.ByteValue;
import io.zeebe.util.buffer.*;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.mockito.ArgumentMatchers;

public class ClientTransportTest {
  private ControlledActorClock clock = new ControlledActorClock();
  public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(3, clock);
  public AutoCloseableRule closeables = new AutoCloseableRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(actorSchedulerRule).around(closeables);

  public static final DirectBuffer BUF1 = BufferUtil.wrapBytes(1, 2, 3, 4);
  public static final SocketAddress SERVER_ADDRESS1 = new SocketAddress("localhost", 51115);
  public static final SocketAddress SERVER_ADDRESS2 = new SocketAddress("localhost", 51116);

  public static final int REQUEST_POOL_SIZE = 4;
  public static final ByteValue BUFFER_SIZE = ByteValue.ofKilobytes(16);
  public static final int MESSAGES_REQUIRED_TO_SATURATE_SEND_BUFFER =
      (int) BUFFER_SIZE.toBytes() / BUF1.capacity();

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
        Transports.newClientTransport()
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
      Function<ServerTransportBuilder, ServerTransport> builderConsumer) {
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
  public void shouldOpenChannelOnRegistrationOfRemote() {
    // given
    final ControllableServerTransport serverTransport = buildControllableServerTransport();
    serverTransport.listenOn(SERVER_ADDRESS1);
    final RecordingChannelListener channelListener = new RecordingChannelListener();
    clientTransport.registerChannelListener(channelListener);

    // when
    final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);

    // then
    waitUntil(() -> channelListener.getOpenedConnections().size() == 1);

    assertThat(channelListener.getOpenedConnections()).hasSize(1);
    assertThat(channelListener.getOpenedConnections().get(0)).isEqualTo(remoteAddress);
  }

  @Test
  public void shouldUseSameChannelForConsecutiveRequestsToSameRemote() {
    // given
    final ControllableServerTransport serverTransport = buildControllableServerTransport();
    serverTransport.listenOn(SERVER_ADDRESS1);

    final RemoteAddress remote = clientTransport.registerRemoteAndAwaitChannel(SERVER_ADDRESS1);

    final ClientOutput output = clientTransport.getOutput();
    output.sendRequest(remote, new DirectBufferWriter().wrap(BUF1));
    output.sendRequest(remote, new DirectBufferWriter().wrap(BUF1));

    final AtomicInteger messageCounter = serverTransport.acceptNextConnection(SERVER_ADDRESS1);

    // when
    TestUtil.doRepeatedly(() -> serverTransport.receive(SERVER_ADDRESS1))
        .until((r) -> messageCounter.get() == 2);

    // then
    assertThat(serverTransport.getClientChannels(SERVER_ADDRESS1)).hasSize(1);
  }

  @Test
  public void shouldUseDifferentChannelsForDifferentRemotes() {
    // given
    final ControllableServerTransport serverTransport = buildControllableServerTransport();
    serverTransport.listenOn(SERVER_ADDRESS1);
    serverTransport.listenOn(SERVER_ADDRESS2);
    final ClientOutput output = clientTransport.getOutput();

    final RemoteAddress remote1 = clientTransport.registerRemoteAndAwaitChannel(SERVER_ADDRESS1);
    final RemoteAddress remote2 = clientTransport.registerRemoteAndAwaitChannel(SERVER_ADDRESS2);

    output.sendRequest(remote1, new DirectBufferWriter().wrap(BUF1));
    output.sendRequest(remote2, new DirectBufferWriter().wrap(BUF1));

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
    clientTransport.registerRemoteAndAwaitChannel(SERVER_ADDRESS1);

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

    clientTransport.registerRemoteAddress(SERVER_ADDRESS1);
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
    final RemoteAddress remote = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);

    final ClientOutput output = clientTransport.getOutput();

    // when
    final ActorFuture<ClientResponse> responseFuture =
        output.sendRequest(remote, new DirectBufferWriter().wrap(BUF1), Duration.ofMillis(500));

    // then
    assertThatThrownBy(() -> responseFuture.join())
        .hasMessageContaining("Request timed out after PT0.5S");
  }

  @Test
  public void shouldOpenRequestWhenClientRequestPoolCapacityIsExceeded() {
    // given
    final ClientOutput clientOutput = clientTransport.getOutput();
    final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);

    for (int i = 0; i < REQUEST_POOL_SIZE; i++) {
      clientOutput.sendRequest(remoteAddress, new DirectBufferWriter().wrap(BUF1));
    }

    // when
    final ActorFuture<ClientResponse> responseFuture =
        clientOutput.sendRequest(remoteAddress, new DirectBufferWriter().wrap(BUF1));

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
                new ClientMessageHandler() {
                  @Override
                  public boolean onMessage(
                      ClientOutput output,
                      RemoteAddress remoteAddress,
                      DirectBuffer buffer,
                      int offset,
                      int length) {
                    numInvocations.incrementAndGet();
                    return consumeMessage.getAndSet(true);
                  }
                })
            .join();

    // when simulating a received message
    doRepeatedly(() -> clientReceiveBuffer.offer(BUF1)).until(p -> p >= 0);

    // then handler has been invoked twice, once when the message was postponed, and once when it
    // was consumed
    doRepeatedly(() -> subscription.poll()).until(i -> i != 0);
    assertThat(numInvocations.get()).isEqualTo(2);
  }

  @Test
  public void shouldPostponeMessagesOnReceiveBufferBackpressure() throws InterruptedException {
    // given
    final int maximumMessageLength =
        clientReceiveBuffer.getMaxFrameLength()
            - TransportHeaderDescriptor.HEADER_LENGTH
            - 1; // https://github.com/zeebe-io/zb-dispatcher/issues/21

    final DirectBuffer largeBuf = new UnsafeBuffer(new byte[maximumMessageLength]);

    final int messagesToExhaustReceiveBuffer =
        ((int) BUFFER_SIZE.toBytes() / largeBuf.capacity()) + 1;
    final SendMessagesHandler handler =
        new SendMessagesHandler(messagesToExhaustReceiveBuffer, largeBuf);

    buildServerTransport(
        b -> b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress()).build(handler, null));

    final RecordingMessageHandler clientHandler = new RecordingMessageHandler();
    final ClientInputMessageSubscription clientSubscription =
        clientTransport.openSubscription("foo", clientHandler).join();

    // triggering the server pushing a the messages
    final RemoteAddress remote = clientTransport.registerRemoteAndAwaitChannel(SERVER_ADDRESS1);
    clientTransport
        .getOutput()
        .sendMessage(new TransportMessage().remoteAddress(remote).buffer(BUF1));

    TransportTestUtil.waitUntilExhausted(clientReceiveBuffer);
    Thread.sleep(200L); // give transport a bit of time to try to push more messages on top

    // when
    final AtomicInteger receivedMessages = new AtomicInteger(0);
    doRepeatedly(
            () -> {
              final int polledMessages = clientSubscription.poll();
              return receivedMessages.addAndGet(polledMessages);
            })
        .until(m -> m == messagesToExhaustReceiveBuffer);

    // then
    assertThat(receivedMessages.get()).isEqualTo(messagesToExhaustReceiveBuffer);
  }

  @Test
  public void shouldNotBlockAllRequestsWhenOneRemoteIsNotReachable() {
    // given
    final ControllableServerTransport serverTransport = buildControllableServerTransport();
    serverTransport.listenOn(SERVER_ADDRESS1);

    final RemoteAddress remote1 = clientTransport.registerRemoteAndAwaitChannel(SERVER_ADDRESS1);
    final RemoteAddress remote2 = clientTransport.registerRemoteAddress(SERVER_ADDRESS2);

    final AtomicInteger messageCounter = serverTransport.acceptNextConnection(SERVER_ADDRESS1);

    final ClientOutput output = clientTransport.getOutput();

    // when
    output.sendRequest(remote2, new DirectBufferWriter().wrap(BUF1));
    output.sendRequest(remote1, new DirectBufferWriter().wrap(BUF1));

    // then blocked request 1 should not block sending request 2
    doRepeatedly(() -> serverTransport.receive(SERVER_ADDRESS1))
        .until(i -> messageCounter.get() > 0);
    assertThat(messageCounter.get()).isGreaterThanOrEqualTo(0);
  }

  @Test
  public void shouldNotCreateChannelsWhenRemoteAddressIsRetired() throws InterruptedException {
    // given
    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    final RecordingChannelListener channelListener = new RecordingChannelListener();
    clientTransport.registerChannelListener(channelListener).join();

    final RemoteAddress remote = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);

    waitUntil(() -> channelListener.getOpenedConnections().contains(remote));

    // when
    clientTransport.retireRemoteAddress(remote);
    clientTransport.closeAllChannels().join();

    // then
    waitUntil(() -> channelListener.getClosedConnections().contains(remote));
    Thread.sleep(1000L); // timeout for potential reconnection of channel

    // no new channel was connected
    assertThat(channelListener.getOpenedConnections()).hasSize(1);
  }

  @Test
  public void shouldNotCreateChannelsWhenRemoteAddressIsDeactivated() throws InterruptedException {
    // given
    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    final RecordingChannelListener channelListener = new RecordingChannelListener();
    clientTransport.registerChannelListener(channelListener).join();

    final RemoteAddress remote = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);

    waitUntil(() -> channelListener.getOpenedConnections().contains(remote));

    // when
    clientTransport.deactivateRemoteAddress(remote);
    clientTransport.closeAllChannels().join();

    // then
    waitUntil(() -> channelListener.getClosedConnections().contains(remote));
    Thread.sleep(1000L); // timeout for potential reconnection of channel

    // no new channel was connected
    assertThat(channelListener.getOpenedConnections()).hasSize(1);
  }

  @Test
  public void shouldReopenChannelAfterReactivation() {
    // given
    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    final RecordingChannelListener channelListener = new RecordingChannelListener();
    clientTransport.registerChannelListener(channelListener).join();

    final RemoteAddress remote = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);
    waitUntil(() -> channelListener.getOpenedConnections().contains(remote));

    clientTransport.deactivateRemoteAddress(remote);
    clientTransport.closeAllChannels().join();

    // when
    clientTransport.registerRemoteAddress(SERVER_ADDRESS1);

    // then
    waitUntil(() -> channelListener.getOpenedConnections().size() >= 2);
    assertThat(channelListener.getOpenedConnections()).contains(remote, remote);
  }

  @Test
  public void shouldMakeRequestWithRetries() {
    // given
    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    final RemoteAddress remote1 = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);

    // when
    final ActorFuture<ClientResponse> request =
        clientTransport
            .getOutput()
            .sendRequest(remote1, new DirectBufferWriter().wrap(BUF1), Duration.ofSeconds(10));

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

    final RemoteAddress remote1 = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);

    // when/then
    assertThatThrownBy(
            () -> clientTransport.getOutput().sendRequest(remote1, new FailingBufferWriter()))
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

    final RemoteAddress remote = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);
    final ActorFuture<ClientResponse> response =
        clientTransport.getOutput().sendRequest(remote, writer);

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
  public void shouldSendMultipleRequests() throws InterruptedException {
    // given
    final BufferWriter writer = mock(BufferWriter.class);
    when(writer.getLength()).thenReturn(16);

    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    final RemoteAddress remote = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);

    for (int i = 0; i < 10; i++) {
      clientTransport.getOutput().sendRequest(remote, writer).join();
    }
  }

  @Test
  public void shouldSendMultipleRequestsAsync() throws InterruptedException {
    // given
    final BufferWriter writer = mock(BufferWriter.class);
    when(writer.getLength()).thenReturn(16);

    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    final RemoteAddress remote = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);

    final List<ActorFuture<ClientResponse>> responseList = new ArrayList<>();

    for (int i = 0; i < 10; i++) {
      responseList.add(clientTransport.getOutput().sendRequest(remote, writer));
    }

    responseList.forEach(ActorFuture::join);
  }

  @Test
  public void shouldProvideResponseProperties() throws InterruptedException {
    // given
    final BufferWriter writer = new DirectBufferWriter().wrap(BUF1);

    final AtomicLong capturedRequestId = new AtomicLong();

    final EchoRequestResponseHandler requestHandler =
        new EchoRequestResponseHandler() {
          @Override
          public boolean onRequest(
              ServerOutput output,
              RemoteAddress remoteAddress,
              DirectBuffer buffer,
              int offset,
              int length,
              long requestId) {
            capturedRequestId.set(requestId);

            return super.onRequest(output, remoteAddress, buffer, offset, length, requestId);
          }
        };

    buildServerTransport(
        b -> b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress()).build(null, requestHandler));

    final RemoteAddress remote = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);

    // when
    final ClientResponse response = clientTransport.getOutput().sendRequest(remote, writer).join();

    // then
    assertThat(response.getRemoteAddress().getAddress()).isEqualTo(SERVER_ADDRESS1);
    assertThat(response.getRequestId()).isEqualTo(capturedRequestId.get());
    assertThat(response.getResponseBuffer()).isEqualTo(BUF1);
  }

  @Test
  public void shouldSendMultipleMessages() throws InterruptedException {
    // given
    final BufferWriter writer = mock(BufferWriter.class);
    when(writer.getLength()).thenReturn(16);

    final RecordingMessageHandler messageHandler = new RecordingMessageHandler();

    buildServerTransport(
        b -> {
          return b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress()).build(messageHandler, null);
        });

    final RemoteAddress remote = clientTransport.registerRemoteAndAwaitChannel(SERVER_ADDRESS1);

    for (int i = 0; i < 10; i++) {
      final TransportMessage message = new TransportMessage();

      message.writer(writer);
      message.remoteAddress(remote);

      clientTransport.getOutput().sendMessage(message);
    }

    waitUntil(() -> messageHandler.numReceivedMessages() == 10);
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

    final RemoteAddress remote = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);

    // when
    final ActorFuture<ClientResponse> clientRequestActorFuture =
        clientTransport.getOutput().sendRequest(remote, writer, Duration.ofSeconds(10));

    // then
    doRepeatedly(() -> clock.addTime(Duration.ofSeconds(10)))
        .until((v) -> clientRequestActorFuture.isDone());

    assertThatThrownBy(() -> clientRequestActorFuture.join())
        .hasMessageContaining("Request timed out after PT10S");
  }

  @Test
  public void shouldRetryAfterTimeoutWhenAddressSupplierReturnsNull() {
    // given
    buildServerTransport(
        b ->
            b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, new EchoRequestResponseHandler()));

    final RemoteAddress remote = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);

    final AtomicInteger attemptCounter = new AtomicInteger(0);
    final Supplier<RemoteAddress> addressSupplier =
        () -> attemptCounter.getAndIncrement() == 0 ? null : remote;

    // when
    final ActorFuture<ClientResponse> responseFuture =
        clientTransport
            .getOutput()
            .sendRequestWithRetry(
                addressSupplier,
                b -> false,
                new DirectBufferWriter().wrap(BUF1),
                Duration.ofSeconds(2));

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

    final RemoteAddress remote = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);

    final BufferWriter writer = mock(BufferWriter.class);
    when(writer.getLength()).thenReturn(16);

    clientTransport.getOutput().sendRequest(remote, writer);

    final Thread closerThread =
        new Thread(
            () -> {
              clientTransport.close();
              transportClosed.set(true);
            });
    waitUntil(() -> requestReceived.get());

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
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
            return false;
          }
        };

    final RemoteAddress remote = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);

    final ActorFuture<ClientResponse> responseFuture =
        clientTransport
            .getOutput()
            .sendRequestWithRetry(
                () -> remote,
                blockingInspector,
                new DirectBufferWriter().wrap(BUF1),
                Duration.ofSeconds(30));

    waitUntil(() -> isWaiting.get());

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
    waitUntil(() -> closeFuture.isDone());

    assertThat(closeFuture).isDone();
    assertThat(responseFuture).isDone();
  }

  @Test
  public void shouldCloseTransportWithUnreachableRemote() {
    // given
    clientTransport.registerRemoteAddress(SocketAddress.from("foo:123"));

    // when
    try {
      clientTransport.closeAsync().get(10, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      fail("Could not close transport in time", e);
    }
  }

  protected class CountFragmentsHandler implements FragmentHandler {

    protected AtomicInteger i = new AtomicInteger(0);

    @Override
    public int onFragment(
        DirectBuffer buffer, int offset, int length, int streamId, boolean isMarkedFailed) {
      i.incrementAndGet();
      return FragmentHandler.CONSUME_FRAGMENT_RESULT;
    }

    public int getCount() {
      return i.get();
    }
  }

  protected class SendMessagesHandler implements ServerMessageHandler {
    final int numMessagesToSend;
    int messagesSent;
    final TransportMessage message;

    public SendMessagesHandler(int numMessagesToSend, DirectBuffer messageToSend) {
      this.numMessagesToSend = numMessagesToSend;
      this.messagesSent = 0;
      this.message = new TransportMessage().buffer(messageToSend);
    }

    @Override
    public boolean onMessage(
        ServerOutput output,
        RemoteAddress remoteAddress,
        DirectBuffer buffer,
        int offset,
        int length) {
      message.remoteAddress(remoteAddress);
      for (int i = messagesSent; i < numMessagesToSend; i++) {
        if (output.sendMessage(message)) {
          messagesSent++;
        } else {
          return false;
        }
      }

      return true;
    }
  }
}
