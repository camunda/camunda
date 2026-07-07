/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.eventbridge.protocol.ErrorCode;
import io.camunda.eventbridge.protocol.FetchResponseEncoder;
import io.camunda.eventbridge.protocol.MessageHeaderEncoder;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class FetchResponseTest {

  private static final int MESSAGE_OFFSET = 16;

  @Test
  void shouldWindowDataInsideTheWrappedBufferWithoutCopying() {
    // given
    final byte[] data = {1, 2, 3, 4, 5, 6, 7};
    final var frame = encode(data);
    final var response = new FetchResponse();

    // when
    response.wrap(frame.buffer(), MESSAGE_OFFSET, frame.length());

    // then
    assertThat(response.getErrorCode()).isEqualTo(ErrorCode.NONE);
    assertThat(response.getFirstPosition()).isEqualTo(11);
    assertThat(response.getLastPosition()).isEqualTo(17);
    assertThat(response.getHighWatermark()).isEqualTo(42);
    assertThat(response.getDataLength()).isEqualTo(data.length);

    final byte[] read = new byte[data.length];
    response.getData().getBytes(0, read, 0, data.length);
    assertThat(read).isEqualTo(data);

    // the window aliases the wrapped buffer instead of copying: mutating the underlying frame is
    // visible through the window
    frame.buffer().putByte(frame.dataOffset(), (byte) 99);
    assertThat(response.getData().getByte(0)).isEqualTo((byte) 99);
  }

  @Test
  void shouldHandleEmptyData() {
    // given
    final var frame = encode(new byte[0]);
    final var response = new FetchResponse();

    // when
    response.wrap(frame.buffer(), MESSAGE_OFFSET, frame.length());

    // then
    assertThat(response.getDataLength()).isZero();
  }

  @Test
  void shouldRejectDataLengthBeyondTheMessage() {
    // given
    final var frame = encode(new byte[100]);
    final var response = new FetchResponse();

    // when: claim to have fewer bytes than the encoded data length
    final int truncatedLength = frame.length() - 64;

    // then
    assertThatThrownBy(() -> response.wrap(frame.buffer(), MESSAGE_OFFSET, truncatedLength))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("out of bounds");
  }

  private static Frame encode(final byte[] data) {
    final var buffer = new UnsafeBuffer(new byte[MESSAGE_OFFSET + 1024]);
    final var headerEncoder = new MessageHeaderEncoder();
    final var bodyEncoder = new FetchResponseEncoder();
    bodyEncoder
        .wrapAndApplyHeader(buffer, MESSAGE_OFFSET, headerEncoder)
        .errorCode(ErrorCode.NONE)
        .firstPosition(11)
        .lastPosition(17)
        .highWatermark(42)
        .putData(data, 0, data.length);
    final int length = headerEncoder.encodedLength() + bodyEncoder.encodedLength();
    final int dataOffset = MESSAGE_OFFSET + length - data.length;
    return new Frame(buffer, length, dataOffset);
  }

  private record Frame(UnsafeBuffer buffer, int length, int dataOffset) {}
}
