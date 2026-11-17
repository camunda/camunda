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
import io.camunda.zeebe.protocol.impl.record.value.LegacyObjectValues;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link
 * io.camunda.zeebe.protocol.impl.record.value.agentinstance.legacy.AgentInstanceMetrics} is a
 * frozen copy of the property based implementation the generated codec replaced.
 */
final class AgentInstanceMetricsSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.agentinstance.legacy.AgentInstanceMetrics();
    final var current = new AgentInstanceMetrics();
    setAllProperties(v1);
    setAllProperties(current);

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
    // given only defaults, every property has one
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.agentinstance.legacy.AgentInstanceMetrics();
    final var current = new AgentInstanceMetrics();

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
        new io.camunda.zeebe.protocol.impl.record.value.agentinstance.legacy.AgentInstanceMetrics();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = LegacyObjectValues.write(v1, bytes);
    final var current = new AgentInstanceMetrics();
    current
        .setInputTokens(9L)
        .setOutputTokens(9L)
        .setReasoningTokenCount(9L)
        .setCacheCreationTokenCount(9L)
        .setCacheReadTokenCount(9L)
        .setModelCalls(9)
        .setToolCalls(9);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getInputTokens()).isEqualTo(1L);
    assertThat(current.getOutputTokens()).isEqualTo(200L);
    assertThat(current.getReasoningTokenCount()).isEqualTo(70000L);
    assertThat(current.getCacheCreationTokenCount()).isEqualTo(5_000_000_000L);
    assertThat(current.getCacheReadTokenCount()).isEqualTo(5L);
    assertThat(current.getModelCalls()).isEqualTo(300);
    assertThat(current.getToolCalls()).isEqualTo(70000);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given
    final var defaults =
        new io.camunda.zeebe.protocol.impl.record.value.agentinstance.legacy.AgentInstanceMetrics();
    final var bytes = new ExpandableArrayBuffer();
    final int length = LegacyObjectValues.write(defaults, bytes);
    final var current = new AgentInstanceMetrics();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getInputTokens()).isEqualTo(defaults.getInputTokens());
    assertThat(current.getOutputTokens()).isEqualTo(defaults.getOutputTokens());
    assertThat(current.getReasoningTokenCount()).isEqualTo(defaults.getReasoningTokenCount());
    assertThat(current.getCacheCreationTokenCount())
        .isEqualTo(defaults.getCacheCreationTokenCount());
    assertThat(current.getCacheReadTokenCount()).isEqualTo(defaults.getCacheReadTokenCount());
    assertThat(current.getModelCalls()).isEqualTo(defaults.getModelCalls());
    assertThat(current.getToolCalls()).isEqualTo(defaults.getToolCalls());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new AgentInstanceMetrics();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.agentinstance.legacy.AgentInstanceMetrics();
    LegacyObjectValues.read(v1, bytes, length);

    // then
    assertThat(v1.getInputTokens()).isEqualTo(1L);
    assertThat(v1.getOutputTokens()).isEqualTo(200L);
    assertThat(v1.getReasoningTokenCount()).isEqualTo(70000L);
    assertThat(v1.getCacheCreationTokenCount()).isEqualTo(5_000_000_000L);
    assertThat(v1.getCacheReadTokenCount()).isEqualTo(5L);
    assertThat(v1.getModelCalls()).isEqualTo(300);
    assertThat(v1.getToolCalls()).isEqualTo(70000);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new AgentInstanceMetrics();
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new AgentInstanceMetrics();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setInputTokens(99L);
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
        new io.camunda.zeebe.protocol.impl.record.value.agentinstance.legacy.AgentInstanceMetrics();
    final var current = new AgentInstanceMetrics();
    setAllProperties(v1);
    setAllProperties(current);

    // when the parent record serializes the nested value through Jackson
    final var mapper = new ObjectMapper();
    final var v1Json = mapper.valueToTree(v1);
    final var currentJson = mapper.valueToTree(current);

    // then exporters see the same document
    assertThat(currentJson).isEqualTo(v1Json);
  }

  private static void setAllProperties(
      final io.camunda.zeebe.protocol.impl.record.value.agentinstance.legacy.AgentInstanceMetrics
          target) {
    target
        .setInputTokens(1L)
        .setOutputTokens(200L)
        .setReasoningTokenCount(70000L)
        .setCacheCreationTokenCount(5_000_000_000L)
        .setCacheReadTokenCount(5L)
        .setModelCalls(300)
        .setToolCalls(70000);
  }

  private static void setAllProperties(final AgentInstanceMetrics target) {
    target
        .setInputTokens(1L)
        .setOutputTokens(200L)
        .setReasoningTokenCount(70000L)
        .setCacheCreationTokenCount(5_000_000_000L)
        .setCacheReadTokenCount(5L)
        .setModelCalls(300)
        .setToolCalls(70000);
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
