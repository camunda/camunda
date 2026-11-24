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
import io.camunda.zeebe.test.util.logging.LogCapturer;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import org.agrona.CloseHelper;
import org.apache.logging.log4j.Level;
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
  void shouldReplaceTheListenerOnTheSameSubject() {
    // given
    final Queue<String> delivered = new ConcurrentLinkedQueue<>();
    receiver.addListener(SUBJECT, (sender, payload) -> delivered.add("first"));
    receiver.addListener(SUBJECT, (sender, payload) -> delivered.add("second"));

    // when
    sender.unicast(receiverAddress, SUBJECT, "hello".getBytes());

    // then
    Awaitility.await("only the replacement is called")
        .atMost(DELIVERY_TIMEOUT)
        .untilAsserted(() -> assertThat(delivered).containsExactly("second"));
  }

  @Test
  void shouldNotRemoveTheReplacementWithAStaleListener() {
    // given
    final Queue<String> delivered = new ConcurrentLinkedQueue<>();
    final BiConsumer<Address, byte[]> stale = (sender, payload) -> delivered.add("stale");
    final BiConsumer<Address, byte[]> replacement =
        (sender, payload) -> delivered.add("replacement");
    receiver.addListener(SUBJECT, stale);
    receiver.addListener(SUBJECT, replacement);

    // when
    receiver.removeListener(SUBJECT, stale);
    sender.unicast(receiverAddress, SUBJECT, "hello".getBytes());

    // then
    Awaitility.await("the replacement is still called")
        .atMost(DELIVERY_TIMEOUT)
        .untilAsserted(() -> assertThat(delivered).containsExactly("replacement"));
  }

  @Test
  void shouldStopDeliveringAfterTheListenerIsRemoved() {
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
  void shouldDeliverReliableAndUnreliableTrafficOnTheSameSubject() {
    // given - DefaultClusterCommunicationService.consume() registers a messaging handler and then a
    // unicast listener on the same subject, and expects both kinds of traffic to reach the consumer
    final Queue<byte[]> delivered = new ConcurrentLinkedQueue<>();
    final BiConsumer<Address, byte[]> handler = (sender, payload) -> delivered.add(payload);
    receiver.registerHandler(SUBJECT, handler, Runnable::run);
    receiver.addListener(SUBJECT, (sender, payload) -> delivered.add(payload));

    // when
    sender.sendAsync(receiverAddress, SUBJECT, "reliable".getBytes()).join();
    sender.unicast(receiverAddress, SUBJECT, "unreliable".getBytes());

    // then
    Awaitility.await("both messages are delivered")
        .atMost(DELIVERY_TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(delivered)
                    .containsExactlyInAnyOrder("reliable".getBytes(), "unreliable".getBytes()));
  }

  @Test
  void shouldNotRemoveAMessagingHandlerRegisteredAfterTheListener() {
    // given - a messaging handler replaces the listener's registration on the same subject
    final Queue<String> delivered = new ConcurrentLinkedQueue<>();
    final BiConsumer<Address, byte[]> listener = (sender, payload) -> delivered.add("listener");
    receiver.addListener(SUBJECT, listener);
    final BiConsumer<Address, byte[]> handler = (sender, payload) -> delivered.add("handler");
    receiver.registerHandler(SUBJECT, handler, Runnable::run);

    // when
    receiver.removeListener(SUBJECT, listener);
    sender.sendAsync(receiverAddress, SUBJECT, "hello".getBytes()).join();

    // then
    Awaitility.await("the messaging handler is still called")
        .atMost(DELIVERY_TIMEOUT)
        .untilAsserted(() -> assertThat(delivered).containsExactly("handler"));
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
  void shouldLogRepeatedFailuresToAnUnreachablePeerOnlyOnce() {
    // given - gossip to a peer that is down fails on every round until it is declared dead
    final var unreachable = Address.from("127.0.0.1", SocketUtil.getNextAddress().getPort());

    try (final var logs = LogCapturer.capturing(NettyMessagingService.class, Level.DEBUG)) {
      // when
      for (int i = 0; i < 10; i++) {
        sender.unicast(unreachable, SUBJECT, "hello".getBytes());
      }

      // then - one line, without a stack trace, for as long as the failures keep coming
      Awaitility.await("the failures are logged once")
          .during(Duration.ofMillis(500))
          .atMost(DELIVERY_TIMEOUT)
          .untilAsserted(() -> assertThat(unicastFailures(logs)).hasSize(1));
      assertThat(unicastFailures(logs).getFirst()).contains(unreachable.toString(), SUBJECT);
      assertThat(logs.containsInMessagesOrThrowables("\tat ")).isFalse();
    }
  }

  @Test
  void shouldLogOnStartThatUdpIsDisabled() {
    // given
    final var address = Address.from("127.0.0.1", SocketUtil.getNextAddress().getPort());
    final var tcpOnly =
        new NettyMessagingService(
            CLUSTER_ID,
            address,
            new MessagingConfig().setUdpEnabled(false),
            "unicast-tcp-only",
            registry);

    try (final var logs = LogCapturer.capturing(NettyMessagingService.class, Level.INFO)) {
      // when
      tcpOnly.start().join();

      // then
      assertThat(logs.messagesAt(Level.INFO))
          .anySatisfy(
              message ->
                  assertThat(message)
                      .startsWith("Started messaging service")
                      .contains("UDP disabled"));
    } finally {
      tcpOnly.stop().join();
    }
  }

  @Test
  void shouldNotMentionUdpOnStartWhenItIsEnabled() {
    // given
    final var address = Address.from("127.0.0.1", SocketUtil.getNextAddress().getPort());
    final var withUdp =
        new NettyMessagingService(
            CLUSTER_ID, address, new MessagingConfig(), "unicast-with-udp", registry);

    try (final var logs = LogCapturer.capturing(NettyMessagingService.class, Level.INFO)) {
      // when
      withUdp.start().join();

      // then
      assertThat(logs.messagesAt(Level.INFO))
          .filteredOn(message -> message.startsWith("Started messaging service"))
          .singleElement()
          .asString()
          .doesNotContain("UDP");
    } finally {
      withUdp.stop().join();
    }
  }

  private static List<String> unicastFailures(final LogCapturer logs) {
    return logs.messagesAt(Level.DEBUG).stream()
        .filter(message -> message.startsWith("Failed sending unicast message"))
        .toList();
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
  void shouldDeliverOnTheListenersExecutor() {
    // given
    final Queue<String> executorTasks = new ConcurrentLinkedQueue<>();
    final Queue<String> delivered = new ConcurrentLinkedQueue<>();
    receiver.addListener(
        SUBJECT,
        (sender, payload) -> delivered.add("called"),
        task -> {
          executorTasks.add("used");
          task.run();
        });

    // when
    sender.unicast(receiverAddress, SUBJECT, "hello".getBytes());

    // then
    Awaitility.await("the listener is called on its executor")
        .atMost(DELIVERY_TIMEOUT)
        .untilAsserted(
            () -> {
              assertThat(delivered).containsExactly("called");
              assertThat(executorTasks).containsExactly("used");
            });
  }
}
