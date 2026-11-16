/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.signal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link SignalSubscriptionRecordV1} is a frozen copy of the property based implementation
 * the generated codec replaced.
 */
final class SignalSubscriptionRecordSerdeTest {

  private static final String LONG_0 = "a signal name is longer than forty bytes";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new SignalSubscriptionRecordV1();
    final var current = new SignalSubscriptionRecord();
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
    final var v1 = new SignalSubscriptionRecordV1();
    final var current = new SignalSubscriptionRecord();

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
    final var v1 = new SignalSubscriptionRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new SignalSubscriptionRecord();

    // when
    current.wrap(bytes, 0, length);

    // then
    assertAllProperties(current);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with defaults everywhere
    final var v1 = new SignalSubscriptionRecordV1();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new SignalSubscriptionRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults = new SignalSubscriptionRecordV1();
    assertDefaultsOf(defaults, current);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new SignalSubscriptionRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new SignalSubscriptionRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertAllProperties(v1);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new SignalSubscriptionRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has 10 entries, plus one unknown: 0x8a becomes 0x8b
    stored.putByte(0, (byte) (stored.getByte(0) + 1));
    // when read, modified and written again
    final var instance = new SignalSubscriptionRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setStorageOrdinal(99);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new SignalSubscriptionRecordV1();
    final var current = new SignalSubscriptionRecord();
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

  private static void setAllProperties(final SignalSubscriptionRecordV1 record) {
    record
        .setProcessDefinitionKey(11L)
        .setSignalName(buf(LONG_0))
        .setCatchEventId(buf("catch"))
        .setBpmnProcessId(buf("process"))
        .setCatchEventInstanceKey(12L)
        .setTenantId("tenant")
        .setProcessInstanceKey(13L)
        .setRootProcessInstanceKey(14L)
        .setStorageOrdinal(3)
        .setBpmnElementType(BpmnElementType.SERVICE_TASK);
  }

  private static void setAllProperties(final SignalSubscriptionRecord record) {
    record
        .setProcessDefinitionKey(11L)
        .setSignalName(buf(LONG_0))
        .setCatchEventId(buf("catch"))
        .setBpmnProcessId(buf("process"))
        .setCatchEventInstanceKey(12L)
        .setTenantId("tenant")
        .setProcessInstanceKey(13L)
        .setRootProcessInstanceKey(14L)
        .setStorageOrdinal(3)
        .setBpmnElementType(BpmnElementType.SERVICE_TASK);
  }

  private static void assertAllProperties(final SignalSubscriptionRecordV1 record) {
    assertThat(record.getProcessDefinitionKey()).isEqualTo(11L);
    assertThat(record.getSignalName()).isEqualTo(LONG_0);
    assertThat(record.getCatchEventId()).isEqualTo("catch");
    assertThat(record.getBpmnProcessId()).isEqualTo("process");
    assertThat(record.getCatchEventInstanceKey()).isEqualTo(12L);
    assertThat(record.getTenantId()).isEqualTo("tenant");
    assertThat(record.getProcessInstanceKey()).isEqualTo(13L);
    assertThat(record.getRootProcessInstanceKey()).isEqualTo(14L);
    assertThat(record.getStorageOrdinal()).isEqualTo(3);
    assertThat(record.getBpmnElementType()).isEqualTo(BpmnElementType.SERVICE_TASK);
  }

  private static void assertAllProperties(final SignalSubscriptionRecord record) {
    assertThat(record.getProcessDefinitionKey()).isEqualTo(11L);
    assertThat(record.getSignalName()).isEqualTo(LONG_0);
    assertThat(record.getCatchEventId()).isEqualTo("catch");
    assertThat(record.getBpmnProcessId()).isEqualTo("process");
    assertThat(record.getCatchEventInstanceKey()).isEqualTo(12L);
    assertThat(record.getTenantId()).isEqualTo("tenant");
    assertThat(record.getProcessInstanceKey()).isEqualTo(13L);
    assertThat(record.getRootProcessInstanceKey()).isEqualTo(14L);
    assertThat(record.getStorageOrdinal()).isEqualTo(3);
    assertThat(record.getBpmnElementType()).isEqualTo(BpmnElementType.SERVICE_TASK);
  }

  private static void assertDefaultsOf(
      final SignalSubscriptionRecordV1 defaults, final SignalSubscriptionRecord record) {
    assertThat(record.getProcessDefinitionKey()).isEqualTo(defaults.getProcessDefinitionKey());
    assertThat(record.getSignalName()).isEqualTo(defaults.getSignalName());
    assertThat(record.getCatchEventId()).isEqualTo(defaults.getCatchEventId());
    assertThat(record.getBpmnProcessId()).isEqualTo(defaults.getBpmnProcessId());
    assertThat(record.getCatchEventInstanceKey()).isEqualTo(defaults.getCatchEventInstanceKey());
    assertThat(record.getTenantId()).isEqualTo(defaults.getTenantId());
    assertThat(record.getProcessInstanceKey()).isEqualTo(defaults.getProcessInstanceKey());
    assertThat(record.getRootProcessInstanceKey()).isEqualTo(defaults.getRootProcessInstanceKey());
    assertThat(record.getStorageOrdinal()).isEqualTo(defaults.getStorageOrdinal());
    assertThat(record.getBpmnElementType()).isEqualTo(defaults.getBpmnElementType());
  }

  private static DirectBuffer buf(final String s) {
    return utf8(s);
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
