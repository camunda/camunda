/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.escalation;

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
 * break. {@link EscalationRecordV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class EscalationRecordSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new EscalationRecordV1();
    final var current = new EscalationRecord();
    v1.setProcessInstanceKey(4_294_967_296L);
    current.setProcessInstanceKey(4_294_967_296L);
    v1.setEscalationCode("an-escalation-code-that-is-longer-than-32-bytes");
    current.setEscalationCode("an-escalation-code-that-is-longer-than-32-bytes");
    v1.setThrowElementId(utf8("throw-element"));
    current.setThrowElementId(utf8("throw-element"));
    v1.setCatchElementId(utf8("catch-element"));
    current.setCatchElementId(utf8("catch-element"));
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
    // given
    final var v1 = new EscalationRecordV1();
    final var current = new EscalationRecord();

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
    final var v1 = new EscalationRecordV1();
    v1.setProcessInstanceKey(42L);
    v1.setEscalationCode("code-a");
    v1.setThrowElementId(utf8("throw-a"));
    v1.setCatchElementId(utf8("catch-a"));
    v1.setStorageOrdinal(300);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new EscalationRecord();
    current.setProcessInstanceKey(7L);
    current.setEscalationCode("code-b");
    current.setThrowElementId(utf8("throw-b"));
    current.setCatchElementId(utf8("catch-b"));
    current.setStorageOrdinal(5);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getProcessInstanceKey()).isEqualTo(42L);
    assertThat(current.getEscalationCode()).isEqualTo("code-a");
    assertThat(current.getThrowElementId()).isEqualTo("throw-a");
    assertThat(current.getCatchElementId()).isEqualTo("catch-a");
    assertThat(current.getStorageOrdinal()).isEqualTo(300);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given
    final var v1 = new EscalationRecordV1();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new EscalationRecord();
    current.setProcessInstanceKey(7L);
    current.setEscalationCode("code-b");
    current.setThrowElementId(utf8("throw-b"));
    current.setCatchElementId(utf8("catch-b"));
    current.setStorageOrdinal(5);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults = new EscalationRecordV1();
    assertThat(current.getProcessInstanceKey()).isEqualTo(defaults.getProcessInstanceKey());
    assertThat(current.getEscalationCode()).isEqualTo(defaults.getEscalationCode());
    assertThat(current.getThrowElementId()).isEqualTo(defaults.getThrowElementId());
    assertThat(current.getCatchElementId()).isEqualTo(defaults.getCatchElementId());
    assertThat(current.getStorageOrdinal()).isEqualTo(defaults.getStorageOrdinal());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new EscalationRecord();
    current.setProcessInstanceKey(42L);
    current.setEscalationCode("code-a");
    current.setThrowElementId(utf8("throw-a"));
    current.setCatchElementId(utf8("catch-a"));
    current.setStorageOrdinal(300);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new EscalationRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getProcessInstanceKey()).isEqualTo(42L);
    assertThat(v1.getEscalationCode()).isEqualTo("code-a");
    assertThat(v1.getThrowElementId()).isEqualTo("throw-a");
    assertThat(v1.getCatchElementId()).isEqualTo("catch-a");
    assertThat(v1.getStorageOrdinal()).isEqualTo(300);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new EscalationRecord();
    source.setProcessInstanceKey(42L);
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
    final var instance = new EscalationRecord();
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
    final var v1 = new EscalationRecordV1();
    final var current = new EscalationRecord();
    v1.setProcessInstanceKey(42L);
    current.setProcessInstanceKey(42L);
    v1.setEscalationCode("code-a");
    current.setEscalationCode("code-a");
    v1.setThrowElementId(utf8("throw-a"));
    current.setThrowElementId(utf8("throw-a"));
    v1.setCatchElementId(utf8("catch-a"));
    current.setCatchElementId(utf8("catch-a"));
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
