/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.conditional;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link ConditionalSubscriptionRecordV1} is a frozen copy of the property based
 * implementation the generated codec replaced.
 */
final class ConditionalSubscriptionRecordSerdeTest {

  private static final String BPMN_PROCESS_ID = "a bpmn process id that is longer than forty bytes";
  private static final String TENANT = "a tenant id that is longer than forty bytes of utf8";
  private static final List<String> VARIABLE_NAMES =
      List.of("amount", "a variable name that is longer than forty bytes of utf8");
  private static final List<String> VARIABLE_EVENTS = List.of("create");

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new ConditionalSubscriptionRecordV1();
    final var current = new ConditionalSubscriptionRecord();
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
    // given
    final var v1 = new ConditionalSubscriptionRecordV1();
    final var current = new ConditionalSubscriptionRecord();

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
    final var v1 = new ConditionalSubscriptionRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ConditionalSubscriptionRecord();
    current
        .setScopeKey(7L)
        .setInterrupting(true)
        .setTenantId("dirty")
        .setVariableNames(List.of("dirty"))
        .setVariableEvents(List.of("dirty", "dirty"))
        .setElementType(BpmnElementType.END_EVENT);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getScopeKey()).isEqualTo(1L);
    assertThat(current.isInterrupting()).isFalse();
    assertThat(current.getTenantId()).isEqualTo(TENANT);
    assertThat(current.getVariableNames()).isEqualTo(VARIABLE_NAMES);
    assertThat(current.getVariableEvents()).isEqualTo(VARIABLE_EVENTS);
    assertThat(current.getElementType()).isEqualTo(BpmnElementType.SERVICE_TASK);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with every property at its default
    final var v1 = new ConditionalSubscriptionRecordV1();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ConditionalSubscriptionRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then re-encoding gives the bytes of the defaults, so no dirtied property survived
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(bytes, length));
    assertThat(current.getVariableNames()).isEmpty();
    assertThat(current.isInterrupting()).isTrue();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new ConditionalSubscriptionRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new ConditionalSubscriptionRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getScopeKey()).isEqualTo(1L);
    assertThat(v1.getBpmnProcessId()).isEqualTo(BPMN_PROCESS_ID);
    assertThat(v1.getTenantId()).isEqualTo(TENANT);
    assertThat(v1.getVariableNames()).isEqualTo(VARIABLE_NAMES);
    assertThat(v1.getVariableEvents()).isEqualTo(VARIABLE_EVENTS);
    assertThat(v1.getElementType()).isEqualTo(BpmnElementType.SERVICE_TASK);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ConditionalSubscriptionRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has fourteen entries, plus one unknown: 0x8e becomes 0x8f
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new ConditionalSubscriptionRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setStorageOrdinal(9);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new ConditionalSubscriptionRecordV1();
    final var current = new ConditionalSubscriptionRecord();
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

  private static void setAllProperties(final ConditionalSubscriptionRecordV1 record) {
    record
        .setScopeKey(1L)
        .setProcessInstanceKey(2L)
        .setElementInstanceKey(3L)
        .setProcessDefinitionKey(4L)
        .setBpmnProcessId(utf8(BPMN_PROCESS_ID))
        .setCatchEventId(utf8("catch"))
        .setInterrupting(false)
        .setCondition(utf8("= amount > 10"))
        .setVariableNames(VARIABLE_NAMES)
        .setVariableEvents(VARIABLE_EVENTS)
        .setTenantId(TENANT)
        .setRootProcessInstanceKey(5L)
        .setStorageOrdinal(300)
        .setElementType(BpmnElementType.SERVICE_TASK);
  }

  private static void setAllProperties(final ConditionalSubscriptionRecord record) {
    record
        .setScopeKey(1L)
        .setProcessInstanceKey(2L)
        .setElementInstanceKey(3L)
        .setProcessDefinitionKey(4L)
        .setBpmnProcessId(utf8(BPMN_PROCESS_ID))
        .setCatchEventId(utf8("catch"))
        .setInterrupting(false)
        .setCondition(utf8("= amount > 10"))
        .setVariableNames(VARIABLE_NAMES)
        .setVariableEvents(VARIABLE_EVENTS)
        .setTenantId(TENANT)
        .setRootProcessInstanceKey(5L)
        .setStorageOrdinal(300)
        .setElementType(BpmnElementType.SERVICE_TASK);
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
