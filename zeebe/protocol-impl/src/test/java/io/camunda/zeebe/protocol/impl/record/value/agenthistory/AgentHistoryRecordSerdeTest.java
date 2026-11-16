/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agenthistory;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceTool;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link AgentHistoryRecordV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class AgentHistoryRecordSerdeTest {

  private static final String TENANT = "tenant-with-a-name-longer-than-forty-bytes-of-utf8-text";
  private static final String TEXT = "a text that is longer than forty bytes of utf8 characters";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new AgentHistoryRecordV1();
    final var current = new AgentHistoryRecord();
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
    final var v1 = new AgentHistoryRecordV1();
    final var current = new AgentHistoryRecord();

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
    final var v1 = new AgentHistoryRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new AgentHistoryRecord();
    current
        .setAgentHistoryKey(1L)
        .setTenantId("dirty")
        .setRole(AgentHistoryRole.USER)
        .setDuplicate(false)
        .addContent(new AgentHistoryMessageContent().setText("dirty"))
        .addChangedAttribute("dirty")
        .setModel("dirty");

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getAgentHistoryKey()).isEqualTo(42L);
    assertThat(current.getTenantId()).isEqualTo(TENANT);
    assertThat(current.getRole()).isEqualTo(AgentHistoryRole.ASSISTANT);
    assertThat(current.isDuplicate()).isTrue();
    assertThat(current.getContent()).hasSize(1);
    assertThat(current.getChangedAttributes()).containsExactly("attribute");
    assertThat(current.getModel()).isEqualTo("model");
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given
    final var v1 = new AgentHistoryRecordV1();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new AgentHistoryRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults = new AgentHistoryRecordV1();
    assertThat(current.getAgentHistoryKey()).isEqualTo(defaults.getAgentHistoryKey());
    assertThat(current.getTenantId()).isEqualTo(defaults.getTenantId());
    assertThat(current.getRole()).isEqualTo(defaults.getRole());
    assertThat(current.getContent()).isEmpty();
    assertThat(current.getChangedAttributes()).isEmpty();
    assertThat(current.isDuplicate()).isFalse();
    assertThat(current.getModel()).isEqualTo(defaults.getModel());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new AgentHistoryRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new AgentHistoryRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getAgentHistoryKey()).isEqualTo(42L);
    assertThat(v1.getTenantId()).isEqualTo(TENANT);
    assertThat(v1.getRole()).isEqualTo(AgentHistoryRole.ASSISTANT);
    assertThat(v1.isDuplicate()).isTrue();
    assertThat(v1.getContent()).hasSize(1);
    assertThat(v1.getChangedAttributes()).containsExactly("attribute");
    assertThat(v1.getModel()).isEqualTo("model");
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new AgentHistoryRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has 25 entries, written as 0xde 0x00 0x19, plus one unknown: 0x19 becomes 0x1a
    stored.putByte(2, (byte) (stored.getByte(2) + 1));

    // when read, modified and written again
    final var instance = new AgentHistoryRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setModel("modified");
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new AgentHistoryRecordV1();
    final var current = new AgentHistoryRecord();
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

  private static void setAllProperties(final AgentHistoryRecordV1 record) {
    record
        .setAgentHistoryKey(42L)
        .setAgentInstanceKey(2L)
        .setElementInstanceKey(3L)
        .setProcessInstanceKey(4L)
        .setRootProcessInstanceKey(5L)
        .setStorageOrdinal(6)
        .setBpmnProcessId("a bpmn process id that is longer than forty bytes of utf8 text")
        .setProcessDefinitionKey(7L)
        .setTenantId(TENANT)
        .setJobKey(8L)
        .setJobLeaseToken("lease token")
        .setLoopIteration(9)
        .setRole(AgentHistoryRole.ASSISTANT)
        .setProducedAt(10L)
        .addContent(new AgentHistoryMessageContent().setText(TEXT))
        .addSystemPrompt(new AgentHistoryMessageContent().setText("system"))
        .addToolCall(new AgentHistoryEmbeddedToolCall().setToolCallId("call").setToolName("tool"))
        .setHistoryItemId("history item")
        .setTools(List.of(new AgentInstanceTool().setName("tool")))
        .setModel("model")
        .setProvider("provider")
        .addChangedAttribute("attribute")
        .setDuplicate(true);
    record.getMetrics().setInputTokens(11L).setOutputTokens(12L);
    record.getLimits().setMaxTokens(13L).setMaxModelCalls(14).setMaxToolCalls(15);
  }

  private static void setAllProperties(final AgentHistoryRecord record) {
    record
        .setAgentHistoryKey(42L)
        .setAgentInstanceKey(2L)
        .setElementInstanceKey(3L)
        .setProcessInstanceKey(4L)
        .setRootProcessInstanceKey(5L)
        .setStorageOrdinal(6)
        .setBpmnProcessId("a bpmn process id that is longer than forty bytes of utf8 text")
        .setProcessDefinitionKey(7L)
        .setTenantId(TENANT)
        .setJobKey(8L)
        .setJobLeaseToken("lease token")
        .setLoopIteration(9)
        .setRole(AgentHistoryRole.ASSISTANT)
        .setProducedAt(10L)
        .addContent(new AgentHistoryMessageContent().setText(TEXT))
        .addSystemPrompt(new AgentHistoryMessageContent().setText("system"))
        .addToolCall(new AgentHistoryEmbeddedToolCall().setToolCallId("call").setToolName("tool"))
        .setHistoryItemId("history item")
        .setTools(List.of(new AgentInstanceTool().setName("tool")))
        .setModel("model")
        .setProvider("provider")
        .addChangedAttribute("attribute")
        .setDuplicate(true);
    record.getMetrics().setInputTokens(11L).setOutputTokens(12L);
    record.getLimits().setMaxTokens(13L).setMaxModelCalls(14).setMaxToolCalls(15);
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
