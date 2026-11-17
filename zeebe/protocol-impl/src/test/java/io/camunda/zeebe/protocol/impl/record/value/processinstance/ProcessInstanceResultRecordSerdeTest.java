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
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. The V1 class is a frozen copy of the property based implementation the generated codec
 * replaced.
 */
final class ProcessInstanceResultRecordSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceResultRecord();
    final var current = new ProcessInstanceResultRecord();
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
    // given only what cannot be written unset, everything else stays default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceResultRecord();
    final var current = new ProcessInstanceResultRecord();

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
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceResultRecord();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ProcessInstanceResultRecord();
    setOtherProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getBpmnProcessId()).isEqualTo(v1.getBpmnProcessId());
    assertThat(current.getProcessDefinitionKey()).isEqualTo(v1.getProcessDefinitionKey());
    assertThat(current.getProcessInstanceKey()).isEqualTo(v1.getProcessInstanceKey());
    assertThat(current.getVersion()).isEqualTo(v1.getVersion());
    assertThat(current.getTenantId()).isEqualTo(v1.getTenantId());
    assertThat(current.getVariablesBuffer()).isEqualTo(v1.getVariablesBuffer());
    assertThat(current.getTags()).isEqualTo(v1.getTags());
    assertThat(current.getBusinessId()).isEqualTo(v1.getBusinessId());
    assertThat(current.getStorageOrdinal()).isEqualTo(v1.getStorageOrdinal());
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with every property at its default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceResultRecord();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ProcessInstanceResultRecord();
    setOtherProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then re-encoding gives the bytes of the defaults, so no dirtied property survived
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(bytes, length));
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new ProcessInstanceResultRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceResultRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getBpmnProcessId()).isEqualTo(current.getBpmnProcessId());
    assertThat(v1.getProcessDefinitionKey()).isEqualTo(current.getProcessDefinitionKey());
    assertThat(v1.getProcessInstanceKey()).isEqualTo(current.getProcessInstanceKey());
    assertThat(v1.getVersion()).isEqualTo(current.getVersion());
    assertThat(v1.getTenantId()).isEqualTo(current.getTenantId());
    assertThat(v1.getVariablesBuffer()).isEqualTo(current.getVariablesBuffer());
    assertThat(v1.getTags()).isEqualTo(current.getTags());
    assertThat(v1.getBusinessId()).isEqualTo(current.getBusinessId());
    assertThat(v1.getStorageOrdinal()).isEqualTo(current.getStorageOrdinal());
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ProcessInstanceResultRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has fewer than 16 entries, so the header is one byte (0x80 | count): add one
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new ProcessInstanceResultRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    setOtherProperties(instance);
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
            .ProcessInstanceResultRecord();
    final var current = new ProcessInstanceResultRecord();
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
      final io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
              .ProcessInstanceResultRecord
          record) {
    record
        .setBpmnProcessId("process")
        .setProcessDefinitionKey(1L)
        .setProcessInstanceKey(2L)
        .setVersion(3)
        .setTenantId("tenant")
        .setVariables(document())
        .setTags(Set.of("tag"))
        .setBusinessId("business")
        .setStorageOrdinal(4);
  }

  private static void setAllProperties(final ProcessInstanceResultRecord record) {
    record
        .setBpmnProcessId("process")
        .setProcessDefinitionKey(1L)
        .setProcessInstanceKey(2L)
        .setVersion(3)
        .setTenantId("tenant")
        .setVariables(document())
        .setTags(Set.of("tag"))
        .setBusinessId("business")
        .setStorageOrdinal(4);
  }

  private static void setOtherProperties(final ProcessInstanceResultRecord record) {
    record
        .setBpmnProcessId("other")
        .setProcessDefinitionKey(11L)
        .setProcessInstanceKey(12L)
        .setVersion(13)
        .setTenantId("other")
        .setVariables(document())
        .setTags(Set.of("other", "more"))
        .setBusinessId("other")
        .setStorageOrdinal(14);
  }

  private static DirectBuffer document() {
    return new UnsafeBuffer(new byte[] {(byte) 0x81, (byte) 0xa1, (byte) 0x61, (byte) 0x01});
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
