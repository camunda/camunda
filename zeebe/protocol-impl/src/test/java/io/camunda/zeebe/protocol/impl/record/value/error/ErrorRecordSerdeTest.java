/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
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

  private static final Throwable THROWABLE =
      new IllegalStateException("an exception message that is longer than forty bytes");

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new ErrorRecordV1();
    final var current = new ErrorRecord();
    setAllProperties(v1);
    setAllProperties(current);

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
    // given only the properties without a default are set, everything else stays default
    final var v1 = new ErrorRecordV1();
    final var current = new ErrorRecord();
    setRequiredProperties(v1);
    setRequiredProperties(current);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldFailToWriteWhileUnsetLikeV1() {
    // given
    final var v1 = new ErrorRecordV1();
    final var current = new ErrorRecord();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new ErrorRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ErrorRecord();
    current.initErrorRecord(new IllegalArgumentException("dirty"), 99L);
    current.setProcessInstanceKey(9L).setStorageOrdinal(5);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertAllProperties(current);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with defaults everywhere except the properties without a default
    final var v1 = new ErrorRecordV1();
    setRequiredProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ErrorRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults = new ErrorRecordV1();
    setRequiredProperties(defaults);
    assertDefaultsOf(defaults, current);
  }

  @Test
  void shouldFailToReadWithoutTheKeysLikeV1() {
    // given an empty map
    final var bytes = new ExpandableArrayBuffer(1);
    bytes.putByte(0, (byte) 0x80);

    // when
    final var v1Failure = catchThrowable(() -> new ErrorRecordV1().wrap(bytes, 0, 1));
    final var failure = catchThrowable(() -> new ErrorRecord().wrap(bytes, 0, 1));

    // then the same failure as V1
    assertThat(failure.getCause()).isExactlyInstanceOf(v1Failure.getCause().getClass());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new ErrorRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new ErrorRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertAllProperties(v1);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ErrorRecord();
    setAllProperties(source);
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
    setAllProperties(v1);
    setAllProperties(current);

    // when
    final var v1Json = v1.toJson();
    final var currentJson = current.toJson();

    // then exporters see the same document; compare the trees, Jackson does not guarantee the order
    // of the properties and it differs between the old and the new class
    final var mapper = new ObjectMapper();
    assertThat(mapper.readTree(currentJson)).isEqualTo(mapper.readTree(v1Json));
  }

  private static void setAllProperties(final ErrorRecordV1 record) {
    record.initErrorRecord(THROWABLE, 42L);
    record.setProcessInstanceKey(7L).setStorageOrdinal(3);
  }

  private static void setAllProperties(final ErrorRecord record) {
    record.initErrorRecord(THROWABLE, 42L);
    record.setProcessInstanceKey(7L).setStorageOrdinal(3);
  }

  private static void setRequiredProperties(final ErrorRecordV1 record) {
    record.initErrorRecord(THROWABLE, 42L);
  }

  private static void setRequiredProperties(final ErrorRecord record) {
    record.initErrorRecord(THROWABLE, 42L);
  }

  private static void assertAllProperties(final ErrorRecordV1 record) {
    assertThat(record.getExceptionMessage())
        .isEqualTo("an exception message that is longer than forty bytes");
    assertThat(record.getStacktrace()).startsWith(THROWABLE.toString());
    assertThat(record.getErrorEventPosition()).isEqualTo(42L);
    assertThat(record.getProcessInstanceKey()).isEqualTo(7L);
    assertThat(record.getStorageOrdinal()).isEqualTo(3);
  }

  private static void assertAllProperties(final ErrorRecord record) {
    assertThat(record.getExceptionMessage())
        .isEqualTo("an exception message that is longer than forty bytes");
    assertThat(record.getStacktrace()).startsWith(THROWABLE.toString());
    assertThat(record.getErrorEventPosition()).isEqualTo(42L);
    assertThat(record.getProcessInstanceKey()).isEqualTo(7L);
    assertThat(record.getStorageOrdinal()).isEqualTo(3);
  }

  private static void assertDefaultsOf(final ErrorRecordV1 defaults, final ErrorRecord record) {
    assertThat(record.getExceptionMessage()).isEqualTo(defaults.getExceptionMessage());
    assertThat(record.getStacktrace()).isEqualTo(defaults.getStacktrace());
    assertThat(record.getErrorEventPosition()).isEqualTo(defaults.getErrorEventPosition());
    assertThat(record.getProcessInstanceKey()).isEqualTo(defaults.getProcessInstanceKey());
    assertThat(record.getStorageOrdinal()).isEqualTo(defaults.getStorageOrdinal());
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
