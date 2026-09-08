/*
 * Copyright 2018-present Open Networking Foundation
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
import static org.assertj.core.api.Assertions.assertThatCode;

import io.atomix.cluster.messaging.ManagedUnicastService;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Netty unicast service test. */
final class NettyUnicastServiceTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  @AutoClose private final MeterRegistry registry = new SimpleMeterRegistry();

  private ManagedUnicastService service1;
  private ManagedUnicastService service2;
  private Address address1;
  private Address address2;

  @BeforeEach
  void setUp() {
    address1 = Address.from("127.0.0.1", SocketUtil.getNextAddress().getPort());
    address2 = Address.from("127.0.0.1", SocketUtil.getNextAddress().getPort());

    final String clusterId = "testClusterId";
    service1 =
        new NettyUnicastService(clusterId, address1, new MessagingConfig(), "Unicast-1", registry);
    service1.start().join();

    service2 =
        new NettyUnicastService(clusterId, address2, new MessagingConfig(), "Unicast-2", registry);
    service2.start().join();
  }

  @AfterEach
  void tearDown() {
    CloseHelper.quietCloseAll(() -> service1.stop().join(), () -> service2.stop().join());
  }

  @Test
  void shouldDeliverUnicastMessageToItsListener() {
    // given
    final var sender = new CompletableFuture<Address>();
    final var received = new CompletableFuture<byte[]>();
    service1.addListener(
        "test",
        (address, payload) -> {
          sender.complete(address);
          received.complete(payload);
        });

    // when
    service2.unicast(address1, "test", "Hello world!".getBytes());

    // then - the sender is the peer's advertised address, not its ephemeral source port
    assertThat(received).succeedsWithin(TIMEOUT).isEqualTo("Hello world!".getBytes());
    assertThat(sender).succeedsWithin(TIMEOUT).isEqualTo(address2);
  }

  @Test
  void shouldReplaceTheListenerAlreadyRegisteredForASubject() {
    // given
    final var replaced = new CompletableFuture<byte[]>();
    final var current = new CompletableFuture<byte[]>();
    service1.addListener("test", (address, payload) -> replaced.complete(payload));
    service1.addListener("test", (address, payload) -> current.complete(payload));

    // when
    service2.unicast(address1, "test", "Hello world!".getBytes());

    // then - last registration wins, as it does for MessagingService#registerHandler
    assertThat(current).succeedsWithin(TIMEOUT).isEqualTo("Hello world!".getBytes());
    assertThat(replaced).as("the replaced listener no longer receives").isNotDone();
  }

  @Test
  void shouldKeepTheCurrentListenerWhenRemovingAReplacedOne() {
    // given
    final var current = new CompletableFuture<byte[]>();
    final BiConsumer<Address, byte[]> replaced = (address, payload) -> {};
    service1.addListener("test", replaced);
    service1.addListener("test", (address, payload) -> current.complete(payload));

    // when - removing a listener that is no longer the registered one
    service1.removeListener("test", replaced);

    // then - the listener that replaced it is untouched
    service2.unicast(address1, "test", "Hello world!".getBytes());
    assertThat(current).succeedsWithin(TIMEOUT).isEqualTo("Hello world!".getBytes());
  }

  @Test
  void shouldKeepTheCurrentListenerWhenRemovingAnEqualButDistinctOne() {
    // given - a listener whose type has value equality, so an equal instance is not the same one
    final var current = new CompletableFuture<byte[]>();
    service1.addListener("test", new ValueEqualListener(current));

    // when - removing a listener that is equal to, but not, the registered instance
    service1.removeListener("test", new ValueEqualListener(current));

    // then - the registered listener is untouched, as removal compares by identity
    service2.unicast(address1, "test", "Hello world!".getBytes());
    assertThat(current).succeedsWithin(TIMEOUT).isEqualTo("Hello world!".getBytes());
  }

  @Test
  void shouldNotThrowExceptionWhenServiceStopped() {
    // given
    service2.stop();

    // when - then
    assertThatCode(() -> service2.unicast(address1, "test", "Hello world!".getBytes()))
        .doesNotThrowAnyException();
  }

  /** A listener with value equality, so that two distinct instances can compare equal. */
  private record ValueEqualListener(CompletableFuture<byte[]> received)
      implements BiConsumer<Address, byte[]> {
    @Override
    public void accept(final Address address, final byte[] payload) {
      received.complete(payload);
    }
  }
}
