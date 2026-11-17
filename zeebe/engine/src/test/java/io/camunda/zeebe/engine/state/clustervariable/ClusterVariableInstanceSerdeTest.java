/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.clustervariable;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.RecordV1s;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.engine.state.clustervariable.legacy.ClusterVariableInstance} is a
 * frozen copy of the property based implementation the generated codec replaced.
 */
final class ClusterVariableInstanceSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var record = populatedRecord();
    final var v1 =
        new io.camunda.zeebe.engine.state.clustervariable.legacy.ClusterVariableInstance();
    final var current = new ClusterVariableInstance();
    v1.setRecord(
        RecordV1s.convert(
            record,
            new io.camunda.zeebe.protocol.impl.record.value.clustervariable.legacy
                .ClusterVariableRecord()));
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
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.engine.state.clustervariable.legacy.ClusterVariableInstance();
    v1.setRecord(
        RecordV1s.convert(
            populatedRecord(),
            new io.camunda.zeebe.protocol.impl.record.value.clustervariable.legacy
                .ClusterVariableRecord()));
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ClusterVariableInstance();
    current.setRecord(new ClusterVariableRecord().setName("other"));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getRecord().getName()).isEqualTo("name");
    assertThat(current.getRecord().getTenantId()).isEqualTo("tenant");
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new ClusterVariableInstance();
    current.setRecord(populatedRecord());
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.engine.state.clustervariable.legacy.ClusterVariableInstance();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getRecord().getName()).isEqualTo("name");
    assertThat(v1.getRecord().getTenantId()).isEqualTo("tenant");
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ClusterVariableInstance();
    source.setRecord(populatedRecord());
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // The map has one entry, a header of 0x80 | count, so announce one more.
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new ClusterVariableInstance();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setRecord(new ClusterVariableRecord().setName("other"));
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static ClusterVariableRecord populatedRecord() {
    return new ClusterVariableRecord()
        .setName("name")
        .setValue(BufferUtil.wrapString("\"value\""))
        .setTenantId("tenant");
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
