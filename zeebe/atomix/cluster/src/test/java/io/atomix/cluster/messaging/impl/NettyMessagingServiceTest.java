/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.cluster.messaging.impl;

import static org.assertj.core.api.Assertions.*;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;
import com.sun.security.auth.module.UnixSystem;
import io.atomix.cluster.messaging.HeartbeatRequestDecoder;
import io.atomix.cluster.messaging.HeartbeatResponseDecoder;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.MessageHeaderDecoder;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.MessagingException;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.agrona.collections.MutableReference;
import org.agrona.concurrent.UnsafeBuffer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Netty messaging service test. */
final class NettyMessagingServiceTest {
  private static final String CLUSTER_NAME = "zeebe";
  private static final int UID_COLUMN = 7;
  @AutoClose private final MeterRegistry registry = new SimpleMeterRegistry();

  private MessagingConfig defaultConfig() {
    return new MessagingConfig()
        .setShutdownQuietPeriod(Duration.ofMillis(50))
        .setHeartbeatInterval(Duration.ofMillis(50))
        .setHeartbeatTimeout(Duration.ofMillis(500));
  }

  private String nextSubject() {
    return UUID.randomUUID().toString();
  }

  private NettyMessagingService newMessagingService() {
    return new NettyMessagingService(
        CLUSTER_NAME, newAddress(), defaultConfig(), "testingPrefix", registry);
  }

  private Address newAddress() {
    return Address.from(SocketUtil.getNextAddress().getPort());
  }

  private void startMessagingServices(final NettyMessagingService... services) {
    CompletableFuture.allOf(
            Stream.of(services)
                .parallel()
                .map(ManagedMessagingService::start)
                .toArray(CompletableFuture[]::new))
        .join();
  }

  private long udpSocketCount() throws IOException {
    final var pid = ProcessHandle.current().pid();
    final var udpPath = Path.of("/proc/" + pid + "/net/udp");
    final var udp6Path = Path.of("/proc/" + pid + "/net/udp6");
    return udpSocketCount(udpPath) + udpSocketCount(udp6Path);
  }

  private long udpSocketCount(final Path path) throws IOException {
    // we can drop the first line since it's just the headers
    final var lines = Files.readAllLines(path);
    final var uid = new UnixSystem().getUid();
    lines.remove(0);

    // the UDP file is a table, where each row is whitespace separated and here we're only
    // interested in the lines where the UID column happens to match our user ID
    return lines.stream()
        .filter(
            line -> {
              final String[] columns = line.trim().split("\\s+");
              return Long.parseLong(columns[UID_COLUMN]) == uid;
            })
        .count();
  }

  @Nested
  final class HeartbeatPayloadTest {

    static Stream<Arguments> sendPayloadArguments() {
      final Supplier<Stream<Boolean>> booleans = () -> Stream.of(true, false);
      return booleans.get().flatMap(b1 -> booleans.get().map(b2 -> Arguments.of(b1, b2)));
    }

