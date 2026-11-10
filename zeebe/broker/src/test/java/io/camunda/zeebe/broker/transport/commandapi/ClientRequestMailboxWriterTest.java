/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.mailbox.InMemoryMailboxStore;
import io.camunda.zeebe.mailbox.api.InboxMessage;
import io.camunda.zeebe.mailbox.api.MessageKind;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.ServerOutput;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** The writer hands the requests of an inbox to the request handler, one after another. */
final class ClientRequestMailboxWriterTest {

  private static final int PARTITION = 3;

  private final CommandApiRequestHandler handler = mock(CommandApiRequestHandler.class);
  private final ServerOutput transport = mock(ServerOutput.class);
  private final MailboxResponseRouter router =
      new MailboxResponseRouter(transport, new InMemoryMailboxStore(), Runnable::run);
  private final ClientRequestMailboxWriter writer =
      new ClientRequestMailboxWriter(handler, router, PARTITION, Duration.ofSeconds(5));

  @Test
  void shouldHandleAllRequestsInOrder() {
    // given
    when(handler.handleRequestFromMailbox(any(), anyInt(), anyLong(), any(), anyInt(), anyInt()))
        .thenReturn(CompletableActorFuture.completed());

    // when
    final var written = writer.writeAll(messages(1, 2, 3));

    // then
    assertThat(written).isEqualTo(3);
    final var requestIds = ArgumentCaptor.forClass(Long.class);
    final var buffers = ArgumentCaptor.forClass(DirectBuffer.class);
    verify(handler, times(3))
        .handleRequestFromMailbox(
            eq(router), eq(PARTITION), requestIds.capture(), buffers.capture(), eq(0), eq(1));
    assertThat(requestIds.getAllValues()).isSorted().doesNotHaveDuplicates();
    assertThat(buffers.getAllValues())
        .extracting(buffer -> buffer.getByte(0))
        .containsExactly((byte) 1, (byte) 2, (byte) 3);
  }

  @Test
  void shouldRememberTheGatewayOfEachRequestForItsResponse() {
    // given
    when(handler.handleRequestFromMailbox(any(), anyInt(), anyLong(), any(), anyInt(), anyInt()))
        .thenReturn(CompletableActorFuture.completed());

    // when
    writer.writeAll(messages(1, 2));

    // then
    assertThat(router.pendingCount()).isEqualTo(2);
  }

  @Test
  void shouldThrowWhenTheFirstRequestFails() {
    // given
    when(handler.handleRequestFromMailbox(any(), anyInt(), anyLong(), any(), anyInt(), anyInt()))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(new IllegalStateException("closed")));

    // when / then
    assertThatThrownBy(() -> writer.writeAll(messages(1, 2)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(router.pendingCount()).isZero();
    verify(handler, times(1))
        .handleRequestFromMailbox(any(), anyInt(), anyLong(), any(), anyInt(), anyInt());
  }

  @Test
  void shouldKeepTheConfirmedRequestsWhenALaterOneFails() {
    // given
    when(handler.handleRequestFromMailbox(any(), anyInt(), anyLong(), any(), anyInt(), anyInt()))
        .thenReturn(CompletableActorFuture.completed())
        .thenReturn(
            CompletableActorFuture.completedExceptionally(new IllegalStateException("closed")));

    // when
    final var written = writer.writeAll(messages(1, 2, 3));

    // then
    assertThat(written).isEqualTo(1);
    assertThat(router.pendingCount()).isEqualTo(1);
    verify(handler, times(2))
        .handleRequestFromMailbox(any(), anyInt(), anyLong(), any(), anyInt(), anyInt());
  }

  @Test
  void shouldFailWhenARequestIsNotHandledInTime() {
    // given
    final var shortWriter =
        new ClientRequestMailboxWriter(handler, router, PARTITION, Duration.ofMillis(50));
    when(handler.handleRequestFromMailbox(any(), anyInt(), anyLong(), any(), anyInt(), anyInt()))
        .thenReturn(new CompletableActorFuture<>());

    // when / then
    assertThatThrownBy(() -> shortWriter.writeAll(messages(1)))
        .isInstanceOf(RuntimeException.class);
    assertThat(router.pendingCount()).isZero();
  }

  @Test
  void shouldNotHandleRequestsOfAnEmptyBatch() {
    // when
    final var written = writer.writeAll(List.of());

    // then
    assertThat(written).isZero();
    verify(handler, never())
        .handleRequestFromMailbox(any(), anyInt(), anyLong(), any(), anyInt(), anyInt());
  }

  private static List<InboxMessage> messages(final int... markers) {
    final var messages = new ArrayList<InboxMessage>();
    for (final int marker : markers) {
      messages.add(
          new InboxMessage(
              marker,
              PARTITION,
              MessageKind.CLIENT_REQUEST,
              "gateway-1",
              marker,
              new byte[] {(byte) marker},
              1));
    }
    return messages;
  }
}
