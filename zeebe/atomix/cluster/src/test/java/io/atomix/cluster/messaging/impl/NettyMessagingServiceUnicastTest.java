/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the unreliable-unicast semantics {@link NettyMessagingService} carries over its TCP
 * transport, which is what lets a cluster run without UDP.
 */
final class NettyMessagingServiceUnicastTest {

  private static final String CLUSTER_ID = "zeebe";
  private static final String SUBJECT = "test-subject";
  private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(5);

  @AutoClose private final MeterRegistry registry = new SimpleMeterRegistry();

  private Address senderAddress;
  private Address receiverAddress;
  private NettyMessagingService sender;
  private NettyMessagingService receiver;

  @BeforeEach
  void setUp() {
    senderAddress = Address.from("127.0.0.1", SocketUtil.getNextAddress().getPort());
    receiverAddress = Address.from("127.0.0.1", SocketUtil.getNextAddress().getPort());

    sender =
        new NettyMessagingService(
            CLUSTER_ID, senderAddress, new MessagingConfig(), "unicast-sender", registry);
    receiver =
        new NettyMessagingService(
            CLUSTER_ID, receiverAddress, new MessagingConfig(), "unicast-receiver", registry);

    sender.start().join();
    receiver.start().join();
  }

  @AfterEach
  void tearDown() {
    CloseHelper.quietCloseAll(() -> sender.stop().join(), () -> receiver.stop().join());
  }

  @Test
  void shouldDeliverUnicastMessageFromTheAdvertisedAddress() {
    // given - the listener must be able to resolve the sender back to a cluster member, so it needs
    // the advertised address rather than the ephemeral source port of the connection
    final Queue<Address> senders = new ConcurrentLinkedQueue<>();
    final Queue<byte[]> payloads = new ConcurrentLinkedQueue<>();
    receiver.addListener(
        SUBJECT,
        (sender, payload) -> {
          senders.add(sender);
          payloads.add(payload);
        });

    // when
    sender.unicast(receiverAddress, SUBJECT, "hello".getBytes());

    // then
    Awaitility.await("the message is delivered")
        .atMost(DELIVERY_TIMEOUT)
        .untilAsserted(() -> assertThat(payloads).hasSize(1));
    assertThat(senders).containsExactly(senderAddress);
    assertThat(payloads.peek()).isEqualTo("hello".getBytes());
  }

  @Test
  void shouldDeliverToAllListenersOnTheSameSubject() {
    // given
    final Queue<String> delivered = new ConcurrentLinkedQueue<>();
    receiver.addListener(SUBJECT, (sender, payload) -> delivered.add("first"));
    receiver.addListener(SUBJECT, (sender, payload) -> delivered.add("second"));

    // when
    sender.unicast(receiverAddress, SUBJECT, "hello".getBytes());

    // then
    Awaitility.await("both listeners are called")
        .atMost(DELIVERY_TIMEOUT)
        .untilAsserted(() -> assertThat(delivered).containsExactlyInAnyOrder("first", "second"));
  }

  @Test
  void shouldKeepDeliveringUntilTheLastListenerIsRemoved() {
    // given
    final Queue<String> delivered = new ConcurrentLinkedQueue<>();
    final BiConsumer<Address, byte[]> first = (sender, payload) -> delivered.add("first");
    final BiConsumer<Address, byte[]> second = (sender, payload) -> delivered.add("second");
    receiver.addListener(SUBJECT, first);
    receiver.addListener(SUBJECT, second);

    // when - only one of the two listeners goes away
    receiver.removeListener(SUBJECT, first);
    sender.unicast(receiverAddress, SUBJECT, "hello".getBytes());

    // then
    Awaitility.await("the remaining listener is called")
        .atMost(DELIVERY_TIMEOUT)
        .untilAsserted(() -> assertThat(delivered).containsExactly("second"));
  }

