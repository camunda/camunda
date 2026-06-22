/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.publish;

import io.camunda.eventbridge.protocol.ExecutePublishResponseEncoder;
import io.camunda.eventbridge.protocol.MessageHeaderEncoder;
import io.camunda.eventbridge.protocol.PublishResponseStatus;
import io.camunda.eventbridge.protocol.RejectionReason;
import java.nio.charset.StandardCharsets;
import org.agrona.concurrent.UnsafeBuffer;

/** Serializes and deserializes publish responses using SBE-generated codecs. */
public final class PublishResponse {

  private PublishResponse() {}

  public static byte[] success(final long firstPosition, final long lastPosition) {
    final int totalLength =
        MessageHeaderEncoder.ENCODED_LENGTH
            + ExecutePublishResponseEncoder.BLOCK_LENGTH
            + ExecutePublishResponseEncoder.rejectionMessageHeaderLength();

    final var bytes = new byte[totalLength];
    final var buffer = new UnsafeBuffer(bytes);

    final var headerEncoder = new MessageHeaderEncoder();
    final var encoder = new ExecutePublishResponseEncoder();

    encoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
    encoder.status(PublishResponseStatus.SUCCESS);
    encoder.rejectionReason(RejectionReason.UNKNOWN);
    encoder.firstPosition(firstPosition);
    encoder.lastPosition(lastPosition);
    encoder.putRejectionMessage(new byte[0], 0, 0);

    return bytes;
  }

  public static byte[] error(final RejectionReason reason, final Throwable error) {
    return error(
        reason, error.getMessage() != null ? error.getMessage() : error.getClass().getName());
  }

  public static byte[] error(final RejectionReason reason, final String message) {
    final var messageBytes =
        message != null ? message.getBytes(StandardCharsets.UTF_8) : new byte[0];

    final int totalLength =
        MessageHeaderEncoder.ENCODED_LENGTH
            + ExecutePublishResponseEncoder.BLOCK_LENGTH
            + ExecutePublishResponseEncoder.rejectionMessageHeaderLength()
            + messageBytes.length;

    final var bytes = new byte[totalLength];
    final var buffer = new UnsafeBuffer(bytes);

    final var headerEncoder = new MessageHeaderEncoder();
    final var encoder = new ExecutePublishResponseEncoder();

    encoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
    encoder.status(PublishResponseStatus.ERROR);
    encoder.rejectionReason(reason);
    encoder.putRejectionMessage(messageBytes, 0, messageBytes.length);

    return bytes;
  }
}
