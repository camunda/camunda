/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link ErrorRecordV1} is a frozen copy of the property based implementation the generated
 * codec replaced.
 */
final class ErrorRecordSerdeTest {

  // the same throwable for both classes, so that the stack traces are identical
  private static final Throwable THROWABLE =
      new IllegalStateException("an exception message that is longer than thirty two bytes");

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new ErrorRecordV1();
    final var current = new ErrorRecord();
    v1.initErrorRecord(THROWABLE, 4_294_967_296L);
    current.initErrorRecord(THROWABLE, 4_294_967_296L);
    v1.setProcessInstanceKey(4_294_967_297L);
    current.setProcessInstanceKey(4_294_967_297L);
    v1.setStorageOrdinal(300);
    current.setStorageOrdinal(300);

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
    // given only what cannot be written unset, everything else stays default
    final var v1 = new ErrorRecordV1();
    final var current = new ErrorRecord();
    v1.initErrorRecord(THROWABLE, 0L);
    current.initErrorRecord(THROWABLE, 0L);

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
    final var v1 = new ErrorRecordV1();
    v1.initErrorRecord(THROWABLE, 42L);
    v1.setProcessInstanceKey(300L);
    v1.setStorageOrdinal(300);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ErrorRecord();
    current.initErrorRecord(new IllegalStateException("other"), 7L);
    current.setProcessInstanceKey(7L);
    current.setStorageOrdinal(5);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getExceptionMessage())
        .isEqualTo("an exception message that is longer than thirty two bytes");
    assertThat(current.getStacktrace()).isEqualTo(v1.getStacktrace());
    assertThat(current.getErrorEventPosition()).isEqualTo(42L);
    assertThat(current.getProcessInstanceKey()).isEqualTo(300L);
    assertThat(current.getStorageOrdinal()).isEqualTo(300);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given
    final var v1 = new ErrorRecordV1();
    v1.initErrorRecord(THROWABLE, 0L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ErrorRecord();
    current.initErrorRecord(new IllegalStateException("other"), 7L);
    current.setProcessInstanceKey(7L);
    current.setStorageOrdinal(5);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults = new ErrorRecordV1();
    assertThat(current.getProcessInstanceKey()).isEqualTo(defaults.getProcessInstanceKey());
    assertThat(current.getStorageOrdinal()).isEqualTo(defaults.getStorageOrdinal());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new ErrorRecord();
    current.initErrorRecord(THROWABLE, 42L);
    current.setProcessInstanceKey(300L);
    current.setStorageOrdinal(300);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new ErrorRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getExceptionMessage())
        .isEqualTo("an exception message that is longer than thirty two bytes");
    assertThat(v1.getStacktrace()).isEqualTo(current.getStacktrace());
    assertThat(v1.getErrorEventPosition()).isEqualTo(42L);
    assertThat(v1.getProcessInstanceKey()).isEqualTo(300L);
    assertThat(v1.getStorageOrdinal()).isEqualTo(300);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ErrorRecord();
    source.initErrorRecord(THROWABLE, 42L);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has five entries, plus one unknown: 0x85 becomes 0x86
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new ErrorRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setStorageOrdinal(9);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new ErrorRecordV1();
    final var current = new ErrorRecord();
    v1.initErrorRecord(THROWABLE, 42L);
    current.initErrorRecord(THROWABLE, 42L);
    v1.setProcessInstanceKey(300L);
    current.setProcessInstanceKey(300L);
    v1.setStorageOrdinal(300);
    current.setStorageOrdinal(300);

    // when
    final var v1Json = v1.toJson();
    final var currentJson = current.toJson();

    // then exporters see the same document; compare the trees, Jackson does not guarantee the order
    // of the properties and it differs between the old and the new class
    final var mapper = new ObjectMapper();
    assertThat(mapper.readTree(currentJson)).isEqualTo(mapper.readTree(v1Json));
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