    @ParameterizedTest
    @MethodSource("sendPayloadArguments")
    void shouldSendHeartbeatPayloadIfConfigured(
        final boolean clientSupportsPayload, final boolean serverSupportsPayload) throws Exception {
      try (final var clientNetty = newMessagingService();
          final var serverNetty = newMessagingService()) {
        clientNetty.enableHeartbeatsForwarding();
        serverNetty.enableHeartbeatsForwarding();
        if (!clientSupportsPayload) {
          clientNetty.noHeartbeatPayload();
        }
        if (!serverSupportsPayload) {
          serverNetty.noHeartbeatPayload();
        }
        clientNetty.start().join();
        serverNetty.start().join();

        // Track heartbeats from client - capture both empty and payload heartbeats
        final var heartbeatFromClient = new MutableReference<HeartbeatRequestDecoder>(null);
        final var emptyHeartbeatFromClientReceived = new AtomicBoolean(false);
        serverNetty.registerHandler(
            HeartbeatHandler.HEARTBEAT_SUBJECT,
            (BiConsumer<Address, byte[]>)
                (addr, payload) -> {
                  if (payload != null && payload.length > 0) {
                    heartbeatFromClient.set(
                        new HeartbeatRequestDecoder()
                            .wrapAndApplyHeader(
                                new UnsafeBuffer(payload), 0, new MessageHeaderDecoder()));
                  } else {
                    emptyHeartbeatFromClientReceived.set(true);
                  }
                },
            Runnable::run);

        // when - connect and intercept heartbeat responses from server
        final var heartbeatResponseFromServer =
            new MutableReference<HeartbeatResponseDecoder>(null);
        final var emptyHeartbeatResponseFromServerReceived = new AtomicBoolean(false);
        final var clientChannel =
            clientNetty.getChannelPool().getChannel(serverNetty.address(), "test").join();

        // Wait for heartbeat handler to be installed (after setup completes)
        Awaitility.await("Until heartbeat handler is installed")
            .until(
                () ->
                    clientChannel.pipeline().get(HeartbeatHandler.HEARTBEAT_HANDLER_NAME) != null);

        // add interceptor before heartbeat handler to capture ProtocolReply
        final var interceptingHandler =
            new ChannelInboundHandlerAdapter() {
              @Override
              public void channelRead(final ChannelHandlerContext ctx, final Object msg)
                  throws Exception {
                if (msg instanceof final ProtocolReply reply) {
                  if (reply.payload() != null && reply.payload().length > 0) {
                    heartbeatResponseFromServer.set(
                        new HeartbeatResponseDecoder()
                            .wrapAndApplyHeader(
                                new UnsafeBuffer(reply.payload()), 0, new MessageHeaderDecoder()));
                  } else {
                    emptyHeartbeatResponseFromServerReceived.set(true);
                  }
                }
                ctx.fireChannelRead(msg);
              }
            };
        clientChannel
            .pipeline()
            .addBefore(
                HeartbeatHandler.HEARTBEAT_HANDLER_NAME, "test-interceptor", interceptingHandler);

        // then - verify heartbeats are exchanged with correct payload based on configuration
        Awaitility.await("Until heartbeats are received")
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(
                () -> {
                  if (clientSupportsPayload && serverSupportsPayload) {
                    // Both support payloads: verify payloads with timestamps
                    assertThat(heartbeatFromClient.get())
                        .isNotNull()
                        .satisfies(heartbeat -> assertThat(heartbeat.sentAt()).isPositive());
                    assertThat(heartbeatResponseFromServer.get())
                        .isNotNull()
                        .satisfies(heartbeat -> assertThat(heartbeat.receivedAt()).isPositive());
                  } else {
                    // At least one party doesn't support payloads: negotiation requires both to
                    // agree, so empty heartbeats are exchanged
                    assertThat(emptyHeartbeatFromClientReceived.get())
                        .as(
                            "Client should send empty heartbeats when negotiation results in no payload support")
                        .isTrue();
                    assertThat(emptyHeartbeatResponseFromServerReceived.get())
                        .as(
                            "Server should send empty heartbeat responses when negotiation results in no payload support")
                        .isTrue();
                  }
                });
      }
    }
  }

  @Nested
  final class VersionTest {
    @AutoClose private final NettyMessagingService nettyv11 = newMessagingService();
    @AutoClose private final NettyMessagingService nettyv12 = newMessagingService();
    @AutoClose private final NettyMessagingService nettyv21 = newMessagingService();
    @AutoClose private final NettyMessagingService nettyv22 = newMessagingService();

    @BeforeEach
    void beforeEach() {
      startMessagingServices(nettyv11, nettyv12, nettyv21, nettyv22);
    }

    @Test
    void testV1() throws Exception {
      final String subject;
      final byte[] payload = "Hello world!".getBytes();
      final byte[] response;

      subject = nextSubject();
      nettyv11.registerHandler(
          subject, (address, bytes) -> CompletableFuture.completedFuture(bytes));
      response =
          nettyv12.sendAndReceive(nettyv11.address(), subject, payload).get(10, TimeUnit.SECONDS);
      assertThat(response).isEqualTo(payload);
    }

