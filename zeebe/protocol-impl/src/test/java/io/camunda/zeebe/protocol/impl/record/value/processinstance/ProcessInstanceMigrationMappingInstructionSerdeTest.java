/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.LegacyObjectValues;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. The legacy class is a frozen copy of the property based implementation the generated codec
 * replaced.
 */
final class ProcessInstanceMigrationMappingInstructionSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceMigrationMappingInstruction();
    final var current = new ProcessInstanceMigrationMappingInstruction();
    v1.setSourceElementId("a-long-id-above-thirty-one-bytes-0123456789")
        .setTargetElementId("target");
    current
        .setSourceElementId("a-long-id-above-thirty-one-bytes-0123456789")
        .setTargetElementId("target");

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = LegacyObjectValues.write(v1, v1Bytes);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(currentLength)
        .isEqualTo(current.getLength())
        .isEqualTo(LegacyObjectValues.length(v1));
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldWriteTheSameBytesAsV1WithDefaults() {
    // given nothing is required, everything stays default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceMigrationMappingInstruction();
    final var current = new ProcessInstanceMigrationMappingInstruction();

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = LegacyObjectValues.write(v1, v1Bytes);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceMigrationMappingInstruction();
    v1.setSourceElementId("source").setTargetElementId("target");
    final var bytes = new ExpandableArrayBuffer();
    final int length = LegacyObjectValues.write(v1, bytes);
    final var current = new ProcessInstanceMigrationMappingInstruction();
    current.setSourceElementId("dirty-source").setTargetElementId("dirty-target");

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getSourceElementId()).isEqualTo("source");
    assertThat(current.getTargetElementId()).isEqualTo("target");
  }

  @Test
  void shouldRestoreDefaultsWhenReadingPartialBytes() {
    // given bytes of a value that never set its properties
    final var bytes = new ExpandableArrayBuffer();
    final int length =
        LegacyObjectValues.write(
            new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
                .ProcessInstanceMigrationMappingInstruction(),
            bytes);
    final var current = new ProcessInstanceMigrationMappingInstruction();
    current.setSourceElementId("dirty-source").setTargetElementId("dirty-target");

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getSourceElementId()).isEmpty();
    assertThat(current.getTargetElementId()).isEmpty();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new ProcessInstanceMigrationMappingInstruction();
    current.setSourceElementId("source").setTargetElementId("target");
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceMigrationMappingInstruction();
    LegacyObjectValues.read(v1, bytes, length);

    // then
    assertThat(v1.getSourceElementId()).isEqualTo("source");
    assertThat(v1.getTargetElementId()).isEqualTo("target");
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ProcessInstanceMigrationMappingInstruction();
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new ProcessInstanceMigrationMappingInstruction();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setSourceElementId("modified");
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
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceMigrationMappingInstruction();
    final var current = new ProcessInstanceMigrationMappingInstruction();
    v1.setSourceElementId("source").setTargetElementId("target");
    current.setSourceElementId("source").setTargetElementId("target");

    // when
    final var mapper = new ObjectMapper();
    final var v1Json = mapper.valueToTree(v1);
    final var currentJson = mapper.valueToTree(current);

    // then the parent record serializes the value with Jackson, so the documents must be equal
    assertThat(currentJson).isEqualTo(v1Json);
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
