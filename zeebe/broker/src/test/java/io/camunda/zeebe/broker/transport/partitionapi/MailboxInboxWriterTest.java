/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.partitionapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.mailbox.api.InboxMessage;
import io.camunda.zeebe.mailbox.api.MessageKind;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

final class MailboxInboxWriterTest {

  private final List<Byte> handled = new ArrayList<>();

  @Test
  void shouldWriteAllMessagesInOrder() {
    // given
    final var writer = writer(payload -> CompletableActorFuture.completed(true));

    // when
    final var written = writer.writeAll(messages(1, 2, 3));

    // then
    assertThat(written).isEqualTo(3);
    assertThat(handled).containsExactly((byte) 1, (byte) 2, (byte) 3);
  }

  @Test
  void shouldStopAtTheFirstMessageThatTheLogDoesNotTake() {
    // given
    final var writer = writer(payload -> CompletableActorFuture.completed(payload[0] != 2));

    // when
    final var written = writer.writeAll(messages(1, 2, 3));

    // then
    assertThat(written).isEqualTo(1);
    assertThat(handled).containsExactly((byte) 1, (byte) 2);
  }

  @Test
  void shouldThrowWhenTheFirstMessageFails() {
    // given
    final var writer =
        writer(
            payload ->
                CompletableActorFuture.completedExceptionally(
                    new IllegalStateException("log closed")));

    // when / then
    assertThatThrownBy(() -> writer.writeAll(messages(1, 2))).isInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldKeepTheConfirmedMessagesWhenALaterOneFails() {
    // given
    final var writer =
        writer(
            payload ->
                payload[0] == 2
                    ? CompletableActorFuture.completedExceptionally(
                        new IllegalStateException("log closed"))
                    : CompletableActorFuture.completed(true));

    // when
    final var written = writer.writeAll(messages(1, 2, 3));

    // then
    assertThat(written).isEqualTo(1);
    assertThat(handled).containsExactly((byte) 1, (byte) 2);
  }

  @Test
  void shouldFailWhenTheMessageIsNotHandledInTime() {
    // given
    final var writer =
        new MailboxInboxWriter(
            (sender, payload) -> new CompletableActorFuture<Boolean>(), Duration.ofMillis(50));

    // when / then
    assertThatThrownBy(() -> writer.writeAll(messages(1))).isInstanceOf(RuntimeException.class);
  }

  private MailboxInboxWriter writer(final Function<byte[], ActorFuture<Boolean>> behaviour) {
    return new MailboxInboxWriter(
        (sender, payload) -> {
          handled.add(payload[0]);
          return behaviour.apply(payload);
        },
        Duration.ofSeconds(5));
  }

  private static List<InboxMessage> messages(final int... markers) {
    final var messages = new ArrayList<InboxMessage>();
    for (final int marker : markers) {
      messages.add(
          new InboxMessage(
              marker,
              1,
              MessageKind.INTER_PARTITION_COMMAND,
              "1",
              marker,
              new byte[] {(byte) marker},
              1));
    }
    return messages;
  }
}
