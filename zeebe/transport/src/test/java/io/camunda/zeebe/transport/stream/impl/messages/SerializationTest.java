/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl.messages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.transport.stream.api.StreamResponseException.ErrorDetail;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import java.util.UUID;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

final class SerializationTest {
  private final MutableDirectBuffer buffer = new ExpandableArrayBuffer();

  @Test
  void shouldSerializeAddStreamRequest() {
    // given
    final var streamId = UUID.randomUUID();
    final var request =
        new AddStreamRequest()
            .streamId(streamId)
            .streamType(BufferUtil.wrapString("foo"))
            .metadata(BufferUtil.wrapString("bar"));

    // when
    request.write(buffer, 0);
    final var deserialized = new AddStreamRequest();
    deserialized.wrap(buffer, 0, request.getLength());

    // then
    assertThat(deserialized.streamId()).isEqualTo(streamId);
    assertThat(deserialized.streamType()).isEqualTo(BufferUtil.wrapString("foo"));
    assertThat(deserialized.metadata()).isEqualTo(BufferUtil.wrapString("bar"));
  }

  @Test
  void shouldSerializeAddStreamRequestWithMetadataWriter() {
    // given
    final var streamId = UUID.randomUUID();
    final DirectBuffer metadata = BufferUtil.wrapString("bar");
    final var request =
        new AddStreamRequest()
            .streamId(streamId)
            .streamType(BufferUtil.wrapString("foo"))
            .metadata(new DirectBufferWriter().wrap(metadata));

    // when
    request.write(buffer, 0);
    final var deserialized = new AddStreamRequest();
    deserialized.wrap(buffer, 0, request.getLength());

    // then
    assertThat(deserialized.streamId()).isEqualTo(streamId);
    assertThat(deserialized.streamType()).isEqualTo(BufferUtil.wrapString("foo"));
    assertThat(deserialized.metadata()).isEqualTo(BufferUtil.wrapString("bar"));
  }

  @Test
  void shouldSerializeAddStreamResponse() {
    // given
    final var response = new AddStreamResponse();

    // when
    response.write(buffer, 0);
    final var deserialized = new AddStreamResponse();

    // then
    assertThatCode(() -> deserialized.wrap(buffer, 0, response.getLength()))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldSerializeRemoveStreamRequest() {
    // given
    final var streamId = UUID.randomUUID();
    final var request = new RemoveStreamRequest().streamId(streamId);

    // when
    request.write(buffer, 0);
    final var deserialized = new RemoveStreamRequest();
    deserialized.wrap(buffer, 0, request.getLength());

    // then
    assertThat(deserialized.streamId()).isEqualTo(streamId);
  }

  @Test
  void shouldSerializeRemoveStreamResponse() {
    // given
    final var response = new RemoveStreamResponse();

    // when
    response.write(buffer, 0);
    final var deserialized = new RemoveStreamResponse();

    // then
    assertThatCode(() -> deserialized.wrap(buffer, 0, response.getLength()))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldSerializePushStreamRequest() {
    // given
    final var streamId = UUID.randomUUID();
    final var request =
        new PushStreamRequest().streamId(streamId).payload(BufferUtil.wrapString("foo"));

    // when
    request.write(buffer, 0);
    final var deserialized = new PushStreamRequest();
    deserialized.wrap(buffer, 0, request.getLength());

    // then
    assertThat(deserialized.streamId()).isEqualTo(streamId);
    assertThat(deserialized.payload()).isEqualTo(BufferUtil.wrapString("foo"));
  }

  @Test
  void shouldSerializePushStreamResponse() {
    // given
    final var response = new PushStreamResponse();

    // when
    response.write(buffer, 0);
    final var deserialized = new PushStreamResponse();

    // then
    assertThatCode(() -> deserialized.wrap(buffer, 0, response.getLength()))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldSerializeErrorResponse() {
    // given
    final var response =
        new ErrorResponse().code(ErrorCode.EXHAUSTED).message("Stream is exhausted");

    // when
    response.write(buffer, 0);
    final var deserialized = new ErrorResponse();
    deserialized.wrap(buffer, 0, response.getLength());

    // then
    assertThat(deserialized.code()).isEqualTo(ErrorCode.EXHAUSTED);
    assertThat(deserialized.message()).isEqualTo("Stream is exhausted");
  }

  @Test
  void shouldSerializeErrorResponseDetails() {
    // given
    final var response =
        new ErrorResponse()
            .code(ErrorCode.EXHAUSTED)
            .message("Stream is exhausted")
            .addDetail(ErrorCode.BLOCKED, "Stream is blocked")
            .addDetail(ErrorCode.INVALID, "Message is invalid");

    // when
    response.write(buffer, 0);
    final var deserialized = new ErrorResponse();
    deserialized.wrap(buffer, 0, response.getLength());

    // then
    assertThat(deserialized.details())
        .extracting(ErrorDetail::code, ErrorDetail::message)
        .containsExactlyInAnyOrder(
            Tuple.tuple(ErrorCode.BLOCKED, "Stream is blocked"),
            Tuple.tuple(ErrorCode.INVALID, "Message is invalid"));
  }
}
