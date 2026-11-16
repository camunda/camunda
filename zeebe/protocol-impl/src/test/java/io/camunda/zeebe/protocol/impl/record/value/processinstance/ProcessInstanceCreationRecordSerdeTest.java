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
import io.camunda.zeebe.protocol.record.value.RuntimeInstructionType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
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
final class ProcessInstanceCreationRecordSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new ProcessInstanceCreationRecordV1();
    final var current = new ProcessInstanceCreationRecord();
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
    final var v1 = new ProcessInstanceCreationRecordV1();
    final var current = new ProcessInstanceCreationRecord();

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
    final var v1 = new ProcessInstanceCreationRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ProcessInstanceCreationRecord();
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
    assertThat(current.getRootProcessInstanceKey()).isEqualTo(v1.getRootProcessInstanceKey());
    assertThat(current.getBusinessId()).isEqualTo(v1.getBusinessId());
    assertThat(current.getVersionTag()).isEqualTo(v1.getVersionTag());
    assertThat(current.getStorageOrdinal()).isEqualTo(v1.getStorageOrdinal());
    assertThat(current.getStartInstructions()).hasSize(2);
    assertThat(current.getRuntimeInstructions()).hasSize(1);
    assertThat(current.fetchVariables()).hasSize(2);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with every property at its default
    final var v1 = new ProcessInstanceCreationRecordV1();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ProcessInstanceCreationRecord();
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
    final var current = new ProcessInstanceCreationRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new ProcessInstanceCreationRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getBpmnProcessId()).isEqualTo(current.getBpmnProcessId());
    assertThat(v1.getProcessDefinitionKey()).isEqualTo(current.getProcessDefinitionKey());
    assertThat(v1.getProcessInstanceKey()).isEqualTo(current.getProcessInstanceKey());
    assertThat(v1.getVersion()).isEqualTo(current.getVersion());
    assertThat(v1.getTenantId()).isEqualTo(current.getTenantId());
    assertThat(v1.getVariablesBuffer()).isEqualTo(current.getVariablesBuffer());
    assertThat(v1.getTags()).isEqualTo(current.getTags());
    assertThat(v1.getRootProcessInstanceKey()).isEqualTo(current.getRootProcessInstanceKey());
    assertThat(v1.getBusinessId()).isEqualTo(current.getBusinessId());
    assertThat(v1.getVersionTag()).isEqualTo(current.getVersionTag());
    assertThat(v1.getStorageOrdinal()).isEqualTo(current.getStorageOrdinal());
    assertThat(v1.getStartInstructions()).hasSize(2);
    assertThat(v1.getRuntimeInstructions()).hasSize(1);
    assertThat(v1.fetchVariables()).hasSize(2);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ProcessInstanceCreationRecord();
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
    final var instance = new ProcessInstanceCreationRecord();
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
    final var v1 = new ProcessInstanceCreationRecordV1();
    final var current = new ProcessInstanceCreationRecord();
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

  private static void setAllProperties(final ProcessInstanceCreationRecordV1 record) {
    record
        .setBpmnProcessId("process")
        .setProcessDefinitionKey(1L)
        .setProcessInstanceKey(2L)
        .setVersion(3)
        .setVariables(document())
        .setFetchVariables(List.of("a", "bb"))
        .setTenantId("tenant")
        .setTags(Set.of("tag"))
        .setRootProcessInstanceKey(5L)
        .setStorageOrdinal(4)
        .setBusinessId("business")
        .setVersionTag("tag-1");
    record.addStartInstruction(startInstruction("start-1"));
    record.addStartInstruction(startInstruction("start-2"));
    record.addRuntimeInstruction(runtimeInstruction("after-1"));
  }

  private static void setAllProperties(final ProcessInstanceCreationRecord record) {
    record
        .setBpmnProcessId("process")
        .setProcessDefinitionKey(1L)
        .setProcessInstanceKey(2L)
        .setVersion(3)
        .setVariables(document())
        .setFetchVariables(List.of("a", "bb"))
        .setTenantId("tenant")
        .setTags(Set.of("tag"))
        .setRootProcessInstanceKey(5L)
        .setStorageOrdinal(4)
        .setBusinessId("business")
        .setVersionTag("tag-1");
    record.addStartInstruction(startInstruction("start-1"));
    record.addStartInstruction(startInstruction("start-2"));
    record.addRuntimeInstruction(runtimeInstruction("after-1"));
  }

  private static void setOtherProperties(final ProcessInstanceCreationRecord record) {
    record
        .setBpmnProcessId("other")
        .setProcessDefinitionKey(11L)
        .setProcessInstanceKey(12L)
        .setVersion(13)
        .setVariables(document())
        .setFetchVariables(List.of("c"))
        .setTenantId("other")
        .setTags(Set.of("other", "more"))
        .setRootProcessInstanceKey(15L)
        .setStorageOrdinal(14)
        .setBusinessId("other")
        .setVersionTag("other");
    record.addStartInstruction(startInstruction("other-start"));
  }

  private static ProcessInstanceCreationStartInstruction startInstruction(final String elementId) {
    return new ProcessInstanceCreationStartInstruction().setElementId(elementId);
  }

  private static ProcessInstanceCreationRuntimeInstruction runtimeInstruction(
      final String afterElementId) {
    return new ProcessInstanceCreationRuntimeInstruction()
        .setType(RuntimeInstructionType.TERMINATE_PROCESS_INSTANCE)
        .setAfterElementId(afterElementId);
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
