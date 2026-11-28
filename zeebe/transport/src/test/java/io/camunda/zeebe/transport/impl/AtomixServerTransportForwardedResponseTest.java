/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.atomix.cluster.messaging.MessagingService;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerExtension;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.impl.AtomixServerTransport.TopicSupplier;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import org.agrona.concurrent.SnowflakeIdGenerator;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;

final class AtomixServerTransportForwardedResponseTest {

  private static final int PARTITION = 1;

  @RegisterExtension
  private final ActorSchedulerExtension scheduler = new ActorSchedulerExtension(1);

  private final MessagingService messagingService = mock(MessagingService.class);
  private final CompletableFuture<Long> unmatchedRequestId = new CompletableFuture<>();
  private final CompletableFuture<Long> receivedRequestId = new CompletableFuture<>();
  private AtomixServerTransport transport;

  @BeforeEach
  void setUp() {
    transport =
        new AtomixServerTransport(
            messagingService,
            new SnowflakeIdGenerator(1),
            List.of(TopicSupplier.withPrefix("command")),
            (requestId, partitionId, response) -> unmatchedRequestId.complete(requestId));
    scheduler.submitActor(transport).join();
    transport
        .subscribe(
            PARTITION,
            RequestType.COMMAND,
            (output, partitionId, requestId, buffer, offset, length) ->
                receivedRequestId.complete(requestId))
        .join();
  }

  @AfterEach
  void tearDown() {
    transport.close();
  }

  @Test
  void shouldCompletePendingRequestWithForwardedResponse() {
    // given
    final var pendingRequest = receiveRequest();
    final long requestId = receivedRequestId.join();

    // when
    transport.completeForwardedResponse(requestId, "forwarded".getBytes());

    // then
    assertThat(pendingRequest)
        .succeedsWithin(Duration.ofSeconds(5))
        .isEqualTo("forwarded".getBytes());
  }

  @Test
  void shouldHandOverResponseToRequestThatIsNotPending() {
    // given
    final var pendingRequest = receiveRequest();
    final long otherRequestId = receivedRequestId.join() + 1;

    // when
    transport.sendResponse(
        new ServerResponseImpl()
            .setRequestId(otherRequestId)
            .setPartitionId(PARTITION)
            .writer(new DirectBufferWriter().wrap(BufferUtil.wrapString("response"))));

    // then
    assertThat(unmatchedRequestId).succeedsWithin(Duration.ofSeconds(5)).isEqualTo(otherRequestId);
    assertThat(pendingRequest).isNotDone();
  }

  @SuppressWarnings("unchecked")
  private CompletableFuture<byte[]> receiveRequest() {
    final ArgumentCaptor<BiFunction<Address, byte[], CompletableFuture<byte[]>>> handler =
        ArgumentCaptor.forClass(BiFunction.class);
    verify(messagingService).registerHandler(anyString(), handler.capture());
    final var pendingRequest =
        handler.getValue().apply(Address.from("localhost:1"), "request".getBytes());
    Awaitility.await("request is handled").until(receivedRequestId::isDone);
    return pendingRequest;
  }
}