    @Test
    void testV2() throws Exception {
      final String subject;
      final byte[] payload = "Hello world!".getBytes();
      final byte[] response;

      subject = nextSubject();
      nettyv21.registerHandler(
          subject, (address, bytes) -> CompletableFuture.completedFuture(bytes));
      response =
          nettyv22.sendAndReceive(nettyv21.address(), subject, payload).get(10, TimeUnit.SECONDS);
      assertThat(response).isEqualTo(payload);
    }

    @Test
    void testVersionNegotiation() throws Exception {
      String subject;
      final byte[] payload = "Hello world!".getBytes();
      byte[] response;

      subject = nextSubject();
      nettyv11.registerHandler(
          subject, (address, bytes) -> CompletableFuture.completedFuture(bytes));
      response =
          nettyv21.sendAndReceive(nettyv11.address(), subject, payload).get(10, TimeUnit.SECONDS);
      assertThat(response).isEqualTo(payload);

      subject = nextSubject();
      nettyv22.registerHandler(
          subject, (address, bytes) -> CompletableFuture.completedFuture(bytes));
      response =
          nettyv12.sendAndReceive(nettyv22.address(), subject, payload).get(10, TimeUnit.SECONDS);
      assertThat(response).isEqualTo(payload);
    }
  }

  @Nested
  final class SingleInstanceTest {
    @Test
    void testSendAsyncToUnresolvable() throws Exception {
      // given
      final var subject = nextSubject();
      try (final var service = newMessagingService()) {
        final var unresolvable = Address.from("unknown.local", service.address().port());

        // when
        assertThat(service.start()).succeedsWithin(Duration.ofSeconds(5));
        final var response = service.sendAsync(unresolvable, subject, "hello world".getBytes());

        // then
        assertThat(response).failsWithin(Duration.ofSeconds(10));
      }
    }

    @Test
    void shouldNotBindToAdvertisedAddress() throws Exception {
      // given
      final var bindingAddress = newAddress();
      final MessagingConfig config = defaultConfig();
      config.setInterfaces(List.of(bindingAddress.host()));
      config.setPort(bindingAddress.port());

      // when
      final var nonBindableAddress = new Address("invalid.host", 1);
      try (final var service =
          new NettyMessagingService(
              "test", nonBindableAddress, config, "testingPrefix", registry)) {
        // then - should not fail by using advertisedAddress for binding
        assertThat(service.start()).succeedsWithin(Duration.ofSeconds(5));
        assertThat(service.bindingAddresses()).contains(bindingAddress);
        assertThat(service.address()).isEqualTo(nonBindableAddress);
      }
    }
  }

  @Nested
  final class DualInstanceTest {
    @AutoClose private final NettyMessagingService netty1 = newMessagingService();
    @AutoClose private final NettyMessagingService netty2 = newMessagingService();

    @BeforeEach
    void beforeEach() {
      startMessagingServices(netty1, netty2);
    }

    @Test
    void testSendAndReceive() {
      final String subject = nextSubject();
      final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
      final AtomicReference<byte[]> request = new AtomicReference<>();
      final AtomicReference<Address> sender = new AtomicReference<>();

      final BiFunction<Address, byte[], byte[]> handler =
          (ep, data) -> {
            handlerInvoked.set(true);
            sender.set(ep);
            request.set(data);
            return "hello there".getBytes();
          };
      netty2.registerHandler(subject, handler, MoreExecutors.directExecutor());

      final CompletableFuture<byte[]> response =
          netty1.sendAndReceive(netty2.address(), subject, "hello world".getBytes());
      assertThat("hello there".getBytes()).isEqualTo(response.join());
      assertThat(handlerInvoked.get()).isTrue();
      assertThat(request.get()).asString().isEqualTo("hello world");
      assertThat(Arrays.equals(request.get(), "hello world".getBytes())).isTrue();
      assertThat(sender.get().tryResolveAddress()).isEqualTo(netty1.address().tryResolveAddress());
    }

