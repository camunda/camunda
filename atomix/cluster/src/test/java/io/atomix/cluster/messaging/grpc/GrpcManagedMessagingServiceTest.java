/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.cluster.messaging.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.util.concurrent.MoreExecutors;
import io.atomix.cluster.ClusterConfig;
import io.atomix.cluster.MemberConfig;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.utils.Managed;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.grpc.StatusRuntimeException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class GrpcManagedMessagingServiceTest {
  private final ClusterConfig aliceConfig = createClusterConfigFor("alice");
  private final GrpcManagedMessagingService alice = GrpcFactory.create(aliceConfig);
  private final ClusterConfig bobConfig = createClusterConfigFor("bob");
  private final GrpcManagedMessagingService bob = GrpcFactory.create(bobConfig);

  @BeforeEach
  void beforeEach() {
    Stream.of(alice, bob).parallel().map(Managed::start).forEach(CompletableFuture::join);
  }

  @AfterEach
  void afterEach() {
    Stream.of(alice, bob).parallel().map(Managed::stop).forEach(CompletableFuture::join);
  }

  @Test
  void shouldSendAndReceive() {
    // given
    final List<byte[]> bobInbox = Collections.synchronizedList(new ArrayList<>());
    bob.registerHandler(
        "greeting",
        (replyTo, payload) -> {
          bobInbox.add(payload);
          return "I am Bob".getBytes(StandardCharsets.UTF_8);
        },
        MoreExecutors.directExecutor());

    // when
    final var greeting =
        alice.sendAndReceive(
            bob.address(), "greeting", "I am Alice".getBytes(StandardCharsets.UTF_8));

    // then
    assertThat(greeting)
        .succeedsWithin(Duration.ofSeconds(5))
        .isEqualTo("I am Bob".getBytes(StandardCharsets.UTF_8));
    assertThat(bobInbox).hasSize(1).containsExactly("I am Alice".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void testSendAsyncToUnresolvable() {
    final var unresolvable = Address.from("192.168.0.0", 1025);
    final String subject = nextSubject();
    final CompletableFuture<Void> response =
        alice.sendAsync(unresolvable, subject, "hello world".getBytes());

    assertThat(response).failsWithin(Duration.ofSeconds(10));
  }

  @Test
  void testSendAsync() {
    // given
    final String subject = nextSubject();

    // when
    final var successfulRequest = alice.sendAsync(bob.address(), subject, "hello world".getBytes());
    //    final var failedRequest =
    //        alice.sendAsync(Address.from("1.2.3.4", 1025), subject, "hello world".getBytes());

    // then
    assertThat(successfulRequest).succeedsWithin(Duration.ofSeconds(10));
    //    assertThat(failedRequest).failsWithin(Duration.ofSeconds(10));
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
    bob.registerHandler(subject, handler, MoreExecutors.directExecutor());

    final CompletableFuture<byte[]> response =
        alice.sendAndReceive(bob.address(), subject, "hello world".getBytes());
    assertThat(Arrays.equals("hello there".getBytes(), response.join())).isTrue();
    assertThat(handlerInvoked.get()).isTrue();
    assertThat(Arrays.equals(request.get(), "hello world".getBytes())).isTrue();
    assertThat(sender.get()).isEqualTo(alice.address());
  }

  @Test
  void shouldCompleteExistingRequestFutureExceptionallyWhenMessagingServiceIsClosed() {
    final String subject = nextSubject();
    final CompletableFuture<byte[]> response =
        alice.sendAndReceive(
            bob.address(), subject, "hello world".getBytes(), true, Duration.ofSeconds(5));

    // when
    alice.stop().join();

    // then
    assertThat(response).failsWithin(Duration.ofSeconds(10));
  }

  @Test
  void shouldCompleteExistingRequestWithKeepAliveExceptionallyWhenMessagingServiceIsClosed() {
    final String subject = nextSubject();
    final CompletableFuture<byte[]> response =
        alice.sendAndReceive(
            bob.address(), subject, "hello world".getBytes(), true, Duration.ofSeconds(5));

    // when
    alice.stop().join();

    // then
    assertThat(response).failsWithin(Duration.ofSeconds(10));
  }

  @Test
  void shouldCompleteFutureExceptionallyIfMessagingServiceIsClosedInBetween() {
    final String subject = nextSubject();

    // when
    final var stopFuture = alice.stop();
    final CompletableFuture<byte[]> response =
        alice.sendAndReceive(
            bob.address(), subject, "hello world".getBytes(), false, Duration.ofSeconds(5));

    stopFuture.join();

    // then
    assertThat(response).failsWithin(Duration.ofSeconds(10));
  }

  @Test
  void shouldCompleteRequestWithKeepAliveExceptionallyIfMessagingServiceIsClosedInBetween() {
    final String subject = nextSubject();

    // when
    final var stopFuture = alice.stop();
    final CompletableFuture<byte[]> response =
        alice.sendAndReceive(
            bob.address(), subject, "hello world".getBytes(), true, Duration.ofSeconds(5));

    stopFuture.join();

    // then
    assertThat(response).failsWithin(Duration.ofSeconds(10));
  }

  @Test
  void shouldCompleteFutureExceptionallyIfMessagingServiceHasAlreadyClosed() {
    final String subject = nextSubject();
    alice.stop().join();

    // when
    final CompletableFuture<byte[]> response =
        alice.sendAndReceive(
            bob.address(), subject, "hello world".getBytes(), false, Duration.ofSeconds(5));

    // then
    assertThat(response).failsWithin(Duration.ofSeconds(10));
  }

  @Test
  void shouldCompleteRequestWithKeepAliveExceptionallyIfMessagingServiceHasAlreadyClosed() {
    final String subject = nextSubject();
    alice.stop().join();

    // when
    final CompletableFuture<byte[]> response =
        alice.sendAndReceive(
            bob.address(), subject, "hello world".getBytes(), true, Duration.ofSeconds(5));

    // then
    assertThat(response).failsWithin(Duration.ofSeconds(10));
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
    bob.registerHandler(subject, handler, MoreExecutors.directExecutor());

    final CompletableFuture<byte[]> response =
        alice.sendAndReceive(bob.address(), subject, "hello world".getBytes(), false);
    assertThat(Arrays.equals("hello there".getBytes(), response.join())).isTrue();
    assertThat(handlerInvoked.get()).isTrue();
    assertThat(Arrays.equals(request.get(), "hello world".getBytes())).isTrue();
    assertThat(sender.get().address()).isEqualTo(alice.address().address());
  }

  @Test
  void testSendAndReceiveWithFixedTimeout() {
    final String subject = nextSubject();
    final BiFunction<Address, byte[], CompletableFuture<byte[]>> handler =
        (ep, payload) -> new CompletableFuture<>();
    bob.registerHandler(subject, handler);

    // when
    final var request =
        alice.sendAndReceive(
            bob.address(), subject, "hello world".getBytes(), Duration.ofSeconds(1));

    // then
    assertThat(request)
        .failsWithin(Duration.ofSeconds(2))
        .withThrowableOfType(ExecutionException.class)
        .havingRootCause()
        .isInstanceOf(StatusRuntimeException.class);
  }

  @Test
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
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
          } catch (final InterruptedException e1) {
            Thread.currentThread().interrupt();
            Assertions.fail("InterruptedException");
          }
          return "hello there".getBytes();
        };
    bob.registerHandler(subject, handler, handlerExecutor);

    final CompletableFuture<byte[]> response =
        alice.sendAndReceive(bob.address(), subject, "hello world".getBytes(), completionExecutor);
    response.whenComplete((r, e) -> completionThreadName.set(Thread.currentThread().getName()));
    latch.countDown();

    // Verify that the message was request handling and response completion happens on the correct
    // thread.
    assertThat(Arrays.equals("hello there".getBytes(), response.join())).isTrue();
    assertThat(completionThreadName.get()).isEqualTo("completion-thread");
    assertThat(handlerThreadName.get()).isEqualTo("handler-thread");
  }

  @Test
  void shouldNotBindToAdvertisedAddress() {
    // given
    final var config = createClusterConfigFor("colin");
    config.getNodeConfig().setHost("1.2.3.4").setPort(1);

    try (final GrpcManagedMessagingService managedService = GrpcFactory.create(config)) {
      final var messagingService = managedService.start().join();
      // then - should not fail by using advertisedAddress for binding
      assertThat(messagingService.bindingAddresses())
          .contains(
              Address.from(
                  config.getMessagingConfig().getInterfaces().get(0),
                  config.getMessagingConfig().getPort()));
      assertThat(messagingService.address()).isEqualTo(config.getNodeConfig().getAddress());
    }
  }

  private String nextSubject() {
    return UUID.randomUUID().toString();
  }

  private ClusterConfig createClusterConfigFor(final String id) {
    final var address = SocketUtil.getNextAddress();
    return new ClusterConfig()
        .setClusterId("cluster")
        .setNodeConfig(
            new MemberConfig().setId(id).setHost(address.getHostName()).setPort(address.getPort()))
        .setMessagingConfig(
            new MessagingConfig()
                .setPort(address.getPort())
                .setShutdownQuietPeriod(Duration.ofSeconds(1))
                .setShutdownTimeout(Duration.ofSeconds(2))
                .setInterfaces(List.of(address.getHostName())));
  }
}
