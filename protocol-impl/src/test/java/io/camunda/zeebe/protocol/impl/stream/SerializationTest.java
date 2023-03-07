/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.stream;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.UUID;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
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
}
