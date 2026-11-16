/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.suspension;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.BufferedCommandRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link DbBufferedCommandV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class DbBufferedCommandSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new DbBufferedCommandV1();
    final var current = new DbBufferedCommand();
    final var record = record("tenant-with-an-id-longer-than-thirty-two-bytes");
    v1.setRecord(record);
    current.setRecord(record);

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
    // given only defaults
    final var v1 = new DbBufferedCommandV1();
    final var current = new DbBufferedCommand();

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
    final var v1 = new DbBufferedCommandV1();
    v1.setRecord(record("tenant-a"));
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new DbBufferedCommand();
    current.setRecord(record("dirty"));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getRecord().getTenantId()).isEqualTo("tenant-a");
    assertThat(current.getRecord().getProcessInstanceKey()).isEqualTo(5L);
    assertThat(current.getRecord().getCommandKey()).isEqualTo(7L);
    assertThat(current.getRecord().getValueType()).isEqualTo(ValueType.PROCESS_INSTANCE);
  }

  @Test
  void shouldResetTheRecordToItsDefaultWhenReadFromV1WithDefaults() {
    // given
    final var v1 = new DbBufferedCommandV1();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new DbBufferedCommand();
    current.setRecord(record("dirty"));

    // when
    current.wrap(bytes, 0, length);

    // then the defaults are back: the same bytes as a fresh V1
    final var defaults = new ExpandableArrayBuffer();
    final int defaultsLength = new DbBufferedCommandV1().write(defaults, 0);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = current.write(rewritten, 0);
    assertThat(hex(rewritten, rewrittenLength)).isEqualTo(hex(defaults, defaultsLength));
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new DbBufferedCommand();
    current.setRecord(record("tenant-b"));
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new DbBufferedCommandV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getRecord().getTenantId()).isEqualTo("tenant-b");
    assertThat(v1.getRecord().getProcessInstanceKey()).isEqualTo(5L);
    assertThat(v1.getRecord().getCommandKey()).isEqualTo(7L);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new DbBufferedCommand();
    source.setRecord(record("tenant-c"));
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // The map has one entry, so its header is 0x81; announce one more entry (0x82).
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new DbBufferedCommand();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setRecord(record("tenant-d"));
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static BufferedCommandRecord record(final String tenantId) {
    return new BufferedCommandRecord()
        .setProcessInstanceKey(5L)
        .setProcessDefinitionKey(6L)
        .setCommandKey(7L)
        .setValueType(ValueType.PROCESS_INSTANCE)
        .setTenantId(tenantId)
        .setStorageOrdinal(3);
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
