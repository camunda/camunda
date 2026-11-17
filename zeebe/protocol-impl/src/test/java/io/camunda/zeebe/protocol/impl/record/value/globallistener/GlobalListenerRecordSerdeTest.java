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
import io.camunda.zeebe.protocol.record.value.GlobalListenerSource;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link
 * io.camunda.zeebe.protocol.impl.record.value.globallistener.legacy.GlobalListenerRecord} is a
 * frozen copy of the property based implementation the generated codec replaced.
 */
final class GlobalListenerRecordSerdeTest {

  private static final String ID = "a-listener-id-that-is-longer-than-thirty-two-bytes";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.globallistener.legacy
            .GlobalListenerRecord();
    final var current = new GlobalListenerRecord();
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
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.globallistener.legacy
            .GlobalListenerRecord();
    final var current = new GlobalListenerRecord();

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
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.globallistener.legacy
            .GlobalListenerRecord();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new GlobalListenerRecord();
    current
        .setGlobalListenerKey(7L)
        .setId("dirty")
        .setType("dirty")
        .setRetries(9)
        .setEventTypes(List.of("dirty"))
        .setAfterNonGlobal(true)
        .setPriority(1)
        .setSource(GlobalListenerSource.API)
        .setConfigKey(8L);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertAllProperties(current);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with defaults everywhere
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.globallistener.legacy
            .GlobalListenerRecord();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new GlobalListenerRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertDefaultsOf(
        new io.camunda.zeebe.protocol.impl.record.value.globallistener.legacy
            .GlobalListenerRecord(),
        current);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new GlobalListenerRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.globallistener.legacy
            .GlobalListenerRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertAllProperties(v1);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new GlobalListenerRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has ten entries, plus one unknown: 0x8a becomes 0x8b
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new GlobalListenerRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setPriority(9);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.globallistener.legacy
            .GlobalListenerRecord();
    final var current = new GlobalListenerRecord();
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

  private static void setAllProperties(
      final io.camunda.zeebe.protocol.impl.record.value.globallistener.legacy.GlobalListenerRecord
          record) {
    record
        .setGlobalListenerKey(4_294_967_296L)
        .setId(ID)
        .setType("listener-type")
        .setRetries(300)
        .setEventTypes(List.of("creating", "assigning"))
        .setAfterNonGlobal(true)
        .setPriority(70_000)
        .setSource(GlobalListenerSource.API)
        .setConfigKey(65_536L);
  }

  private static void setAllProperties(final GlobalListenerRecord record) {
    record
        .setGlobalListenerKey(4_294_967_296L)
        .setId(ID)
        .setType("listener-type")
        .setRetries(300)
        .setEventTypes(List.of("creating", "assigning"))
        .setAfterNonGlobal(true)
        .setPriority(70_000)
        .setSource(GlobalListenerSource.API)
        .setConfigKey(65_536L);
  }

  private static void assertAllProperties(
      final io.camunda.zeebe.protocol.impl.record.value.globallistener.legacy.GlobalListenerRecord
          record) {
    assertThat(record.getGlobalListenerKey()).isEqualTo(4_294_967_296L);
    assertThat(record.getId()).isEqualTo(ID);
    assertThat(record.getType()).isEqualTo("listener-type");
    assertThat(record.getRetries()).isEqualTo(300);
    assertThat(record.getEventTypes()).containsExactly("creating", "assigning");
    assertThat(record.isAfterNonGlobal()).isTrue();
    assertThat(record.getPriority()).isEqualTo(70_000);
    assertThat(record.getSource()).isEqualTo(GlobalListenerSource.API);
    assertThat(record.getConfigKey()).isEqualTo(65_536L);
  }

  private static void assertAllProperties(final GlobalListenerRecord record) {
    assertThat(record.getGlobalListenerKey()).isEqualTo(4_294_967_296L);
    assertThat(record.getId()).isEqualTo(ID);
    assertThat(record.getType()).isEqualTo("listener-type");
    assertThat(record.getRetries()).isEqualTo(300);
    assertThat(record.getEventTypes()).containsExactly("creating", "assigning");
    assertThat(record.isAfterNonGlobal()).isTrue();
    assertThat(record.getPriority()).isEqualTo(70_000);
    assertThat(record.getSource()).isEqualTo(GlobalListenerSource.API);
    assertThat(record.getConfigKey()).isEqualTo(65_536L);
  }

  private static void assertDefaultsOf(
      final io.camunda.zeebe.protocol.impl.record.value.globallistener.legacy.GlobalListenerRecord
          defaults,
      final GlobalListenerRecord record) {
    assertThat(record.getGlobalListenerKey()).isEqualTo(defaults.getGlobalListenerKey());
    assertThat(record.getId()).isEqualTo(defaults.getId());
    assertThat(record.getType()).isEqualTo(defaults.getType());
    assertThat(record.getRetries()).isEqualTo(defaults.getRetries());
    assertThat(record.getEventTypes()).isEqualTo(defaults.getEventTypes());
    assertThat(record.isAfterNonGlobal()).isEqualTo(defaults.isAfterNonGlobal());
    assertThat(record.getPriority()).isEqualTo(defaults.getPriority());
    assertThat(record.getSource()).isEqualTo(defaults.getSource());
    assertThat(record.getListenerType()).isEqualTo(defaults.getListenerType());
    assertThat(record.getConfigKey()).isEqualTo(defaults.getConfigKey());
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
