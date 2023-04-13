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
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.slf4j.LoggerFactory.getLogger;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.MessagingException;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.net.ConnectException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;

/** Netty messaging service test. */
public class NettyMessagingServiceTest {

  private static final Logger LOGGER = getLogger(NettyMessagingServiceTest.class);
  private static final String IP_STRING = "127.0.0.1";
  ManagedMessagingService netty1;
  ManagedMessagingService netty2;
  ManagedMessagingService nettyv11;
  ManagedMessagingService nettyv12;
  ManagedMessagingService nettyv21;
  ManagedMessagingService nettyv22;
  Address address1;
  Address address2;
  Address addressv11;
  Address addressv12;
  Address addressv21;
  Address addressv22;
  Address invalidAddress;
  private ManagedMessagingService messagingService;

  @Before
  public void setUp() throws Exception {
    address1 = Address.from(SocketUtil.getNextAddress().getPort());
    final var config = new MessagingConfig().setShutdownQuietPeriod(Duration.ofMillis(50));

    netty1 =
        (ManagedMessagingService)
            new NettyMessagingService("test", address1, config).start().join();

    address2 = Address.from(SocketUtil.getNextAddress().getPort());
    netty2 =
        (ManagedMessagingService)
            new NettyMessagingService("test", address2, config).start().join();

    addressv11 = Address.from(SocketUtil.getNextAddress().getPort());
    nettyv11 =
        (ManagedMessagingService)
            new NettyMessagingService("test", addressv11, config, ProtocolVersion.V1)
                .start()
                .join();

    addressv12 = Address.from(SocketUtil.getNextAddress().getPort());
    nettyv12 =
        (ManagedMessagingService)
            new NettyMessagingService("test", addressv12, config, ProtocolVersion.V1)
                .start()
                .join();

    addressv21 = Address.from(SocketUtil.getNextAddress().getPort());
    nettyv21 =
        (ManagedMessagingService)
            new NettyMessagingService("test", addressv21, config, ProtocolVersion.V2)
                .start()
                .join();

    addressv22 = Address.from(SocketUtil.getNextAddress().getPort());
    nettyv22 =
        (ManagedMessagingService)
            new NettyMessagingService("test", addressv22, config, ProtocolVersion.V2)
                .start()
                .join();

    invalidAddress = Address.from(IP_STRING, 5007);
  }

  /**
   * Returns a random String to be used as a test subject.
   *
   * @return string
   */
  private String nextSubject() {
    return UUID.randomUUID().toString();
  }

  @After
  public void tearDown() throws Exception {
    Stream.of(netty1, netty2, nettyv11, nettyv12, nettyv21, nettyv22, messagingService)
        .parallel()
        .filter(Objects::nonNull)
        .forEach(
            service -> {
              try {
                service.stop().join();
              } catch (final Exception e) {
                LOGGER.warn("Failed stopping NettyMessagingService {}", service, e);
              }
            });
  }

  @Test
  public void testSendAsyncToUnresolvable() {
    final Address unresolvable = Address.from("unknown.local", address1.port());
    final String subject = nextSubject();
    final CompletableFuture<Void> response =
        netty1.sendAsync(unresolvable, subject, "hello world".getBytes());
    assertTrue(response.isCompletedExceptionally());
  }

  @Test
  public void testSendAsync() {
    final String subject = nextSubject();
    final CountDownLatch latch1 = new CountDownLatch(1);
    CompletableFuture<Void> response =
        netty1.sendAsync(address2, subject, "hello world".getBytes());
    response.whenComplete(
        (r, e) -> {
          assertNull(e);
          latch1.countDown();
        });
    Uninterruptibles.awaitUninterruptibly(latch1);

    final CountDownLatch latch2 = new CountDownLatch(1);
    response = netty1.sendAsync(invalidAddress, subject, "hello world".getBytes());
    response.whenComplete(
        (r, e) -> {
          assertNotNull(e);
          assertTrue(e instanceof ConnectException);
          latch2.countDown();
        });
    Uninterruptibles.awaitUninterruptibly(latch2);
  }

  @Test
  public void testSendAndReceive() {
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
        netty1.sendAndReceive(address2, subject, "hello world".getBytes());
    assertTrue(Arrays.equals("hello there".getBytes(), response.join()));
    assertTrue(handlerInvoked.get());
    assertTrue(Arrays.equals(request.get(), "hello world".getBytes()));
    assertEquals(address1.address(), sender.get().address());
  }

  @Test
  public void shouldCompleteExistingRequestFutureExceptionallyWhenMessagingServiceIsClosed() {
    final String subject = nextSubject();
    final CompletableFuture<byte[]> response =
        netty1.sendAndReceive(
            address2, subject, "hello world".getBytes(), true, Duration.ofSeconds(5));

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
            address2, subject, "hello world".getBytes(), true, Duration.ofSeconds(5));

