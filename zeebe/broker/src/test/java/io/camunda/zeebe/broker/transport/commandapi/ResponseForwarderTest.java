/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import org.agrona.concurrent.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class ResponseForwarderTest {

  private static final int LOCAL_NODE_ID = 1;
  private static final int RECEIVING_NODE_ID = 2;

  private final ClusterCommunicationService communicationService =
      mock(ClusterCommunicationService.class);
  private final ResponseForwarder forwarder =
      new ResponseForwarder(communicationService, LOCAL_NODE_ID);

  @Test
  @SuppressWarnings("unchecked")
  void shouldDeliverResponseToBrokerThatReceivedTheRequest() {
    // given
    final long requestId = new SnowflakeIdGenerator(RECEIVING_NODE_ID).nextId();
    final var receivingTransport = mock(AtomixServerTransport.class);
    final var receivingForwarder = new ResponseForwarder(communicationService, RECEIVING_NODE_ID);
    receivingForwarder.startReceiving(receivingTransport);

    // when
    forwarder.onUnmatchedResponse(requestId, 1, new byte[] {1, 2, 3});

    // then
    final ArgumentCaptor<ResponseForwarder.ForwardedResponse> message =
        ArgumentCaptor.forClass(ResponseForwarder.ForwardedResponse.class);
    final ArgumentCaptor<Function<ResponseForwarder.ForwardedResponse, byte[]>> encoder =
        ArgumentCaptor.forClass(Function.class);
    verify(communicationService)
        .unicast(
            eq(ResponseForwarder.SUBJECT),
            message.capture(),
            encoder.capture(),
            eq(MemberId.from(String.valueOf(RECEIVING_NODE_ID))),
            eq(true));

    final ArgumentCaptor<Function<byte[], ResponseForwarder.ForwardedResponse>> decoder =
        ArgumentCaptor.forClass(Function.class);
    final ArgumentCaptor<Consumer<ResponseForwarder.ForwardedResponse>> handler =
        ArgumentCaptor.forClass(Consumer.class);
    verify(communicationService)
        .consume(
            eq(ResponseForwarder.SUBJECT),
            decoder.capture(),
            handler.capture(),
            any(Executor.class));
    final byte[] sent = encoder.getValue().apply(message.getValue());
    handler.getValue().accept(decoder.getValue().apply(sent));
    verify(receivingTransport).completeForwardedResponse(requestId, new byte[] {1, 2, 3});
  }

  @Test
  void shouldStopReceivingForwardedResponses() {
    // given
    forwarder.startReceiving(mock(AtomixServerTransport.class));

    // when
    forwarder.stopReceiving();

    // then
    verify(communicationService).unsubscribe(ResponseForwarder.SUBJECT);
  }

  @Test
  void shouldNotForwardResponseToRequestThatThisBrokerReceived() {
    // given
    final long requestId = new SnowflakeIdGenerator(LOCAL_NODE_ID).nextId();

    // when
    forwarder.onUnmatchedResponse(requestId, 1, new byte[] {1});

    // then
    verify(communicationService, never())
        .unicast(any(), any(), any(), any(MemberId.class), anyBoolean());
  }
}
