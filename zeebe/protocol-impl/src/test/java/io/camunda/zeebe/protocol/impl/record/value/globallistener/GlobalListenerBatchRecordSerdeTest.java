/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.globallistener;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link GlobalListenerBatchRecordV1} is a frozen copy of the property based implementation
 * the generated codec replaced.
 */
final class GlobalListenerBatchRecordSerdeTest {

  private static void populate(final GlobalListenerBatchRecordV1 record) {
    final var first = new GlobalListenerRecordV1().setGlobalListenerKey(300L).setId("first");
    final var second = new GlobalListenerRecordV1().setGlobalListenerKey(70_000L).setId("second");
    record
        .setGlobalListenerBatchKey(4_294_967_296L)
        .addListener(first)
        .addListener(second)
        .addCreatedListener(first)
        .addUpdatedListener(second)
        .addDeletedListener(new GlobalListenerRecordV1().setGlobalListenerKey(65_536L));
  }

  private static void populate(final GlobalListenerBatchRecord record) {
    final var first = new GlobalListenerRecord().setGlobalListenerKey(300L).setId("first");
    final var second = new GlobalListenerRecord().setGlobalListenerKey(70_000L).setId("second");
    record
        .setGlobalListenerBatchKey(4_294_967_296L)
        .addListener(first)
        .addListener(second)
        .addCreatedListener(first)
        .addUpdatedListener(second)
        .addDeletedListener(new GlobalListenerRecord().setGlobalListenerKey(65_536L));
  }

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new GlobalListenerBatchRecordV1();
    final var current = new GlobalListenerBatchRecord();
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
    // given everything stays default
    final var v1 = new GlobalListenerBatchRecordV1();
    final var current = new GlobalListenerBatchRecord();

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
    final var v1 = new GlobalListenerBatchRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new GlobalListenerBatchRecord();
    current
        .setGlobalListenerBatchKey(9L)
        .addListener(new GlobalListenerRecord().setId("dirty"))
        .addCreatedListener(new GlobalListenerRecord().setGlobalListenerKey(1L))
        .addUpdatedListener(new GlobalListenerRecord().setGlobalListenerKey(2L))
        .addDeletedListener(new GlobalListenerRecord().setGlobalListenerKey(3L));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertAllProperties(current);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with defaults everywhere
    final var v1 = new GlobalListenerBatchRecordV1();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new GlobalListenerBatchRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertDefaultsOf(new GlobalListenerBatchRecordV1(), current);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new GlobalListenerBatchRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new GlobalListenerBatchRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertAllProperties(v1);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new GlobalListenerBatchRecord();
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
    final var instance = new GlobalListenerBatchRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setGlobalListenerBatchKey(9L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new GlobalListenerBatchRecordV1();
    final var current = new GlobalListenerBatchRecord();
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

  private static void setAllProperties(final GlobalListenerBatchRecordV1 record) {
    populate(record);
  }

  private static void setAllProperties(final GlobalListenerBatchRecord record) {
    populate(record);
  }

  private static void assertAllProperties(final GlobalListenerBatchRecordV1 record) {
    assertThat(record.getGlobalListenerBatchKey()).isEqualTo(4_294_967_296L);
    assertThat(record.getListeners())
        .extracting(GlobalListenerRecordValue::getId)
        .containsExactly("first", "second");
    assertThat(record.getCreatedListenerKeys()).containsExactly(300L);
    assertThat(record.getUpdatedListenerKeys()).containsExactly(70_000L);
    assertThat(record.getDeletedListenerKeys()).containsExactly(65_536L);
  }

  private static void assertAllProperties(final GlobalListenerBatchRecord record) {
    assertThat(record.getGlobalListenerBatchKey()).isEqualTo(4_294_967_296L);
    assertThat(record.getListeners())
        .extracting(GlobalListenerRecordValue::getId)
        .containsExactly("first", "second");
    assertThat(record.getCreatedListenerKeys()).containsExactly(300L);
    assertThat(record.getUpdatedListenerKeys()).containsExactly(70_000L);
    assertThat(record.getDeletedListenerKeys()).containsExactly(65_536L);
  }

  private static void assertDefaultsOf(
      final GlobalListenerBatchRecordV1 defaults, final GlobalListenerBatchRecord record) {
    assertThat(record.getGlobalListenerBatchKey()).isEqualTo(defaults.getGlobalListenerBatchKey());
    assertThat(record.getListeners()).isEmpty();
    assertThat(record.getCreatedListenerKeys()).isEmpty();
    assertThat(record.getUpdatedListenerKeys()).isEmpty();
    assertThat(record.getDeletedListenerKeys()).isEmpty();
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