    @Test
    void testSendAsync() {
      final var invalidAddress = Address.from("127.0.0.1", 5007);
      final var subject = nextSubject();
      final var latch1 = new CountDownLatch(1);
      CompletableFuture<Void> response =
          netty1.sendAsync(netty2.address(), subject, "hello world".getBytes());
      response.whenComplete(
          (r, e) -> {
            assertThat(e).isNull();
            latch1.countDown();
          });
      Uninterruptibles.awaitUninterruptibly(latch1);

      final CountDownLatch latch2 = new CountDownLatch(1);
      response = netty1.sendAsync(invalidAddress, subject, "hello world".getBytes());
      response.whenComplete(
          (r, e) -> {
            assertThat(e).isInstanceOf(ConnectException.class);
            latch2.countDown();
          });
      Uninterruptibles.awaitUninterruptibly(latch2);
    }

    @Test
    void shouldCompleteExistingRequestFutureExceptionallyWhenMessagingServiceIsClosed() {
      final String subject = nextSubject();
      final CompletableFuture<byte[]> response =
          netty1.sendAndReceive(
              netty2.address(), subject, "hello world".getBytes(), true, Duration.ofSeconds(5));

      // when
      netty1.stop().join();

      // then
      assertThat(response).isCompletedExceptionally();
    }

    @Test
    public void
        shouldCompleteExistingRequestWithKeepAliveExceptionallyWhenMessagingServiceIsClosed() {
      final String subject = nextSubject();
      final CompletableFuture<byte[]> response =
          netty1.sendAndReceive(
              netty2.address(), subject, "hello world".getBytes(), true, Duration.ofSeconds(5));

      // when
      netty1.stop().join();

      // then
      assertThat(response).isCompletedExceptionally();
    }

    @Test
    void shouldCompleteFutureExceptionallyIfMessagingServiceIsClosedInBetween() {
      final String subject = nextSubject();

      // when
      final var stopFuture = netty1.stop();
      final CompletableFuture<byte[]> response =
          netty1.sendAndReceive(
              netty2.address(), subject, "hello world".getBytes(), false, Duration.ofSeconds(5));

      stopFuture.join();

      // then
      assertThat(response).isCompletedExceptionally();
    }

    @Test
    void shouldCompleteRequestWithKeepAliveExceptionallyIfMessagingServiceIsClosedInBetween() {
      final String subject = nextSubject();

      // when
      final var stopFuture = netty1.stop();
      final CompletableFuture<byte[]> response =
          netty1.sendAndReceive(
              netty2.address(), subject, "hello world".getBytes(), true, Duration.ofSeconds(5));

      stopFuture.join();

      // then
      assertThat(response).isCompletedExceptionally();
    }

    @Test
    void shouldCompleteFutureExceptionallyIfMessagingServiceHasAlreadyClosed() {
      final String subject = nextSubject();
      netty1.stop().join();

      // when
      final CompletableFuture<byte[]> response =
          netty1.sendAndReceive(
              netty2.address(), subject, "hello world".getBytes(), false, Duration.ofSeconds(5));

      // then
      assertThat(response).isCompletedExceptionally();
    }

    @Test
    void shouldCompleteRequestWithKeepAliveExceptionallyIfMessagingServiceHasAlreadyClosed() {
      final String subject = nextSubject();
      netty1.stop().join();

      // when
      final CompletableFuture<byte[]> response =
          netty1.sendAndReceive(
              netty2.address(), subject, "hello world".getBytes(), true, Duration.ofSeconds(5));

      // then
      assertThat(response).isCompletedExceptionally();
    }

    @Test
    void testTransientSendAndReceive() {
      final String subject = nextSubject();
      final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
      final AtomicReference<byte[]> request = new AtomicReference<>();
      final AtomicReference<Address> sender = new AtomicReference<>();

      final BiFunction<Address, byte[], byte[]> handler =
          (ep, data) -> {
            handlerInvoked.set(true);
            sender.set(ep);
            request.set(data);
            return "hello there".getBytes();
          };
      netty2.registerHandler(subject, handler, MoreExecutors.directExecutor());

      final CompletableFuture<byte[]> response =
          netty1.sendAndReceive(netty2.address(), subject, "hello world".getBytes(), false);
      assertThat("hello there".getBytes()).isEqualTo(response.join());
      assertThat(handlerInvoked.get()).isTrue();
      assertThat(request.get()).asString().isEqualTo("hello world");
      assertThat(sender.get().tryResolveAddress()).isEqualTo(netty1.address().tryResolveAddress());
    }

