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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;
import com.sun.security.auth.module.UnixSystem;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.MessagingException;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.camunda.zeebe.test.util.socket.SocketUtil;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.agrona.collections.MutableReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Netty messaging service test. */
@AutoCloseResources
final class NettyMessagingServiceTest {
  private static final String CLUSTER_NAME = "zeebe";
  private static final int UID_COLUMN = 7;

  private MessagingConfig defaultConfig() {
    return new MessagingConfig().setShutdownQuietPeriod(Duration.ofMillis(50));
  }

  private String nextSubject() {
    return UUID.randomUUID().toString();
  }

  private ManagedMessagingService createMessagingServiceWithPool(
      final MutableReference<ChannelPool> poolRef) {
    final var otherAddress = newAddress();
    final var config = defaultConfig();
    return new NettyMessagingService(
        CLUSTER_NAME,
        otherAddress,
        config,
        ProtocolVersion.V2,
        factory -> {
          final var pool = new ChannelPool(factory, 8);
          poolRef.set(pool);
          return pool;
        },
        "testingPrefix");
  }

  private NettyMessagingService newMessagingService() {
    return new NettyMessagingService(CLUSTER_NAME, newAddress(), defaultConfig(), "testingPrefix");
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
  final class VersionTest {
    @AutoCloseResource private final NettyMessagingService nettyv11 = newMessagingService();
    @AutoCloseResource private final NettyMessagingService nettyv12 = newMessagingService();
    @AutoCloseResource private final NettyMessagingService nettyv21 = newMessagingService();
    @AutoCloseResource private final NettyMessagingService nettyv22 = newMessagingService();

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
          new NettyMessagingService("test", nonBindableAddress, config, "testingPrefix")) {
        // then - should not fail by using advertisedAddress for binding
        assertThat(service.start()).succeedsWithin(Duration.ofSeconds(5));
        assertThat(service.bindingAddresses()).contains(bindingAddress);
        assertThat(service.address()).isEqualTo(nonBindableAddress);
      }
    }
  }

  @Nested
  final class DualInstanceTest {
    @AutoCloseResource private final NettyMessagingService netty1 = newMessagingService();
    @AutoCloseResource private final NettyMessagingService netty2 = newMessagingService();

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
        Assertions.fail();
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
              Assertions.fail("InterruptedException");
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
    void shouldCloseChannelAfterTimeout() throws Exception {
      // given
      final var subject = nextSubject();
      final MutableReference<ChannelPool> poolRef = new MutableReference<>();
      final var timeoutOnCreate = Duration.ofSeconds(10);

      try (final var nettyWithOwnPool = createMessagingServiceWithPool(poolRef)) {
        final var channelPool = poolRef.get();
        nettyWithOwnPool.start().join();

        // grab the original channel, so we can assert it was closed
        // it's also useful to send a successful request once, so we can ensure the channel exists
        // and set a lower timeout on the request we want specifically to time out
        netty2.registerHandler(subject, (address, bytes) -> new byte[0], Runnable::run);
        nettyWithOwnPool
            .sendAndReceive(
                netty2.address(), subject, "get channel".getBytes(), true, timeoutOnCreate)
            .join();
        final var originalChannel = channelPool.getChannel(netty2.address(), subject).join();

        // when - set up handler which will always cause timeouts
        netty2.unregisterHandler(subject);
        netty2.registerHandler(subject, (address, bytes) -> new CompletableFuture<>());
        final CompletableFuture<byte[]> response =
            nettyWithOwnPool.sendAndReceive(
                netty2.address(), subject, "fail".getBytes(), true, Duration.ofSeconds(1));

        // then
        assertThat(response)
            .failsWithin(Duration.ofSeconds(15))
            .withThrowableThat()
            .havingRootCause()
            .isInstanceOf(TimeoutException.class)
            .withMessageContaining("timed out in");
        assertThat(originalChannel.closeFuture()).succeedsWithin(Duration.ofSeconds(15));
      }
    }

    @Test
    void shouldCreateNewChannelOnNewRequestAfterTimeout() throws Exception {
      // given
      final var subject = nextSubject();
      final MutableReference<ChannelPool> poolRef = new MutableReference<>();
      final var timeoutOnCreate = Duration.ofSeconds(10);

      try (final var nettyWithOwnPool = createMessagingServiceWithPool(poolRef)) {
        final var channelPool = poolRef.get();
        nettyWithOwnPool.start().join();

        // grab the original channel, so we can assert it was closed
        // it's also useful to send a successful request once, so we can ensure the channel exists
        // and set a lower timeout on the request we want specifically to time out
        netty2.registerHandler(subject, (address, bytes) -> new byte[0], Runnable::run);
        nettyWithOwnPool
            .sendAndReceive(
                netty2.address(), subject, "get channel".getBytes(), true, timeoutOnCreate)
            .join();
        final var originalChannel = channelPool.getChannel(netty2.address(), subject).join();

        // set up handler which will always cause timeouts
        netty2.unregisterHandler(subject);
        netty2.registerHandler(subject, (address, bytes) -> new CompletableFuture<>());
        final CompletableFuture<byte[]> response =
            nettyWithOwnPool.sendAndReceive(
                netty2.address(), subject, "fail".getBytes(), true, Duration.ofSeconds(1));
        assertThat(response)
            .failsWithin(Duration.ofSeconds(15))
            .withThrowableThat()
            .havingRootCause()
            .isInstanceOf(TimeoutException.class);
        // wait until the channel is closed before grabbing the next one
        assertThat(originalChannel.closeFuture()).succeedsWithin(Duration.ofSeconds(15));

        // when - remote connection finally succeeds
        // give a generous time out on the second request, as creating a new channel can be slow at
        // times
        netty2.unregisterHandler(subject);
        netty2.registerHandler(subject, (address, bytes) -> new byte[0], Runnable::run);
        nettyWithOwnPool.sendAndReceive(
            netty2.address(), subject, "success".getBytes(), true, timeoutOnCreate);

        // then
        final var newChannel = channelPool.getChannel(netty2.address(), subject).join();
        assertThat(newChannel).isNotEqualTo(originalChannel);
      }
    }

    @EnabledOnOs(OS.LINUX)
    @RegressionTest("https://github.com/camunda/camunda/issues/14837")
    void shouldNotLeakUdpSockets() throws IOException {
      // given
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
      assertThat(udpSocketCount()).isLessThanOrEqualTo(maxConnections * 2L);
    }
  }
}
