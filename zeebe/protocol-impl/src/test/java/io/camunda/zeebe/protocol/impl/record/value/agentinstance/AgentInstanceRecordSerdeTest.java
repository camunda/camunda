/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agentinstance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryMessageContent;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecordV1;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link AgentInstanceRecordV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class AgentInstanceRecordSerdeTest {

  private static final String TENANT = "tenant-with-a-name-longer-than-forty-bytes-of-utf8-text";
  private static final String ELEMENT_ID = "an element id that is longer than forty bytes of utf8";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new AgentInstanceRecordV1();
    final var current = new AgentInstanceRecord();
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
    // given only defaults, every property has one
    final var v1 = new AgentInstanceRecordV1();
    final var current = new AgentInstanceRecord();

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
    final var v1 = new AgentInstanceRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new AgentInstanceRecord();
    current
        .setAgentInstanceKey(1L)
        .addElementInstanceKey(2L)
        .setElementId("dirty")
        .setTenantId("dirty")
        .setStatus(AgentInstanceStatus.UNSPECIFIED)
        .addChangedAttribute("dirty")
        .addHistoryItem(new AgentHistoryRecord().setJobKey(3L));
    current.getDefinition().setModel("dirty");

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getAgentInstanceKey()).isEqualTo(42L);
    assertThat(current.getElementInstanceKeys()).containsExactly(300L, 70_000L);
    assertThat(current.getElementId()).isEqualTo(ELEMENT_ID);
    assertThat(current.getTenantId()).isEqualTo(TENANT);
    assertThat(current.getStatus()).isEqualTo(AgentInstanceStatus.INITIALIZING);
    assertThat(current.getChangedAttributes()).containsExactly("attribute");
    assertThat(current.getDefinition().getModel()).isEqualTo("model");
    assertThat(current.getHistory()).hasSize(1);
    assertThat(current.getHistory().getFirst().getJobKey()).isEqualTo(8L);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given
    final var v1 = new AgentInstanceRecordV1();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new AgentInstanceRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults = new AgentInstanceRecordV1();
    assertThat(current.getAgentInstanceKey()).isEqualTo(defaults.getAgentInstanceKey());
    assertThat(current.getElementInstanceKeys()).isEmpty();
    assertThat(current.getTenantId()).isEqualTo(defaults.getTenantId());
    assertThat(current.getStatus()).isEqualTo(defaults.getStatus());
    assertThat(current.getChangedAttributes()).isEmpty();
    assertThat(current.getHistory()).isEmpty();
    assertThat(current.getDefinition().getModel()).isEqualTo(defaults.getDefinition().getModel());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new AgentInstanceRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new AgentInstanceRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getAgentInstanceKey()).isEqualTo(42L);
    assertThat(v1.getElementInstanceKeys()).containsExactly(300L, 70_000L);
    assertThat(v1.getElementId()).isEqualTo(ELEMENT_ID);
    assertThat(v1.getTenantId()).isEqualTo(TENANT);
    assertThat(v1.getStatus()).isEqualTo(AgentInstanceStatus.INITIALIZING);
    assertThat(v1.getChangedAttributes()).containsExactly("attribute");
    assertThat(v1.getDefinition().getModel()).isEqualTo("model");
    assertThat(v1.getHistory()).hasSize(1);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new AgentInstanceRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has 22 entries, written as 0xde 0x00 0x16, plus one unknown: 0x16 becomes 0x17
    stored.putByte(2, (byte) (stored.getByte(2) + 1));

    // when read, modified and written again
    final var instance = new AgentInstanceRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setJobKey(100L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new AgentInstanceRecordV1();
    final var current = new AgentInstanceRecord();
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

  private static void setAllProperties(final AgentInstanceRecordV1 record) {
    record
        .setAgentInstanceKey(42L)
        .setAgentDefinitionKey(2L)
        .setElementInstanceKey(3L)
        .addElementInstanceKey(300L)
        .addElementInstanceKey(70_000L)
        .setElementId(ELEMENT_ID)
        .setProcessInstanceKey(4L)
        .setRootProcessInstanceKey(5L)
        .setStorageOrdinal(6)
        .setBpmnProcessId("a bpmn process id that is longer than forty bytes of utf8 text")
        .setProcessDefinitionKey(7L)
        .setProcessDefinitionVersion(9)
        .setProcessDefinitionVersionTag("tag")
        .setTenantId(TENANT)
        .setStatus(AgentInstanceStatus.INITIALIZING)
        .addChangedAttribute("attribute")
        .setTools(List.of(new AgentInstanceTool().setName("tool")))
        .setJobKey(8L)
        .setJobLeaseToken("lease token")
        .addHistoryItem(new AgentHistoryRecordV1().setJobKey(8L));
    record.getDefinition().setModel("model").setProvider("provider");
    record.getDefinition().addSystemPrompt(new AgentHistoryMessageContent().setText("system"));
    record.getLimits().setMaxTokens(13L);
    record.getMetrics().setInputTokens(11L).setModelCalls(2);
  }

  private static void setAllProperties(final AgentInstanceRecord record) {
    record
        .setAgentInstanceKey(42L)
        .setAgentDefinitionKey(2L)
        .setElementInstanceKey(3L)
        .addElementInstanceKey(300L)
        .addElementInstanceKey(70_000L)
        .setElementId(ELEMENT_ID)
        .setProcessInstanceKey(4L)
        .setRootProcessInstanceKey(5L)
        .setStorageOrdinal(6)
        .setBpmnProcessId("a bpmn process id that is longer than forty bytes of utf8 text")
        .setProcessDefinitionKey(7L)
        .setProcessDefinitionVersion(9)
        .setProcessDefinitionVersionTag("tag")
        .setTenantId(TENANT)
        .setStatus(AgentInstanceStatus.INITIALIZING)
        .addChangedAttribute("attribute")
        .setTools(List.of(new AgentInstanceTool().setName("tool")))
        .setJobKey(8L)
        .setJobLeaseToken("lease token")
        .addHistoryItem(new AgentHistoryRecord().setJobKey(8L));
    record.getDefinition().setModel("model").setProvider("provider");
    record.getDefinition().addSystemPrompt(new AgentHistoryMessageContent().setText("system"));
    record.getLimits().setMaxTokens(13L);
    record.getMetrics().setInputTokens(11L).setModelCalls(2);
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
