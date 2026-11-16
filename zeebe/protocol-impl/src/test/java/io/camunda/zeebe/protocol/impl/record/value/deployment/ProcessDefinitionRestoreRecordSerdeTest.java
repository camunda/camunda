/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

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
 * break. {@link ProcessDefinitionRestoreRecordV1} is a frozen copy of the property based
 * implementation the generated codec replaced.
 */
final class ProcessDefinitionRestoreRecordSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new ProcessDefinitionRestoreRecordV1();
    final var current = new ProcessDefinitionRestoreRecord();
    v1.setProcessDefinitionKey(4_294_967_296L);
    current.setProcessDefinitionKey(4_294_967_296L);
    v1.setTenantId("a-tenant-identifier-that-is-longer-than-32-bytes");
    current.setTenantId("a-tenant-identifier-that-is-longer-than-32-bytes");
    v1.setRestoredCount(300);
    current.setRestoredCount(300);

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
    final var v1 = new ProcessDefinitionRestoreRecordV1();
    final var current = new ProcessDefinitionRestoreRecord();

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
    final var v1 = new ProcessDefinitionRestoreRecordV1();
    v1.setProcessDefinitionKey(42L);
    v1.setTenantId("tenant-a");
    v1.setRestoredCount(300);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ProcessDefinitionRestoreRecord();
    current.setProcessDefinitionKey(7L);
    current.setTenantId("tenant-b");
    current.setRestoredCount(5);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getProcessDefinitionKey()).isEqualTo(42L);
    assertThat(current.getTenantId()).isEqualTo("tenant-a");
    assertThat(current.getRestoredCount()).isEqualTo(300);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given
    final var v1 = new ProcessDefinitionRestoreRecordV1();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ProcessDefinitionRestoreRecord();
    current.setProcessDefinitionKey(7L);
    current.setTenantId("tenant-b");
    current.setRestoredCount(5);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults = new ProcessDefinitionRestoreRecordV1();
    assertThat(current.getProcessDefinitionKey()).isEqualTo(defaults.getProcessDefinitionKey());
    assertThat(current.getTenantId()).isEqualTo(defaults.getTenantId());
    assertThat(current.getRestoredCount()).isEqualTo(defaults.getRestoredCount());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new ProcessDefinitionRestoreRecord();
    current.setProcessDefinitionKey(42L);
    current.setTenantId("tenant-a");
    current.setRestoredCount(300);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new ProcessDefinitionRestoreRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getProcessDefinitionKey()).isEqualTo(42L);
    assertThat(v1.getTenantId()).isEqualTo("tenant-a");
    assertThat(v1.getRestoredCount()).isEqualTo(300);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ProcessDefinitionRestoreRecord();
    source.setProcessDefinitionKey(42L);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has three entries, plus one unknown: 0x83 becomes 0x84
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new ProcessDefinitionRestoreRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setRestoredCount(9);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new ProcessDefinitionRestoreRecordV1();
    final var current = new ProcessDefinitionRestoreRecord();
    v1.setProcessDefinitionKey(42L);
    current.setProcessDefinitionKey(42L);
    v1.setTenantId("tenant-a");
    current.setTenantId("tenant-a");
    v1.setRestoredCount(300);
    current.setRestoredCount(300);

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
