/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.transport.impl.AtomixServerTransport.UnmatchedResponseHandler;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import org.agrona.concurrent.SnowflakeIdGenerator;
import org.slf4j.Logger;

/**
 * Delivers responses to requests that another broker received. A response can outlive the
 * leadership of the broker that received its request: an instance created with a request to await
 * its result may complete after leadership of its partition moved. Request ids carry the node id of
 * the broker that received the request, which keeps the gateway's request open, so the new leader
 * sends the response there to complete it.
 */
public final class ResponseForwarder implements UnmatchedResponseHandler {

  static final String SUBJECT = "command-api-forwarded-response";
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;

  private static final long NODE_ID_MASK = (1L << SnowflakeIdGenerator.NODE_ID_BITS_DEFAULT) - 1;

  private final ClusterCommunicationService communicationService;
  private final int localNodeId;

  /** Expects request ids from a {@link SnowflakeIdGenerator} with the default bit layout. */
  public ResponseForwarder(
      final ClusterCommunicationService communicationService, final int localNodeId) {
    this.communicationService = communicationService;
    this.localNodeId = localNodeId;
  }

  @Override
  public void onUnmatchedResponse(
      final long requestId, final int partitionId, final byte[] response) {
    final var receivingNodeId =
        (int) ((requestId >>> SnowflakeIdGenerator.SEQUENCE_BITS_DEFAULT) & NODE_ID_MASK);
    if (receivingNodeId == localNodeId) {
      return;
    }

    LOG.debug(
        "Forwarding response to request {} of partition {} to broker {}",
        requestId,
        partitionId,
        receivingNodeId);
    communicationService.unicast(
        SUBJECT,
        new ForwardedResponse(requestId, response),
        ForwardedResponse::encode,
        MemberId.from(String.valueOf(receivingNodeId)),
        true);
  }

  public void startReceiving(final AtomixServerTransport transport) {
    final Consumer<ForwardedResponse> handler =
        forwarded ->
            transport.completeForwardedResponse(forwarded.requestId(), forwarded.response());
    communicationService.consume(SUBJECT, ForwardedResponse::decode, handler, Runnable::run);
  }

  public void stopReceiving() {
    communicationService.unsubscribe(SUBJECT);
  }

  record ForwardedResponse(long requestId, byte[] response) {

    byte[] encode() {
      return ByteBuffer.allocate(Long.BYTES + response.length)
          .putLong(requestId)
          .put(response)
          .array();
    }

    static ForwardedResponse decode(final byte[] bytes) {
      final var buffer = ByteBuffer.wrap(bytes);
      final var requestId = buffer.getLong();
      final var response = new byte[buffer.remaining()];
      buffer.get(response);
      return new ForwardedResponse(requestId, response);
    }
  }
}