    @Test
    void testSendAndReceiveWithFixedTimeout() {
      final String subject = nextSubject();
      final BiFunction<Address, byte[], CompletableFuture<byte[]>> handler =
          (ep, payload) -> new CompletableFuture<>();
      netty2.registerHandler(subject, handler);

      try {
        netty1
            .sendAndReceive(
                netty2.address(), subject, "hello world".getBytes(), Duration.ofSeconds(1))
            .join();
        fail("");
      } catch (final CompletionException e) {
        assertThat(e.getCause()).isInstanceOf(TimeoutException.class);
      }
    }

    @Test
    @Disabled
    void testSendAndReceiveWithExecutor() {
      final String subject = nextSubject();
      final ExecutorService completionExecutor =
          Executors.newSingleThreadExecutor(r -> new Thread(r, "completion-thread"));
      final ExecutorService handlerExecutor =
          Executors.newSingleThreadExecutor(r -> new Thread(r, "handler-thread"));
      final AtomicReference<String> handlerThreadName = new AtomicReference<>();
      final AtomicReference<String> completionThreadName = new AtomicReference<>();

      final CountDownLatch latch = new CountDownLatch(1);

      final BiFunction<Address, byte[], byte[]> handler =
          (ep, data) -> {
            handlerThreadName.set(Thread.currentThread().getName());
            try {
              latch.await();
            } catch (final InterruptedException e1) {
              Thread.currentThread().interrupt();
              fail("InterruptedException");
            }
            return "hello there".getBytes();
          };
      netty2.registerHandler(subject, handler, handlerExecutor);

      final CompletableFuture<byte[]> response =
          netty1.sendAndReceive(
              netty2.address(), subject, "hello world".getBytes(), completionExecutor);
      response.whenComplete((r, e) -> completionThreadName.set(Thread.currentThread().getName()));
      latch.countDown();

      // Verify that the message was request handling and response completion happens on the correct
      // thread.
      assertThat("hello there".getBytes()).isEqualTo(response.join());
      assertThat(completionThreadName.get()).isEqualTo("completion-thread");
      assertThat(handlerThreadName.get()).isEqualTo("handler-thread");
    }

    @Test
    void testNoRemoteHandlerException() {
      // given
      final var subject = nextSubject();
      final var expectedException = new MessagingException.NoRemoteHandler(subject);

      // when
      final CompletableFuture<byte[]> response =
          netty1.sendAndReceive(netty2.address(), subject, "fail".getBytes());

      // then
      assertThat(response)
          .failsWithin(Duration.ofSeconds(5))
          .withThrowableOfType(ExecutionException.class)
          .havingRootCause()
          .isInstanceOf(MessagingException.NoRemoteHandler.class)
          .withMessage(expectedException.getMessage());
    }

    @Test
    void testNoRemoteHandlerExceptionEmptyStringValue() {
      // given
      final var subject = "";
      final var expectedException = new MessagingException.NoRemoteHandler(null);

      // when
      final CompletableFuture<byte[]> response =
          netty1.sendAndReceive(netty2.address(), subject, "fail".getBytes());

      // then
      assertThat(response)
          .failsWithin(Duration.ofSeconds(5))
          .withThrowableOfType(ExecutionException.class)
          .havingRootCause()
          .isInstanceOf(MessagingException.NoRemoteHandler.class)
          .withMessage(expectedException.getMessage());
    }

