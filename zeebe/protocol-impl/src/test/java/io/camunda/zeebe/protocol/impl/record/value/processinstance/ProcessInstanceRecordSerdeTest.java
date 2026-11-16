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
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
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
final class ProcessInstanceRecordSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new ProcessInstanceRecordV1();
    final var current = new ProcessInstanceRecord();
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
    final var v1 = new ProcessInstanceRecordV1();
    final var current = new ProcessInstanceRecord();

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
    final var v1 = new ProcessInstanceRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ProcessInstanceRecord();
    setOtherProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getElementId()).isEqualTo(v1.getElementId());
    assertThat(current.getBpmnProcessId()).isEqualTo(v1.getBpmnProcessId());
    assertThat(current.getVersion()).isEqualTo(v1.getVersion());
    assertThat(current.getProcessDefinitionKey()).isEqualTo(v1.getProcessDefinitionKey());
    assertThat(current.getProcessInstanceKey()).isEqualTo(v1.getProcessInstanceKey());
    assertThat(current.getFlowScopeKey()).isEqualTo(v1.getFlowScopeKey());
    assertThat(current.getBpmnElementType()).isEqualTo(v1.getBpmnElementType());
    assertThat(current.getBpmnEventType()).isEqualTo(v1.getBpmnEventType());
    assertThat(current.getParentProcessInstanceKey()).isEqualTo(v1.getParentProcessInstanceKey());
    assertThat(current.getParentElementInstanceKey()).isEqualTo(v1.getParentElementInstanceKey());
    assertThat(current.getTenantId()).isEqualTo(v1.getTenantId());
    assertThat(current.getElementInstancePath()).isEqualTo(v1.getElementInstancePath());
    assertThat(current.getProcessDefinitionPath()).isEqualTo(v1.getProcessDefinitionPath());
    assertThat(current.getCallingElementPath()).isEqualTo(v1.getCallingElementPath());
    assertThat(current.getTags()).isEqualTo(v1.getTags());
    assertThat(current.getRootProcessInstanceKey()).isEqualTo(v1.getRootProcessInstanceKey());
    assertThat(current.getStorageOrdinal()).isEqualTo(v1.getStorageOrdinal());
    assertThat(current.getBusinessId()).isEqualTo(v1.getBusinessId());
    assertThat(current.getResumeFromJobKey()).isEqualTo(v1.getResumeFromJobKey());
    assertThat(current.getResumeFromUserTaskKey()).isEqualTo(v1.getResumeFromUserTaskKey());
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with every property at its default
    final var v1 = new ProcessInstanceRecordV1();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ProcessInstanceRecord();
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
    final var current = new ProcessInstanceRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new ProcessInstanceRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getElementId()).isEqualTo(current.getElementId());
    assertThat(v1.getBpmnProcessId()).isEqualTo(current.getBpmnProcessId());
    assertThat(v1.getVersion()).isEqualTo(current.getVersion());
    assertThat(v1.getProcessDefinitionKey()).isEqualTo(current.getProcessDefinitionKey());
    assertThat(v1.getProcessInstanceKey()).isEqualTo(current.getProcessInstanceKey());
    assertThat(v1.getFlowScopeKey()).isEqualTo(current.getFlowScopeKey());
    assertThat(v1.getBpmnElementType()).isEqualTo(current.getBpmnElementType());
    assertThat(v1.getBpmnEventType()).isEqualTo(current.getBpmnEventType());
    assertThat(v1.getParentProcessInstanceKey()).isEqualTo(current.getParentProcessInstanceKey());
    assertThat(v1.getParentElementInstanceKey()).isEqualTo(current.getParentElementInstanceKey());
    assertThat(v1.getTenantId()).isEqualTo(current.getTenantId());
    assertThat(v1.getElementInstancePath()).isEqualTo(current.getElementInstancePath());
    assertThat(v1.getProcessDefinitionPath()).isEqualTo(current.getProcessDefinitionPath());
    assertThat(v1.getCallingElementPath()).isEqualTo(current.getCallingElementPath());
    assertThat(v1.getTags()).isEqualTo(current.getTags());
    assertThat(v1.getRootProcessInstanceKey()).isEqualTo(current.getRootProcessInstanceKey());
    assertThat(v1.getStorageOrdinal()).isEqualTo(current.getStorageOrdinal());
    assertThat(v1.getBusinessId()).isEqualTo(current.getBusinessId());
    assertThat(v1.getResumeFromJobKey()).isEqualTo(current.getResumeFromJobKey());
    assertThat(v1.getResumeFromUserTaskKey()).isEqualTo(current.getResumeFromUserTaskKey());
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ProcessInstanceRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has 20 entries, so the header is 0xde and a two byte count: add one to the count
    stored.putByte(2, (byte) (stored.getByte(2) + 1));

    // when read, modified and written again
    final var instance = new ProcessInstanceRecord();
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
    final var v1 = new ProcessInstanceRecordV1();
    final var current = new ProcessInstanceRecord();
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

  private static void setAllProperties(final ProcessInstanceRecordV1 record) {
    record
        .setElementId("element")
        .setBpmnProcessId("process")
        .setVersion(3)
        .setProcessDefinitionKey(1L)
        .setProcessInstanceKey(2L)
        .setFlowScopeKey(5L)
        .setBpmnElementType(BpmnElementType.SERVICE_TASK)
        .setBpmnEventType(BpmnEventType.TIMER)
        .setParentProcessInstanceKey(6L)
        .setParentElementInstanceKey(7L)
        .setTenantId("tenant")
        .setElementInstancePath(List.of(List.of(1L, 300L), List.of(), List.of(70000L)))
        .setProcessDefinitionPath(List.of(8L, 9L))
        .setCallingElementPath(List.of(0, 70000))
        .setTags(Set.of("tag"))
        .setRootProcessInstanceKey(10L)
        .setStorageOrdinal(4)
        .setBusinessId("business")
        .setResumeFromJobKey(11L)
        .setResumeFromUserTaskKey(12L);
  }

  private static void setAllProperties(final ProcessInstanceRecord record) {
    record
        .setElementId("element")
        .setBpmnProcessId("process")
        .setVersion(3)
        .setProcessDefinitionKey(1L)
        .setProcessInstanceKey(2L)
        .setFlowScopeKey(5L)
        .setBpmnElementType(BpmnElementType.SERVICE_TASK)
        .setBpmnEventType(BpmnEventType.TIMER)
        .setParentProcessInstanceKey(6L)
        .setParentElementInstanceKey(7L)
        .setTenantId("tenant")
        .setElementInstancePath(List.of(List.of(1L, 300L), List.of(), List.of(70000L)))
        .setProcessDefinitionPath(List.of(8L, 9L))
        .setCallingElementPath(List.of(0, 70000))
        .setTags(Set.of("tag"))
        .setRootProcessInstanceKey(10L)
        .setStorageOrdinal(4)
        .setBusinessId("business")
        .setResumeFromJobKey(11L)
        .setResumeFromUserTaskKey(12L);
  }

  private static void setOtherProperties(final ProcessInstanceRecord record) {
    record
        .setElementId("other")
        .setBpmnProcessId("other")
        .setVersion(13)
        .setProcessDefinitionKey(21L)
        .setProcessInstanceKey(22L)
        .setFlowScopeKey(25L)
        .setBpmnElementType(BpmnElementType.PROCESS)
        .setBpmnEventType(BpmnEventType.MESSAGE)
        .setParentProcessInstanceKey(26L)
        .setParentElementInstanceKey(27L)
        .setTenantId("other")
        .setElementInstancePath(List.of(List.of(99L)))
        .setProcessDefinitionPath(List.of(98L))
        .setCallingElementPath(List.of(97))
        .setTags(Set.of("other", "more"))
        .setRootProcessInstanceKey(30L)
        .setStorageOrdinal(14)
        .setBusinessId("other")
        .setResumeFromJobKey(31L)
        .setResumeFromUserTaskKey(32L);
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
