/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.asyncrequest;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.RecordV1s;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.AsyncRequestRecord;
import io.camunda.zeebe.protocol.impl.record.value.AsyncRequestRecordV1;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link AsyncRequestMetadataValueV1} is a frozen copy of the property based implementation
 * the generated codec replaced.
 */
final class AsyncRequestMetadataValueSerdeTest {

  private static AsyncRequestRecord record() {
    return new AsyncRequestRecord()
        .setScopeKey(7L)
        .setRequestId(9L)
        .setStorageOrdinal(3)
        .setOperationReference(200L);
  }

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new AsyncRequestMetadataValueV1();
    final var current = new AsyncRequestMetadataValue();
    v1.wrap(42L, RecordV1s.convert(record(), new AsyncRequestRecordV1()));
    current.wrap(42L, record());

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(currentLength).isEqualTo(current.getLength()).isEqualTo(v1.getLength());
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldWriteTheSameBytesAsV1WithDefaults() {
    // given
    final var v1 = new AsyncRequestMetadataValueV1();
    final var current = new AsyncRequestMetadataValue();

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new AsyncRequestMetadataValueV1();
    v1.wrap(42L, RecordV1s.convert(record(), new AsyncRequestRecordV1()));
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new AsyncRequestMetadataValue();
    current.wrap(7L, new AsyncRequestRecord().setScopeKey(1L).setStorageOrdinal(5));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getAsyncRequestKey()).isEqualTo(42L);
    assertThat(current.getRecord().getScopeKey()).isEqualTo(7L);
    assertThat(current.getRecord().getRequestId()).isEqualTo(9L);
    assertThat(current.getRecord().getStorageOrdinal()).isEqualTo(3);
    assertThat(current.getRecord().getOperationReference()).isEqualTo(200L);
  }

  @Test
  void shouldResetToDefaultsWhenReadingBytesWithDefaults() {
    // given
    final var bytes = new ExpandableArrayBuffer();
    final int length = new AsyncRequestMetadataValueV1().write(bytes, 0);
    final var current = new AsyncRequestMetadataValue();
    current.wrap(7L, record());

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults = new AsyncRequestMetadataValueV1();
    assertThat(current.getAsyncRequestKey()).isEqualTo(defaults.getAsyncRequestKey());
    assertThat(current.getRecord().getScopeKey()).isEqualTo(defaults.getRecord().getScopeKey());
    assertThat(current.getRecord().getRequestId()).isEqualTo(defaults.getRecord().getRequestId());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new AsyncRequestMetadataValue();
    current.wrap(42L, record());
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new AsyncRequestMetadataValueV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getAsyncRequestKey()).isEqualTo(42L);
    assertThat(v1.getRecord().getScopeKey()).isEqualTo(7L);
    assertThat(v1.getRecord().getOperationReference()).isEqualTo(200L);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new AsyncRequestMetadataValue();
    source.wrap(42L, record());
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has two entries, plus one unknown: 0x82 becomes 0x83
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new AsyncRequestMetadataValue();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.wrap(43L, record());
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldNotReferenceTheBufferItWasReadFrom() {
    // given
    final var instance = new AsyncRequestMetadataValue();
    instance.wrap(42L, record());
    final var buffer = new ExpandableArrayBuffer();
    final int length = instance.write(buffer, 0);
    final var read = new AsyncRequestMetadataValue();
    read.wrap(buffer, 0, length);

    // when the buffer is reused
    buffer.setMemory(0, buffer.capacity(), (byte) 0);

    // then
    assertThat(read.getRecord().getScopeKey()).isEqualTo(7L);
    assertThat(read.getRecord().getOperationReference()).isEqualTo(200L);
  }

  private static DirectBuffer utf8(final String s) {
    return new UnsafeBuffer(s.getBytes(StandardCharsets.UTF_8));
  }

  private static String hex(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
