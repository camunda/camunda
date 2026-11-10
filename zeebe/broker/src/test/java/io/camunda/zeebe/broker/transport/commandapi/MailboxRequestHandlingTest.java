/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.cluster.PartitionId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerPublishMessageRequest;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.mailbox.InMemoryMailboxStore;
import io.camunda.zeebe.mailbox.api.GatewayId;
import io.camunda.zeebe.mailbox.api.InboxMessage;
import io.camunda.zeebe.mailbox.api.MessageKind;
import io.camunda.zeebe.mailbox.api.OutboxResponse;
import io.camunda.zeebe.protocol.impl.encoding.ErrorResponse;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerExtension;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.transport.ServerResponse;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;

/**
 * A request from the mailbox is handled by the real request handler, as a request from the network.
 */
final class MailboxRequestHandlingTest {

  private static final int PARTITION = 0;
  private static final GatewayId GATEWAY = new GatewayId("gateway-1");
  private static final long GATEWAY_REQUEST_ID = 5;

  @RegisterExtension final ActorSchedulerExtension scheduler = new ActorSchedulerExtension();

  private final InMemoryMailboxStore store = new InMemoryMailboxStore();
  private final ServerOutput transport = mock(ServerOutput.class);
  private final MailboxResponseRouter router =
      new MailboxResponseRouter(transport, store, Runnable::run);
  private final LogStreamWriter logStreamWriter = mock(LogStreamWriter.class);
  private CommandApiRequestHandler handler;
  private ClientRequestMailboxWriter writer;

  @BeforeEach
  void setUp() {
    handler =
        new CommandApiRequestHandler(
            new PartitionId(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, PARTITION));
    scheduler.submitActor(handler).join();
    when(logStreamWriter.canWriteEvents(anyInt(), anyInt())).thenReturn(true);
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.right(1L));
    handler.addPartition(PARTITION, logStreamWriter);
    writer = new ClientRequestMailboxWriter(handler, router, PARTITION, Duration.ofSeconds(5));
  }

  @Test
  void shouldWriteTheCommandToTheLogAndKeepTheResponseDue() {
    // when
    final var written = writer.writeAll(List.of(request()));

    // then
    assertThat(written).isEqualTo(1);
    final var entry = ArgumentCaptor.forClass(LogAppendEntry.class);
    verify(logStreamWriter, timeout(5_000)).tryWrite(any(WriteContext.class), entry.capture());
    final var metadata = entry.getValue().recordMetadata();
    assertThat(metadata.getRequestId())
        .isGreaterThanOrEqualTo(MailboxResponseRouter.FIRST_REQUEST_ID);
    assertThat(metadata.getRequestStreamId()).isEqualTo(PARTITION);
    assertThat(router.pendingCount()).isEqualTo(1);
    assertThat(store.takeResponses(GATEWAY, Set.of(GATEWAY_REQUEST_ID))).isEmpty();
    verify(transport, never()).sendResponse(any());
  }

  @Test
  void shouldDeliverTheResponseOfTheEngineToTheGateway() {
    // given
    writer.writeAll(List.of(request()));
    final var entry = ArgumentCaptor.forClass(LogAppendEntry.class);
    verify(logStreamWriter, timeout(5_000)).tryWrite(any(WriteContext.class), entry.capture());
    final var requestId = entry.getValue().recordMetadata().getRequestId();

    // when - the engine answers later, with the request id from the log record
    router.sendResponse(new BytesResponse(requestId, new byte[] {7, 8, 9}));

    // then
    final var responses = store.takeResponses(GATEWAY, Set.of(GATEWAY_REQUEST_ID));
    assertThat(responses).extracting(OutboxResponse::payload).containsExactly(new byte[] {7, 8, 9});
    verify(transport, never()).sendResponse(any());
  }

  @Test
  void shouldAnswerThroughTheMailboxWhenTheRequestIsRejected() {
    // given - this node does not lead the partition
    handler.removePartition(PARTITION);

    // when
    final var written = writer.writeAll(List.of(request()));

    // then - the error is the answer, and the request leaves the inbox
    assertThat(written).isEqualTo(1);
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              final var responses = store.takeResponses(GATEWAY, Set.of(GATEWAY_REQUEST_ID));
              assertThat(responses).hasSize(1);
              final var buffer = new UnsafeBuffer(responses.get(0).payload());
              final var error = new ErrorResponse();
              assertThat(error.tryWrap(buffer)).isTrue();
              error.wrap(buffer, 0, buffer.capacity());
              assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PARTITION_LEADER_MISMATCH);
            });
  }

  private static InboxMessage request() {
    final var request =
        new BrokerPublishMessageRequest("test", "1").setMessageId("1").setTimeToLive(0);
    request.serializeValue();
    final var bytes = new byte[request.getLength()];
    request.write(new UnsafeBuffer(bytes), 0);
    return new InboxMessage(
        1, PARTITION, MessageKind.CLIENT_REQUEST, GATEWAY.value(), GATEWAY_REQUEST_ID, bytes, 1);
  }

  private record BytesResponse(long requestIdValue, byte[] payload) implements ServerResponse {
    @Override
    public long getRequestId() {
      return requestIdValue;
    }

    @Override
    public int getPartitionId() {
      return PARTITION;
    }

    @Override
    public int getLength() {
      return payload.length;
    }

    @Override
    public int write(final MutableDirectBuffer buffer, final int offset) {
      buffer.putBytes(offset, payload);
      return payload.length;
    }
  }
}
