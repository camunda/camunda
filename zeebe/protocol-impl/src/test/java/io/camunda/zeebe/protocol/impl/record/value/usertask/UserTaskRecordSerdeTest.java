/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.usertask;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskRecord} is a
 * frozen copy of the property based implementation the generated codec replaced.
 */
final class UserTaskRecordSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskRecord();
    final var current = new UserTaskRecord();
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
    // given every property has a default, nothing is set
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskRecord();
    final var current = new UserTaskRecord();

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
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskRecord();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new UserTaskRecord();

    // when
    current.wrap(bytes, 0, length);

    // then
    assertAllProperties(current);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with defaults everywhere
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskRecord();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new UserTaskRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults =
        new io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskRecord();
    assertDefaultsOf(defaults, current);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new UserTaskRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertAllProperties(v1);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new UserTaskRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has 27 entries (header 0xde and a 2 byte count), plus one unknown
    stored.putByte(2, (byte) (stored.getByte(2) + 1));
    // when read, modified and written again
    final var instance = new UserTaskRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setAction("modified");
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskRecord();
    final var current = new UserTaskRecord();
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
      final io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskRecord record) {
    record
        .setUserTaskKey(1L)
        .setAssignee("an assignee is longer than forty bytes")
        .setCandidateGroupsList(names(20))
        .setCandidateUsersList(List.of("user1", "user2"))
        .setDueDate("2024-01-01T00:00:00Z")
        .setFollowUpDate("2024-02-01T00:00:00Z")
        .setFormKey(2L)
        .setExternalFormReference("form reference")
        .setVariables(doc())
        .setCustomHeaders(headers())
        .setBpmnProcessId("process")
        .setProcessDefinitionVersion(3)
        .setProcessDefinitionKey(4L)
        .setProcessInstanceKey(5L)
        .setElementId("element")
        .setElementInstanceKey(6L)
        .setTenantId("tenant")
        .setChangedAttributes(List.of("assignee", "dueDate"))
        .setAction("complete")
        .setCreationTimestamp(7L)
        .setPriority(80)
        .setDeniedReason("denied")
        .setTags(Set.of("tag"))
        .setListenersConfigKey(8L)
        .setRootProcessInstanceKey(9L)
        .setStorageOrdinal(10)
        .setBusinessId("business");
  }

  private static void setAllProperties(final UserTaskRecord record) {
    record
        .setUserTaskKey(1L)
        .setAssignee("an assignee is longer than forty bytes")
        .setCandidateGroupsList(names(20))
        .setCandidateUsersList(List.of("user1", "user2"))
        .setDueDate("2024-01-01T00:00:00Z")
        .setFollowUpDate("2024-02-01T00:00:00Z")
        .setFormKey(2L)
        .setExternalFormReference("form reference")
        .setVariables(doc())
        .setCustomHeaders(headers())
        .setBpmnProcessId("process")
        .setProcessDefinitionVersion(3)
        .setProcessDefinitionKey(4L)
        .setProcessInstanceKey(5L)
        .setElementId("element")
        .setElementInstanceKey(6L)
        .setTenantId("tenant")
        .setChangedAttributes(List.of("assignee", "dueDate"))
        .setAction("complete")
        .setCreationTimestamp(7L)
        .setPriority(80)
        .setDeniedReason("denied")
        .setTags(Set.of("tag"))
        .setListenersConfigKey(8L)
        .setRootProcessInstanceKey(9L)
        .setStorageOrdinal(10)
        .setBusinessId("business");
  }

  private static void assertAllProperties(
      final io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskRecord record) {
    assertThat(record.getUserTaskKey()).isEqualTo(1L);
    assertThat(record.getAssignee()).isEqualTo("an assignee is longer than forty bytes");
    assertThat(record.getCandidateGroupsList()).isEqualTo(names(20));
    assertThat(record.getCandidateUsersList()).isEqualTo(List.of("user1", "user2"));
    assertThat(record.getDueDate()).isEqualTo("2024-01-01T00:00:00Z");
    assertThat(record.getFollowUpDate()).isEqualTo("2024-02-01T00:00:00Z");
    assertThat(record.getFormKey()).isEqualTo(2L);
    assertThat(record.getExternalFormReference()).isEqualTo("form reference");
    assertThat(record.getVariables()).isEqualTo(Map.of("a", 1));
    assertThat(record.getCustomHeaders()).isEqualTo(Map.of("k", "v"));
    assertThat(record.getBpmnProcessId()).isEqualTo("process");
    assertThat(record.getProcessDefinitionVersion()).isEqualTo(3);
    assertThat(record.getProcessDefinitionKey()).isEqualTo(4L);
    assertThat(record.getProcessInstanceKey()).isEqualTo(5L);
    assertThat(record.getElementId()).isEqualTo("element");
    assertThat(record.getElementInstanceKey()).isEqualTo(6L);
    assertThat(record.getTenantId()).isEqualTo("tenant");
    assertThat(record.getChangedAttributes()).isEqualTo(List.of("assignee", "dueDate"));
    assertThat(record.getAction()).isEqualTo("complete");
    assertThat(record.getCreationTimestamp()).isEqualTo(7L);
    assertThat(record.getPriority()).isEqualTo(80);
    assertThat(record.getDeniedReason()).isEqualTo("denied");
    assertThat(record.getTags()).isEqualTo(Set.of("tag"));
    assertThat(record.getListenersConfigKey()).isEqualTo(8L);
    assertThat(record.getRootProcessInstanceKey()).isEqualTo(9L);
    assertThat(record.getStorageOrdinal()).isEqualTo(10);
    assertThat(record.getBusinessId()).isEqualTo("business");
  }

  private static void assertAllProperties(final UserTaskRecord record) {
    assertThat(record.getUserTaskKey()).isEqualTo(1L);
    assertThat(record.getAssignee()).isEqualTo("an assignee is longer than forty bytes");
    assertThat(record.getCandidateGroupsList()).isEqualTo(names(20));
    assertThat(record.getCandidateUsersList()).isEqualTo(List.of("user1", "user2"));
    assertThat(record.getDueDate()).isEqualTo("2024-01-01T00:00:00Z");
    assertThat(record.getFollowUpDate()).isEqualTo("2024-02-01T00:00:00Z");
    assertThat(record.getFormKey()).isEqualTo(2L);
    assertThat(record.getExternalFormReference()).isEqualTo("form reference");
    assertThat(record.getVariables()).isEqualTo(Map.of("a", 1));
    assertThat(record.getCustomHeaders()).isEqualTo(Map.of("k", "v"));
    assertThat(record.getBpmnProcessId()).isEqualTo("process");
    assertThat(record.getProcessDefinitionVersion()).isEqualTo(3);
    assertThat(record.getProcessDefinitionKey()).isEqualTo(4L);
    assertThat(record.getProcessInstanceKey()).isEqualTo(5L);
    assertThat(record.getElementId()).isEqualTo("element");
    assertThat(record.getElementInstanceKey()).isEqualTo(6L);
    assertThat(record.getTenantId()).isEqualTo("tenant");
    assertThat(record.getChangedAttributes()).isEqualTo(List.of("assignee", "dueDate"));
    assertThat(record.getAction()).isEqualTo("complete");
    assertThat(record.getCreationTimestamp()).isEqualTo(7L);
    assertThat(record.getPriority()).isEqualTo(80);
    assertThat(record.getDeniedReason()).isEqualTo("denied");
    assertThat(record.getTags()).isEqualTo(Set.of("tag"));
    assertThat(record.getListenersConfigKey()).isEqualTo(8L);
    assertThat(record.getRootProcessInstanceKey()).isEqualTo(9L);
    assertThat(record.getStorageOrdinal()).isEqualTo(10);
    assertThat(record.getBusinessId()).isEqualTo("business");
  }

  private static void assertDefaultsOf(
      final io.camunda.zeebe.protocol.impl.record.value.usertask.legacy.UserTaskRecord defaults,
      final UserTaskRecord record) {
    assertThat(record.getUserTaskKey()).isEqualTo(defaults.getUserTaskKey());
    assertThat(record.getAssignee()).isEqualTo(defaults.getAssignee());
    assertThat(record.getCandidateGroupsList()).isEqualTo(defaults.getCandidateGroupsList());
    assertThat(record.getCandidateUsersList()).isEqualTo(defaults.getCandidateUsersList());
    assertThat(record.getDueDate()).isEqualTo(defaults.getDueDate());
    assertThat(record.getFollowUpDate()).isEqualTo(defaults.getFollowUpDate());
    assertThat(record.getFormKey()).isEqualTo(defaults.getFormKey());
    assertThat(record.getExternalFormReference()).isEqualTo(defaults.getExternalFormReference());
    assertThat(record.getVariables()).isEqualTo(defaults.getVariables());
    assertThat(record.getCustomHeaders()).isEqualTo(defaults.getCustomHeaders());
    assertThat(record.getBpmnProcessId()).isEqualTo(defaults.getBpmnProcessId());
    assertThat(record.getProcessDefinitionVersion())
        .isEqualTo(defaults.getProcessDefinitionVersion());
    assertThat(record.getProcessDefinitionKey()).isEqualTo(defaults.getProcessDefinitionKey());
    assertThat(record.getProcessInstanceKey()).isEqualTo(defaults.getProcessInstanceKey());
    assertThat(record.getElementId()).isEqualTo(defaults.getElementId());
    assertThat(record.getElementInstanceKey()).isEqualTo(defaults.getElementInstanceKey());
    assertThat(record.getTenantId()).isEqualTo(defaults.getTenantId());
    assertThat(record.getChangedAttributes()).isEqualTo(defaults.getChangedAttributes());
    assertThat(record.getAction()).isEqualTo(defaults.getAction());
    assertThat(record.getCreationTimestamp()).isEqualTo(defaults.getCreationTimestamp());
    assertThat(record.getPriority()).isEqualTo(defaults.getPriority());
    assertThat(record.getDeniedReason()).isEqualTo(defaults.getDeniedReason());
    assertThat(record.getTags()).isEqualTo(defaults.getTags());
    assertThat(record.getListenersConfigKey()).isEqualTo(defaults.getListenersConfigKey());
    assertThat(record.getRootProcessInstanceKey()).isEqualTo(defaults.getRootProcessInstanceKey());
    assertThat(record.getStorageOrdinal()).isEqualTo(defaults.getStorageOrdinal());
    assertThat(record.getBusinessId()).isEqualTo(defaults.getBusinessId());
  }

  private static DirectBuffer doc() {
    return new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{\"a\":1}"));
  }

  private static DirectBuffer headers() {
    return new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{\"k\":\"v\"}"));
  }

  private static List<String> names(final int count) {
    return IntStream.range(0, count).mapToObj(i -> "name" + i).toList();
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