  @Test
  void shouldStopDeliveringAfterTheLastListenerIsRemoved() {
    // given
    final Queue<String> delivered = new ConcurrentLinkedQueue<>();
    final BiConsumer<Address, byte[]> listener = (sender, payload) -> delivered.add("called");
    receiver.addListener(SUBJECT, listener);
    receiver.removeListener(SUBJECT, listener);

    // when - a message that would have been delivered before the listener was removed
    sender.unicast(receiverAddress, SUBJECT, "hello".getBytes());
    // send a second message on a subject that does have a listener, so we can tell "not delivered
    // yet" from "never delivered"
    final Queue<String> sentinel = new ConcurrentLinkedQueue<>();
    receiver.addListener("sentinel", (sender, payload) -> sentinel.add("called"));
    sender.unicast(receiverAddress, "sentinel", "hello".getBytes());

    // then
    Awaitility.await("the sentinel message arrives")
        .atMost(DELIVERY_TIMEOUT)
        .untilAsserted(() -> assertThat(sentinel).hasSize(1));
    assertThat(delivered).isEmpty();
  }

  @Test
  void shouldNotCollideWithMessagingHandlerOnTheSameSubject() {
    // given - a plain messaging handler and a unicast listener registered under the same subject,
    // which is exactly what DefaultClusterCommunicationService.consume() does
    final Queue<byte[]> handlerPayloads = new ConcurrentLinkedQueue<>();
    final Queue<byte[]> listenerPayloads = new ConcurrentLinkedQueue<>();
    final BiConsumer<Address, byte[]> messagingHandler =
        (sender, payload) -> handlerPayloads.add(payload);
    receiver.registerHandler(SUBJECT, messagingHandler, Runnable::run);
    receiver.addListener(SUBJECT, (sender, payload) -> listenerPayloads.add(payload));

    // when
    sender.sendAsync(receiverAddress, SUBJECT, "messaging".getBytes()).join();
    sender.unicast(receiverAddress, SUBJECT, "unicast".getBytes());

    // then - each receives only its own traffic
    Awaitility.await("both messages are delivered")
        .atMost(DELIVERY_TIMEOUT)
        .untilAsserted(
            () -> {
              assertThat(handlerPayloads).hasSize(1);
              assertThat(listenerPayloads).hasSize(1);
            });
    assertThat(handlerPayloads.peek()).isEqualTo("messaging".getBytes());
    assertThat(listenerPayloads.peek()).isEqualTo("unicast".getBytes());
  }

  @Test
  void shouldNotFailWhenThePeerIsUnreachable() {
    // given - gossip to a dead peer must not surface an error to the caller, nor log-flood at the
    // gossip interval
    final var unreachable = Address.from("127.0.0.1", SocketUtil.getNextAddress().getPort());

    // when - then
    assertThatCode(() -> sender.unicast(unreachable, SUBJECT, "hello".getBytes()))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldNotFailWhenTheServiceIsStopped() {
    // given
    sender.stop().join();

    // when - then
    assertThatCode(() -> sender.unicast(receiverAddress, SUBJECT, "hello".getBytes()))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldDeliverToEveryListenerOnItsOwnExecutor() {
    // given - the single registry entry per subject must not make every listener share whichever
    // executor registered first
    final Queue<String> firstExecutorTasks = new ConcurrentLinkedQueue<>();
    final Queue<String> secondExecutorTasks = new ConcurrentLinkedQueue<>();
    receiver.addListener(
        SUBJECT,
        (sender, payload) -> {},
        task -> {
          firstExecutorTasks.add("used");
          task.run();
        });
    receiver.addListener(
        SUBJECT,
        (sender, payload) -> {},
        task -> {
          secondExecutorTasks.add("used");
          task.run();
        });

    // when
    sender.unicast(receiverAddress, SUBJECT, "hello".getBytes());

    // then
    Awaitility.await("both executors are used")
        .atMost(DELIVERY_TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(List.of(firstExecutorTasks.size(), secondExecutorTasks.size()))
                    .containsExactly(1, 1));
  }
}