    @Test
    void testRemoteHandlerFailure() {
      // given
      final var exceptionMessage = "foo bar";
      final var expectedException = new MessagingException.RemoteHandlerFailure(exceptionMessage);

      final BiFunction<Address, byte[], byte[]> handler =
          (ep, data) -> {
            throw new RuntimeException(exceptionMessage);
          };

      final var subject = nextSubject();
      netty2.registerHandler(subject, handler, MoreExecutors.directExecutor());

      // when
      final CompletableFuture<byte[]> response =
          netty1.sendAndReceive(netty2.address(), subject, "fail".getBytes());

      // then
      assertThat(response)
          .failsWithin(Duration.ofSeconds(5))
          .withThrowableOfType(ExecutionException.class)
          .havingRootCause()
          .isInstanceOf(MessagingException.RemoteHandlerFailure.class)
          .withMessage(expectedException.getMessage());
    }

    @Test
    void testRemoteHandlerFailureNullValue() {
      // given
      final var expectedException = new MessagingException.RemoteHandlerFailure(null);

      final BiFunction<Address, byte[], byte[]> handler =
          (ep, data) -> {
            throw new RuntimeException();
          };

      final var subject = nextSubject();
      netty2.registerHandler(subject, handler, MoreExecutors.directExecutor());

      // when
      final CompletableFuture<byte[]> response =
          netty1.sendAndReceive(netty2.address(), subject, "fail".getBytes());

      // then
      assertThat(response)
          .failsWithin(Duration.ofSeconds(5))
          .withThrowableOfType(ExecutionException.class)
          .havingRootCause()
          .isInstanceOf(MessagingException.RemoteHandlerFailure.class)
          .withMessage(expectedException.getMessage());
    }

    @Test
    void testRemoteHandlerFailureEmptyStringValue() {
      // given
      final var exceptionMessage = "";
      final var expectedException = new MessagingException.RemoteHandlerFailure(null);

      final BiFunction<Address, byte[], byte[]> handler =
          (ep, data) -> {
            throw new RuntimeException(exceptionMessage);
          };

      final var subject = nextSubject();
      netty2.registerHandler(subject, handler, MoreExecutors.directExecutor());

      // when
      final CompletableFuture<byte[]> response =
          netty1.sendAndReceive(netty2.address(), subject, "fail".getBytes());

      // then
      assertThat(response)
          .failsWithin(Duration.ofSeconds(5))
          .withThrowableOfType(ExecutionException.class)
          .havingRootCause()
          .isInstanceOf(MessagingException.RemoteHandlerFailure.class)
          .withMessage(expectedException.getMessage());
    }

    @Test
    void testCompletableRemoteHandlerFailure() {
      // given
      final var exceptionMessage = "foo bar";
      final var expectedException = new MessagingException.RemoteHandlerFailure(exceptionMessage);

      final var subject = nextSubject();
      netty2.registerHandler(
          subject,
          (address, bytes) ->
              CompletableFuture.failedFuture(new RuntimeException(exceptionMessage)));

      // when
      final CompletableFuture<byte[]> response =
          netty1.sendAndReceive(netty2.address(), subject, "fail".getBytes());

      // then
      assertThat(response)
          .failsWithin(Duration.ofSeconds(5))
          .withThrowableOfType(ExecutionException.class)
          .havingRootCause()
          .isInstanceOf(MessagingException.RemoteHandlerFailure.class)
          .withMessage(expectedException.getMessage());
    }

    @Test
    void testCompletableRemoteHandlerFailureNullValue() {
      // given
      final var expectedException = new MessagingException.RemoteHandlerFailure(null);

      final var subject = nextSubject();
      netty2.registerHandler(
          subject, (address, bytes) -> CompletableFuture.failedFuture(new RuntimeException()));

      // when
      final CompletableFuture<byte[]> response =
          netty1.sendAndReceive(netty2.address(), subject, "fail".getBytes());

      // then
      assertThat(response)
          .failsWithin(Duration.ofSeconds(5))
          .withThrowableOfType(ExecutionException.class)
          .havingRootCause()
          .isInstanceOf(MessagingException.RemoteHandlerFailure.class)
          .withMessage(expectedException.getMessage());
    }

