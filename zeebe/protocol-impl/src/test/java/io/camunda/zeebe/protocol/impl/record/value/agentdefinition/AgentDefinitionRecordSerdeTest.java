/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agentdefinition;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link AgentDefinitionRecordV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class AgentDefinitionRecordSerdeTest {

  private static final String NAME = "a name that is also longer than forty bytes of utf8 text";
  private static final String TENANT = "tenant-with-a-name-longer-than-forty-bytes-of-utf8-text";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new AgentDefinitionRecordV1();
    final var current = new AgentDefinitionRecord();
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
    final var v1 = new AgentDefinitionRecordV1();
    final var current = new AgentDefinitionRecord();

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
    final var v1 = new AgentDefinitionRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new AgentDefinitionRecord();
    current
        .setAgentDefinitionKey(7L)
        .setAgentType(AgentDefinitionType.UNSPECIFIED)
        .setName("dirty")
        .setElementId("dirty")
        .setBpmnProcessId("dirty")
        .setProcessDefinitionKey(8L)
        .setProcessDefinitionVersion(9)
        .setProcessDefinitionVersionTag("dirty")
        .setTenantId("dirty");

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getAgentDefinitionKey()).isEqualTo(42L);
    assertThat(current.getAgentType()).isEqualTo(AgentDefinitionType.AI_AGENT_TASK);
    assertThat(current.getName()).isEqualTo(NAME);
    assertThat(current.getElementId()).isEqualTo("element");
    assertThat(current.getBpmnProcessId()).isEqualTo("process");
    assertThat(current.getProcessDefinitionKey()).isEqualTo(3L);
    assertThat(current.getProcessDefinitionVersion()).isEqualTo(4);
    assertThat(current.getProcessDefinitionVersionTag()).isEqualTo("tag");
    assertThat(current.getTenantId()).isEqualTo(TENANT);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given
    final var v1 = new AgentDefinitionRecordV1();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new AgentDefinitionRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults = new AgentDefinitionRecordV1();
    assertThat(current.getAgentDefinitionKey()).isEqualTo(defaults.getAgentDefinitionKey());
    assertThat(current.getAgentType()).isEqualTo(defaults.getAgentType());
    assertThat(current.getName()).isEqualTo(defaults.getName());
    assertThat(current.getElementId()).isEqualTo(defaults.getElementId());
    assertThat(current.getBpmnProcessId()).isEqualTo(defaults.getBpmnProcessId());
    assertThat(current.getProcessDefinitionKey()).isEqualTo(defaults.getProcessDefinitionKey());
    assertThat(current.getProcessDefinitionVersion())
        .isEqualTo(defaults.getProcessDefinitionVersion());
    assertThat(current.getProcessDefinitionVersionTag())
        .isEqualTo(defaults.getProcessDefinitionVersionTag());
    assertThat(current.getTenantId()).isEqualTo(defaults.getTenantId());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new AgentDefinitionRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new AgentDefinitionRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getAgentDefinitionKey()).isEqualTo(42L);
    assertThat(v1.getAgentType()).isEqualTo(AgentDefinitionType.AI_AGENT_TASK);
    assertThat(v1.getName()).isEqualTo(NAME);
    assertThat(v1.getTenantId()).isEqualTo(TENANT);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new AgentDefinitionRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has nine entries, plus one unknown: 0x89 becomes 0x8a
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new AgentDefinitionRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setName("modified");
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new AgentDefinitionRecordV1();
    final var current = new AgentDefinitionRecord();
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

  private static void setAllProperties(final AgentDefinitionRecordV1 record) {
    record
        .setAgentDefinitionKey(42L)
        .setAgentType(AgentDefinitionType.AI_AGENT_TASK)
        .setName(NAME)
        .setElementId("element")
        .setBpmnProcessId("process")
        .setProcessDefinitionKey(3L)
        .setProcessDefinitionVersion(4)
        .setProcessDefinitionVersionTag("tag")
        .setTenantId(TENANT);
  }

  private static void setAllProperties(final AgentDefinitionRecord record) {
    record
        .setAgentDefinitionKey(42L)
        .setAgentType(AgentDefinitionType.AI_AGENT_TASK)
        .setName(NAME)
        .setElementId("element")
        .setBpmnProcessId("process")
        .setProcessDefinitionKey(3L)
        .setProcessDefinitionVersion(4)
        .setProcessDefinitionVersionTag("tag")
        .setTenantId(TENANT);
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