    // when
    netty1.stop().join();

    // then
    assertThat(response).isCompletedExceptionally();
  }

  @Test
  public void shouldCompleteFutureExceptionallyIfMessagingServiceIsClosedInBetween() {
    final String subject = nextSubject();

    // when
    final var stopFuture = netty1.stop();
    final CompletableFuture<byte[]> response =
        netty1.sendAndReceive(
            address2, subject, "hello world".getBytes(), false, Duration.ofSeconds(5));

    stopFuture.join();

    // then
    assertThat(response).isCompletedExceptionally();
  }

  @Test
  public void shouldCompleteRequestWithKeepAliveExceptionallyIfMessagingServiceIsClosedInBetween() {
    final String subject = nextSubject();

    // when
    final var stopFuture = netty1.stop();
    final CompletableFuture<byte[]> response =
        netty1.sendAndReceive(
            address2, subject, "hello world".getBytes(), true, Duration.ofSeconds(5));

    stopFuture.join();

    // then
    assertThat(response).isCompletedExceptionally();
  }

  @Test
  public void shouldCompleteFutureExceptionallyIfMessagingServiceHasAlreadyClosed() {
    final String subject = nextSubject();
    netty1.stop().join();

    // when
    final CompletableFuture<byte[]> response =
        netty1.sendAndReceive(
            address2, subject, "hello world".getBytes(), false, Duration.ofSeconds(5));

    // then
    assertThat(response).isCompletedExceptionally();
  }

  @Test
  public void shouldCompleteRequestWithKeepAliveExceptionallyIfMessagingServiceHasAlreadyClosed() {
    final String subject = nextSubject();
    netty1.stop().join();

    // when
    final CompletableFuture<byte[]> response =
        netty1.sendAndReceive(
            address2, subject, "hello world".getBytes(), true, Duration.ofSeconds(5));

    // then
    assertThat(response).isCompletedExceptionally();
  }

  @Test
  public void testTransientSendAndReceive() {
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
        netty1.sendAndReceive(address2, subject, "hello world".getBytes(), false);
    assertTrue(Arrays.equals("hello there".getBytes(), response.join()));
    assertTrue(handlerInvoked.get());
    assertTrue(Arrays.equals(request.get(), "hello world".getBytes()));
    assertEquals(address1.address(), sender.get().address());
  }

  @Test
  public void testSendAndReceiveWithFixedTimeout() {
    final String subject = nextSubject();
    final BiFunction<Address, byte[], CompletableFuture<byte[]>> handler =
        (ep, payload) -> new CompletableFuture<>();
    netty2.registerHandler(subject, handler);

    try {
      netty1
          .sendAndReceive(address2, subject, "hello world".getBytes(), Duration.ofSeconds(1))
          .join();
      fail();
    } catch (final CompletionException e) {
      assertTrue(e.getCause() instanceof TimeoutException);
    }
  }

  @Test
  @Ignore
  public void testSendAndReceiveWithExecutor() {
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
        netty1.sendAndReceive(address2, subject, "hello world".getBytes(), completionExecutor);
    response.whenComplete(
        (r, e) -> {
          completionThreadName.set(Thread.currentThread().getName());
        });
    latch.countDown();

    // Verify that the message was request handling and response completion happens on the correct
    // thread.
    assertTrue(Arrays.equals("hello there".getBytes(), response.join()));
    assertEquals("completion-thread", completionThreadName.get());
    assertEquals("handler-thread", handlerThreadName.get());
  }

  @Test
  public void testV1() throws Exception {
    final String subject;
    final byte[] payload = "Hello world!".getBytes();
    final byte[] response;

    subject = nextSubject();
    nettyv11.registerHandler(subject, (address, bytes) -> CompletableFuture.completedFuture(bytes));
    response = nettyv12.sendAndReceive(addressv11, subject, payload).get(10, TimeUnit.SECONDS);
    assertArrayEquals(payload, response);
  }

  @Test
  public void testV2() throws Exception {
    final String subject;
    final byte[] payload = "Hello world!".getBytes();
    final byte[] response;

    subject = nextSubject();
    nettyv21.registerHandler(subject, (address, bytes) -> CompletableFuture.completedFuture(bytes));
    response = nettyv22.sendAndReceive(addressv21, subject, payload).get(10, TimeUnit.SECONDS);
    assertArrayEquals(payload, response);
  }

  @Test
  public void testVersionNegotiation() throws Exception {
    String subject;
    final byte[] payload = "Hello world!".getBytes();
    byte[] response;

    subject = nextSubject();
    nettyv11.registerHandler(subject, (address, bytes) -> CompletableFuture.completedFuture(bytes));
    response = nettyv21.sendAndReceive(addressv11, subject, payload).get(10, TimeUnit.SECONDS);
    assertArrayEquals(payload, response);

    subject = nextSubject();
    nettyv22.registerHandler(subject, (address, bytes) -> CompletableFuture.completedFuture(bytes));
    response = nettyv12.sendAndReceive(addressv22, subject, payload).get(10, TimeUnit.SECONDS);
    assertArrayEquals(payload, response);
  }

  @Test
  public void shouldNotBindToAdvertisedAddress() {
    // given
    final var bindingAddress = Address.from(SocketUtil.getNextAddress().getPort());
    final MessagingConfig config = new MessagingConfig();
    config.setInterfaces(List.of(bindingAddress.host()));
    config.setPort(bindingAddress.port());

    // when
    final Address nonBindableAddress = new Address("invalid.host", 1);
    final var startFuture = new NettyMessagingService("test", nonBindableAddress, config).start();

    // then - should not fail by using advertisedAddress for binding
    messagingService = (ManagedMessagingService) startFuture.join();
    assertThat(messagingService.bindingAddresses()).contains(bindingAddress);
    assertThat(messagingService.address()).isEqualTo(nonBindableAddress);
  }

  @Test
  public void testRemoteHandlerFailure() {
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
        netty1.sendAndReceive(address2, subject, "fail".getBytes());

    // then
    assertThat(response)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .havingRootCause()
        .isInstanceOf(MessagingException.RemoteHandlerFailure.class)
        .withMessage(expectedException.getMessage());
  }

  @Test
  public void testRemoteHandlerFailureNullValue() {
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
        netty1.sendAndReceive(address2, subject, "fail".getBytes());

    // then
    assertThat(response)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .havingRootCause()
        .isInstanceOf(MessagingException.RemoteHandlerFailure.class)
        .withMessage(expectedException.getMessage());
  }

  @Test
  public void testRemoteHandlerFailureEmptyStringValue() {
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
        netty1.sendAndReceive(address2, subject, "fail".getBytes());

    // then
    assertThat(response)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .havingRootCause()
        .isInstanceOf(MessagingException.RemoteHandlerFailure.class)
        .withMessage(expectedException.getMessage());
  }

  @Test
  public void testCompletableRemoteHandlerFailure() {
    // given
    final var exceptionMessage = "foo bar";
    final var expectedException = new MessagingException.RemoteHandlerFailure(exceptionMessage);

    final var subject = nextSubject();
    netty2.registerHandler(
        subject,
        (address, bytes) -> CompletableFuture.failedFuture(new RuntimeException(exceptionMessage)));

    // when
    final CompletableFuture<byte[]> response =
        netty1.sendAndReceive(address2, subject, "fail".getBytes());

    // then
    assertThat(response)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .havingRootCause()
        .isInstanceOf(MessagingException.RemoteHandlerFailure.class)
        .withMessage(expectedException.getMessage());
  }

  @Test
  public void testCompletableRemoteHandlerFailureNullValue() {
    // given
    final var expectedException = new MessagingException.RemoteHandlerFailure(null);

    final var subject = nextSubject();
    netty2.registerHandler(
        subject, (address, bytes) -> CompletableFuture.failedFuture(new RuntimeException()));

    // when
    final CompletableFuture<byte[]> response =
        netty1.sendAndReceive(address2, subject, "fail".getBytes());

    // then
    assertThat(response)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .havingRootCause()
        .isInstanceOf(MessagingException.RemoteHandlerFailure.class)
        .withMessage(expectedException.getMessage());
  }

  @Test
  public void testCompletableRemoteHandlerFailureEmptyStringValue() {
    // given
    final var exceptionMessage = "";
    final var expectedException = new MessagingException.RemoteHandlerFailure(null);

    final var subject = nextSubject();
    netty2.registerHandler(
        subject,
        (address, bytes) -> CompletableFuture.failedFuture(new RuntimeException(exceptionMessage)));

    // when
    final CompletableFuture<byte[]> response =
        netty1.sendAndReceive(address2, subject, "fail".getBytes());

    // then
    assertThat(response)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .havingRootCause()
        .isInstanceOf(MessagingException.RemoteHandlerFailure.class)
        .withMessage(expectedException.getMessage());
  }

  @Test
  public void shouldCloseChannelAfterTimeout() {
    // given
    final var subject = nextSubject();
    final MutableReference<ChannelPool> poolRef = new MutableReference<>();
    final var timeoutOnCreate = Duration.ofSeconds(10);
    final var nettyWithOwnPool = createMessagingServiceWithPool(poolRef);
    final var channelPool = poolRef.get();

    try {
      nettyWithOwnPool.start().join();

      // grab the original channel, so we can assert it was closed
      // it's also useful to send a successful request once, so we can ensure the channel exists and
      // set a lower timeout on the request we want specifically to time out
      netty2.registerHandler(subject, (address, bytes) -> new byte[0], Runnable::run);
      nettyWithOwnPool
          .sendAndReceive(address2, subject, "get channel".getBytes(), true, timeoutOnCreate)
          .join();
      final var originalChannel = channelPool.getChannel(address2, subject).join();

      // when - set up handler which will always cause timeouts
      netty2.unregisterHandler(subject);
      netty2.registerHandler(subject, (address, bytes) -> new CompletableFuture<>());
      final CompletableFuture<byte[]> response =
          nettyWithOwnPool.sendAndReceive(
              address2, subject, "fail".getBytes(), true, Duration.ofSeconds(1));

      // then
      assertThat(response)
          .failsWithin(Duration.ofSeconds(15))
	  .withThrowableOfType(ExecutionException.class)
          .havingRootCause()
          .isInstanceOf(TimeoutException.class)
          .withMessageContaining("timed out in");
      assertThat(originalChannel.closeFuture()).succeedsWithin(Duration.ofSeconds(15));
    } finally {
      nettyWithOwnPool.stop().join();
    }
  }

  @Test
  public void shouldCreateNewChannelOnNewRequestAfterTimeout() {
    // given
    final var subject = nextSubject();
    final MutableReference<ChannelPool> poolRef = new MutableReference<>();
    final var timeoutOnCreate = Duration.ofSeconds(10);
    final var nettyWithOwnPool = createMessagingServiceWithPool(poolRef);
    final var channelPool = poolRef.get();

    try {
      nettyWithOwnPool.start().join();

      // grab the original channel, so we can assert it was closed
      // it's also useful to send a successful request once, so we can ensure the channel exists and
      // set a lower timeout on the request we want specifically to time out
      netty2.registerHandler(subject, (address, bytes) -> new byte[0], Runnable::run);
      nettyWithOwnPool
          .sendAndReceive(address2, subject, "get channel".getBytes(), true, timeoutOnCreate)
          .join();
      final var originalChannel = channelPool.getChannel(address2, subject).join();

      // set up handler which will always cause timeouts
      netty2.unregisterHandler(subject);
      netty2.registerHandler(subject, (address, bytes) -> new CompletableFuture<>());
      final CompletableFuture<byte[]> response =
          nettyWithOwnPool.sendAndReceive(
              address2, subject, "fail".getBytes(), true, Duration.ofSeconds(1));
      assertThat(response)
          .failsWithin(Duration.ofSeconds(15))
	  .withThrowableOfType(ExecutionException.class)
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
          address2, subject, "success".getBytes(), true, timeoutOnCreate);

      // then
      final var newChannel = channelPool.getChannel(address2, subject).join();
      assertThat(newChannel).isNotEqualTo(originalChannel);
    } finally {
      nettyWithOwnPool.stop().join();
    }
  }

  private ManagedMessagingService createMessagingServiceWithPool(
      final MutableReference<ChannelPool> poolRef) {
    final var otherAddress = Address.from(SocketUtil.getNextAddress().getPort());
    final var config = new MessagingConfig();
    return new NettyMessagingService(
        "test",
        otherAddress,
        config,
        ProtocolVersion.V2,
        factory -> {
          final var pool = new ChannelPool(factory, 8);
          poolRef.set(pool);
          return pool;
        });
  }

  @Test
  public void testNoRemoteHandlerException() {
    // given
    final var subject = nextSubject();
    final var expectedException = new MessagingException.NoRemoteHandler(subject);

    // when
    final CompletableFuture<byte[]> response =
        netty1.sendAndReceive(address2, subject, "fail".getBytes());

    // then
    assertThat(response)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .havingRootCause()
        .isInstanceOf(MessagingException.NoRemoteHandler.class)
        .withMessage(expectedException.getMessage());
  }

  @Test
  public void testNoRemoteHandlerExceptionEmptyStringValue() {
    // given
    final var subject = "";
    final var expectedException = new MessagingException.NoRemoteHandler(null);

    // when
    final CompletableFuture<byte[]> response =
        netty1.sendAndReceive(address2, subject, "fail".getBytes());

    // then
    assertThat(response)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .havingRootCause()
        .isInstanceOf(MessagingException.NoRemoteHandler.class)
        .withMessage(expectedException.getMessage());
  }
}
