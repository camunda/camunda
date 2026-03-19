/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.core.logappend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RawEventRecordValueTest {

  @Nested
  class WritePath {

    @Test
    void shouldReportCorrectLengthForSmallPayload() {
      // given
      final var value = new RawEventRecordValue();
      final byte[] payload = new byte[10];
      value.wrapPayload(payload);

      // then — BIN8: 2-byte header + 10 bytes = 12
      assertThat(value.getLength()).isEqualTo(12);
    }

    @Test
    void shouldReportCorrectLengthForMediumPayload() {
      // given — 256 bytes triggers BIN16 header
      final var value = new RawEventRecordValue();
      value.wrapPayload(new byte[256]);

      // then — BIN16: 3-byte header + 256 bytes = 259
      assertThat(value.getLength()).isEqualTo(259);
    }

    @Test
    void shouldReportCorrectLengthForLargePayload() {
      // given — 65536 bytes triggers BIN32 header
      final var value = new RawEventRecordValue();
      value.wrapPayload(new byte[65536]);

      // then — BIN32: 5-byte header + 65536 bytes = 65541
      assertThat(value.getLength()).isEqualTo(65541);
    }

    @Test
    void shouldWritePayloadAsMsgpackBinary() {
      // given
      final byte[] payload = {0x01, 0x02, 0x03};
      final var value = new RawEventRecordValue();
      value.wrapPayload(payload);

      // when
      final var destBuffer = new ExpandableArrayBuffer();
      final int written = value.write(destBuffer, 0);

      // then
      assertThat(written).isEqualTo(value.getLength());
      // BIN8 header: 0xC4, length byte 3, then the 3 payload bytes
      assertThat(destBuffer.getByte(0)).isEqualTo((byte) 0xC4);
      assertThat(destBuffer.getByte(1)).isEqualTo((byte) 3);
      assertThat(destBuffer.getByte(2)).isEqualTo((byte) 0x01);
      assertThat(destBuffer.getByte(3)).isEqualTo((byte) 0x02);
      assertThat(destBuffer.getByte(4)).isEqualTo((byte) 0x03);
    }

    @Test
    void shouldWritePayloadFromDirectBufferWithOffset() {
      // given — payload starts at offset 5 in a larger buffer
      final byte[] raw = {0x00, 0x00, 0x00, 0x00, 0x00, 0x0A, 0x0B, 0x0C};
      final var srcBuffer = new UnsafeBuffer(raw);
      final var value = new RawEventRecordValue();
      value.wrapPayload(srcBuffer, 5, 3);

      // when
      final var destBuffer = new ExpandableArrayBuffer();
      value.write(destBuffer, 0);

      // then — payload bytes are 0x0A, 0x0B, 0x0C
      assertThat(destBuffer.getByte(2)).isEqualTo((byte) 0x0A);
      assertThat(destBuffer.getByte(3)).isEqualTo((byte) 0x0B);
      assertThat(destBuffer.getByte(4)).isEqualTo((byte) 0x0C);
    }

    @Test
    void shouldExposePayloadFieldsAfterWrapPayload() {
      // given
      final byte[] payload = {0x42};
      final var value = new RawEventRecordValue();
      value.wrapPayload(payload);

      // then
      assertThat(value.getRawPayloadLength()).isEqualTo(1);
      assertThat(value.getRawPayloadOffset()).isEqualTo(0);
    }
  }

  @Nested
  class ReadPath {

    @Test
    void shouldDeserializeSmallPayload() {
      // given — write a value to a buffer, then read it back
      final byte[] original = {0x10, 0x20, 0x30};
      final var writer = new RawEventRecordValue();
      writer.wrapPayload(original);
      final var encoded = new ExpandableArrayBuffer();
      writer.write(encoded, 0);

      // when — read it back via the read path
      final var reader = new RawEventRecordValue();
      reader.wrap(encoded, 0, writer.getLength());

      // then
      assertThat(reader.getRawPayloadLength()).isEqualTo(3);
      assertThat(reader.getRawPayload().getByte(reader.getRawPayloadOffset()))
          .isEqualTo((byte) 0x10);
      assertThat(reader.getRawPayload().getByte(reader.getRawPayloadOffset() + 1))
          .isEqualTo((byte) 0x20);
      assertThat(reader.getRawPayload().getByte(reader.getRawPayloadOffset() + 2))
          .isEqualTo((byte) 0x30);
    }

    @Test
    void shouldDeserializeMediumPayload() {
      // given — 256 bytes forces BIN16 header
      final byte[] original = new byte[256];
      for (int i = 0; i < original.length; i++) {
        original[i] = (byte) (i & 0xFF);
      }
      final var writer = new RawEventRecordValue();
      writer.wrapPayload(original);
      final var encoded = new ExpandableArrayBuffer();
      writer.write(encoded, 0);

      // when
      final var reader = new RawEventRecordValue();
      reader.wrap(encoded, 0, writer.getLength());

      // then
      assertThat(reader.getRawPayloadLength()).isEqualTo(256);
      for (int i = 0; i < 256; i++) {
        assertThat(reader.getRawPayload().getByte(reader.getRawPayloadOffset() + i))
            .isEqualTo((byte) (i & 0xFF));
      }
    }

    @Test
    void shouldRoundTripPayloadAtNonZeroBufferOffset() {
      // given — write at offset 10 in a larger buffer
      final byte[] original = {0x01, 0x02};
      final var writer = new RawEventRecordValue();
      writer.wrapPayload(original);
      final var encoded = new ExpandableArrayBuffer(100);
      encoded.putByte(0, (byte) 0xFF); // sentinel before
      writer.write(encoded, 10);
      encoded.putByte(10 + writer.getLength(), (byte) 0xFF); // sentinel after

      // when — read back from offset 10
      final var reader = new RawEventRecordValue();
      reader.wrap(encoded, 10, writer.getLength());

      // then
      assertThat(reader.getRawPayloadLength()).isEqualTo(2);
      assertThat(reader.getRawPayload().getByte(reader.getRawPayloadOffset()))
          .isEqualTo((byte) 0x01);
      assertThat(reader.getRawPayload().getByte(reader.getRawPayloadOffset() + 1))
          .isEqualTo((byte) 0x02);
    }

    @Test
    void shouldBeReusableForMultipleReadOperations() {
      // given — two distinct payloads
      final var value = new RawEventRecordValue();

      final byte[] first = {(byte) 0xAA};
      value.wrapPayload(first);
      final var enc1 = new ExpandableArrayBuffer();
      value.write(enc1, 0);
      final int len1 = value.getLength();

      final byte[] second = {(byte) 0xBB, (byte) 0xCC};
      value.wrapPayload(second);
      final var enc2 = new ExpandableArrayBuffer();
      value.write(enc2, 0);
      final int len2 = value.getLength();

      // when — reuse the same reader instance
      final var reader = new RawEventRecordValue();

      reader.wrap(enc1, 0, len1);
      final byte firstByte = reader.getRawPayload().getByte(reader.getRawPayloadOffset());

      reader.wrap(enc2, 0, len2);
      final byte secondByte1 = reader.getRawPayload().getByte(reader.getRawPayloadOffset());
      final byte secondByte2 = reader.getRawPayload().getByte(reader.getRawPayloadOffset() + 1);

      // then
      assertThat(firstByte).isEqualTo((byte) 0xAA);
      assertThat(reader.getRawPayloadLength()).isEqualTo(2);
      assertThat(secondByte1).isEqualTo((byte) 0xBB);
      assertThat(secondByte2).isEqualTo((byte) 0xCC);
    }

    @Test
    void shouldNotThrowOnZeroLengthPayload() {
      // given — empty payload is technically valid msgpack bin8 with length 0
      final var writer = new RawEventRecordValue();
      writer.wrapPayload(new byte[0]);
      final var encoded = new ExpandableArrayBuffer();
      writer.write(encoded, 0);

      // when / then — should not throw
      final var reader = new RawEventRecordValue();
      assertThatCode(() -> reader.wrap(encoded, 0, writer.getLength())).doesNotThrowAnyException();
      assertThat(reader.getRawPayloadLength()).isEqualTo(0);
    }
  }
}