    @Test
    void testCompletableRemoteHandlerFailureEmptyStringValue() {
      // given
      final var exceptionMessage = "";
      final var expectedException = new MessagingException.RemoteHandlerFailure(null);

      final var subject = nextSubject();
      netty2.registerHandler(
          subject,
          (address, bytes) ->
              CompletableFuture.failedFuture(new RuntimeException(exceptionMessage)));

      // when
      final CompletableFuture<byte[]> response =
          netty1.sendAndReceive(netty2.address(), subject, "fail".getBytes());

      // then
      assertThat(response)
          .failsWithin(Duration.ofSeconds(5))
          .withThrowableOfType(ExecutionException.class)
          .havingRootCause()
          .isInstanceOf(MessagingException.RemoteHandlerFailure.class)
          .withMessage(expectedException.getMessage());
    }

    @Test
    void shouldNotCreateNewChannelOnNewRequestAfterTimeout() {
      // given
      final var subject = nextSubject();
      final var timeoutOnCreate = Duration.ofSeconds(10);

      final var channelPool = netty1.getChannelPool();

      // grab the original channel, so we can assert it was not closed
      // it's also useful to send a successful request once, so we can ensure the channel exists
      // and set a lower timeout on the request we want specifically to time out
      netty2.registerHandler(subject, (address, bytes) -> new byte[0], Runnable::run);
      netty1
          .sendAndReceive(
              netty2.address(), subject, "get channel".getBytes(), true, timeoutOnCreate)
          .join();
      final var originalChannel = channelPool.getChannel(netty2.address(), subject).join();

      // set up handler which will always cause timeouts
      netty2.unregisterHandler(subject);
      netty2.registerHandler(subject, (address, bytes) -> new CompletableFuture<>());
      final CompletableFuture<byte[]> response =
          netty1.sendAndReceive(
              netty2.address(), subject, "fail".getBytes(), true, Duration.ofSeconds(1));
      assertThat(response)
          .failsWithin(Duration.ofSeconds(15))
          .withThrowableThat()
          .havingRootCause()
          .isInstanceOf(TimeoutException.class);

      // when - remote connection finally succeeds
      // give a generous time out on the second request, as creating a new channel can be slow at
      // times
      netty2.unregisterHandler(subject);
      netty2.registerHandler(subject, (address, bytes) -> new byte[0], Runnable::run);
      netty1.sendAndReceive(netty2.address(), subject, "success".getBytes(), true, timeoutOnCreate);

      // then
      final var currentChannel = channelPool.getChannel(netty2.address(), subject).join();
      assertThat(currentChannel).isEqualTo(originalChannel);
    }

    @EnabledOnOs(OS.LINUX)
    @RegressionTest("https://github.com/camunda/camunda/issues/14837")
    void shouldNotLeakUdpSockets() throws IOException {
      // given
      final var initialUdpSocketCount = udpSocketCount();
      // the configured amount of threads for Netty's epoll transport
      final var maxConnections = Runtime.getRuntime().availableProcessors() * 2;
      final var subject = nextSubject();
      netty2.registerHandler(
          subject, (address, bytes) -> new byte[0], MoreExecutors.directExecutor());

      // when
      for (int i = 0; i < maxConnections * 4; i++) {
        netty1.sendAndReceive(netty2.address(), subject, "hello world".getBytes(), false).join();
      }

      // then - there seems to be a slight amount more than maxConnections normally, but without the
      // fix this was way, way, way more, so it should be fine to allow a little bit more than the
      // expected max number of connections
      Awaitility.await("all sockets are closed")
          .untilAsserted(
              () ->
                  assertThat(udpSocketCount() - initialUdpSocketCount)
                      .isLessThan(maxConnections * 2L));
    }

    @Test
    void shouldGetChannelClosedWhenNotSendingHeartbeatsAfterAWhile() {
      // given
      final var subject = nextSubject();
      final var receivedHeartbeat = new AtomicBoolean(false);
      netty2.enableHeartbeatsForwarding();
      // register for heartbeats
      netty2.registerHandler(
          HeartbeatHandler.HEARTBEAT_SUBJECT,
          (addr, bytes) -> {
            receivedHeartbeat.set(true);
            return CompletableFuture.completedFuture(null);
          });
      final var clientChannel =
          netty1.getChannelPool().getChannel(netty2.address(), subject).join();

      Awaitility.await("Until first heartbeat has been received on the server")
          .until(receivedHeartbeat::get);

      // when - removing the `IdleStateHandler` from the pipeline such that `HeartBeatHandler` is
      // not triggered
      clientChannel.pipeline().remove(HeartbeatHandler.IDLE_STATE_HANDLER_NAME);

      // then - the other side notices a lack of heartbeats and closes the channel
      assertThat(clientChannel.closeFuture()).succeedsWithin(Duration.ofSeconds(5));
    }

