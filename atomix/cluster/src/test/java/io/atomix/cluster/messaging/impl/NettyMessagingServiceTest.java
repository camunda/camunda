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
import io.atomix.utils.net.Address;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
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

  @Before
  public void setUp() throws Exception {
    address1 = Address.from(findAvailablePort(5001));
    netty1 =
        (ManagedMessagingService)
            new NettyMessagingService("test", address1, new MessagingConfig()).start().join();

    address2 = Address.from(findAvailablePort(5002));
    netty2 =
        (ManagedMessagingService)
            new NettyMessagingService("test", address2, new MessagingConfig()).start().join();

    addressv11 = Address.from(findAvailablePort(5003));
    nettyv11 =
        (ManagedMessagingService)
            new NettyMessagingService("test", addressv11, new MessagingConfig(), ProtocolVersion.V1)
                .start()
                .join();

    addressv12 = Address.from(findAvailablePort(5004));
    nettyv12 =
        (ManagedMessagingService)
            new NettyMessagingService("test", addressv12, new MessagingConfig(), ProtocolVersion.V1)
                .start()
                .join();

    addressv21 = Address.from(findAvailablePort(5005));
    nettyv21 =
        (ManagedMessagingService)
            new NettyMessagingService("test", addressv21, new MessagingConfig(), ProtocolVersion.V2)
                .start()
                .join();

    addressv22 = Address.from(findAvailablePort(5006));
    nettyv22 =
        (ManagedMessagingService)
            new NettyMessagingService("test", addressv22, new MessagingConfig(), ProtocolVersion.V2)
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
    if (netty1 != null) {
      try {
        netty1.stop().join();
      } catch (final Exception e) {
        LOGGER.warn("Failed stopping netty1", e);
      }
    }

    if (netty2 != null) {
      try {
        netty2.stop().join();
      } catch (final Exception e) {
        LOGGER.warn("Failed stopping netty2", e);
      }
    }
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
  public void testSendAndReceiveWithDynamicTimeout() {
    final String subject = nextSubject();
    final BiFunction<Address, byte[], CompletableFuture<byte[]>> handler =
        (ep, payload) -> new CompletableFuture<>();
    netty2.registerHandler(subject, handler);

    try {
      netty1.sendAndReceive(address2, subject, "hello world".getBytes()).join();
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

  private static int findAvailablePort(final int defaultPort) {
    try {
      final ServerSocket socket = new ServerSocket(0);
      socket.setReuseAddress(true);
      final int port = socket.getLocalPort();
      socket.close();
      return port;
    } catch (final IOException ex) {
      return defaultPort;
    }
  }
}
