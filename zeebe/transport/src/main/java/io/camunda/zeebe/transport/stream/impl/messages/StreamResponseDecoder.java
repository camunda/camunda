/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl.messages;

import io.camunda.zeebe.util.Either;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class StreamResponseDecoder {
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final DirectBuffer buffer = new UnsafeBuffer();

  public <T extends StreamResponse> Either<ErrorResponse, T> decode(
      final byte[] bytes, final T response) {
    // for backwards compatibility, accept empty responses as success
    // to be removed with 8.5
    if (bytes.length == 0) {
      return Either.right(response);
    }

    buffer.wrap(bytes);
    headerDecoder.wrap(buffer, 0);

    if (headerDecoder.schemaId() != headerDecoder.sbeSchemaId()) {
      return Either.left(
          new ErrorResponse()
              .code(ErrorCode.MALFORMED)
              .message(
                  "Invalid schema ID; expected '%d', got '%d'"
                      .formatted(headerDecoder.sbeSchemaId(), headerDecoder.schemaId())));
    }

    if (headerDecoder.templateId() == ErrorResponseDecoder.TEMPLATE_ID) {
      final var errorResponse = new ErrorResponse();
      errorResponse.wrap(buffer, 0, buffer.capacity());
      return Either.left(errorResponse);
    }

    if (headerDecoder.templateId() != response.templateId()) {
      return Either.left(
          new ErrorResponse()
              .code(ErrorCode.MALFORMED)
              .message(
                  "Invalid template ID; expected '%d', got '%d'"
                      .formatted(headerDecoder.templateId(), response.templateId())));
    }

    response.wrap(buffer, 0, buffer.capacity());
    return Either.right(response);
  }
}