    @Test
    void shouldNotCloseTheConnectionFromTheServerIfNoHeartbeatsIsReceived() {
      // given
      final var subject = nextSubject();
      netty2.enableHeartbeatsForwarding();
      final var receivedHeartbeats = new AtomicInteger(0);
      netty2.registerHandler(
          HeartbeatHandler.HEARTBEAT_SUBJECT,
          (addr, bytes) -> {
            receivedHeartbeats.incrementAndGet();
            return CompletableFuture.completedFuture(null);
          });
      // heartbeats are not sent from the client
      netty1.disableHeartbeats();
      // a client channel
      final var channel = netty1.getChannelPool().getChannel(netty2.address(), subject).join();

      // when
      // a request is made without heartbeats
      netty1.sendAndReceive(netty2.address(), subject, new byte[0], true);

      // then
      // the channel is not closed (because the future times out)
      final var timeout = defaultConfig().getHeartbeatTimeout().toSeconds() + 1;
      assertThatThrownBy(() -> channel.closeFuture().get(timeout, TimeUnit.SECONDS))
          .isInstanceOf(TimeoutException.class);
      assertThat(receivedHeartbeats.get()).isEqualTo(0);
    }

    @Test
    void shouldSendHeartbeatTimeoutFromClientToServer() throws Exception {
      // given
      final var subject = nextSubject();
      // new service with a different timeout;
      final var netty3Config = defaultConfig();
      // heartbeatTimeout on netty3 (server) is very small
      netty3Config
          .setHeartbeatTimeout(Duration.ofMillis(2))
          .setHeartbeatInterval(Duration.ofMillis(1));
      try (final var netty3 =
          new NettyMessagingService(
              CLUSTER_NAME, newAddress(), netty3Config, "testingPrefix", registry)) {
        startMessagingServices(netty3);
        // when
        final var clientChannel =
            netty1.getChannelPool().getChannel(netty3.address(), subject).join();
        // then
        // the channel stays open because netty3's config are ignored
        assertThatThrownBy(() -> clientChannel.closeFuture().get(1, TimeUnit.SECONDS))
            .isInstanceOf(TimeoutException.class);
      }
    }

    @Test
    void shouldNotCloseTheConnectionFromTheClientIfNoHeartbeatsIsReceived()
        throws InterruptedException {
      // given
      final var subject = nextSubject();
      // the server does not support heartbeats
      netty2.disableHeartbeats();
      // a client channel
      final var channel = netty1.getChannelPool().getChannel(netty2.address(), subject).join();

      // a Handler that does not forward back replies to heartbeats to simulate a server with an old
      // version that does not support heartbeats
      final var interceptingHandler =
          new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(final ChannelHandlerContext ctx, final Object msg)
                throws Exception {
              if (msg instanceof IdleStateEvent) {
                // nothing
                ctx.fireChannelRead(msg);
              } else if (msg instanceof final ProtocolReply reply) {
                // don't swallow the handshake
                if (reply.id() > 1) {
                  ReferenceCountUtil.release(msg);
                } else {
                  ctx.fireChannelRead(msg);
                }
              }
            }
          };

      // handler added after decoder so it can see the decoded messages
      channel.pipeline().addAfter("decoder", "interceptor", interceptingHandler);

      // the channel is not closed (because the future times out)
      final var timeout = defaultConfig().getHeartbeatTimeout().toSeconds() + 1;
      assertThatThrownBy(() -> channel.closeFuture().get(timeout, TimeUnit.SECONDS))
          .isInstanceOf(TimeoutException.class);
    }
  }
}
