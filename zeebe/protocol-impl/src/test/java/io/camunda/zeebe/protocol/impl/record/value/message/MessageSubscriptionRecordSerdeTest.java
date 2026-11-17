/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
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
 * break. {@link
 * io.camunda.zeebe.protocol.impl.record.value.message.legacy.MessageSubscriptionRecord} is a frozen
 * copy of the property based implementation the generated codec replaced.
 */
final class MessageSubscriptionRecordSerdeTest {

  // the msgpack map {"a": 1}
  private static final DirectBuffer VARIABLES =
      new UnsafeBuffer(new byte[] {(byte) 0x81, (byte) 'a', 0x01});
  private static final String TENANT = "a tenant id that is longer than forty bytes of utf8";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.message.legacy.MessageSubscriptionRecord();
    final var current = new MessageSubscriptionRecord();
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
        new io.camunda.zeebe.protocol.impl.record.value.message.legacy.MessageSubscriptionRecord();
    final var current = new MessageSubscriptionRecord();
    v1.setProcessInstanceKey(1L);
    v1.setElementInstanceKey(2L);
    current.setProcessInstanceKey(1L);
    current.setElementInstanceKey(2L);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldFailToWriteWhileUnsetLikeV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.message.legacy.MessageSubscriptionRecord();
    final var current = new MessageSubscriptionRecord();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.message.legacy.MessageSubscriptionRecord();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new MessageSubscriptionRecord();
    current.setProcessInstanceKey(1L);
    current.setElementInstanceKey(2L);
    current.setProcessDefinitionKey(3L);
    current.setMessageKey(4L);
    current.setMessageName(utf8("dirty"));
    current.setCorrelationKey(utf8("dirty"));
    current.setInterrupting(true);
    current.setBpmnProcessId(utf8("dirty"));
    current.setVariables(new UnsafeBuffer(new byte[] {(byte) 0x80}));
    current.setTenantId("dirty");
    current.setBusinessId("dirty");
    current.setElementId("dirty");
    current.setRootProcessInstanceKey(9L);
    current.setStorageOrdinal(10);
    current.setElementType(BpmnElementType.START_EVENT);
    current.setSubscriptionKey(11L);

    // when
    current.wrap(bytes, 0, length);

    // then every value was restored and no dirtied property survived
    assertThat(current.getProcessInstanceKey()).isEqualTo(70000L);
    assertThat(current.getElementInstanceKey()).isEqualTo(300L);
    assertThat(current.getProcessDefinitionKey()).isEqualTo(5L);
    assertThat(current.getMessageName()).isEqualTo("message");
    assertThat(current.getCorrelationKey()).isEqualTo("key");
    assertThat(current.isInterrupting()).isFalse();
    assertThat(current.getBpmnProcessId()).isEqualTo("process");
    assertThat(current.getVariablesBuffer()).isEqualTo(VARIABLES);
    assertThat(current.getTenantId()).isEqualTo(TENANT);
    assertThat(current.getBusinessId()).isEqualTo("business");
    assertThat(current.getElementId()).isEqualTo("element");
    assertThat(current.getStorageOrdinal()).isEqualTo(300);
    assertThat(current.getElementType()).isEqualTo(BpmnElementType.SERVICE_TASK);
    assertThat(current.getSubscriptionKey()).isEqualTo(8L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = current.write(rewritten, 0);
    assertThat(hex(rewritten, rewrittenLength)).isEqualTo(hex(bytes, length));
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with every property at its default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.message.legacy.MessageSubscriptionRecord();
    v1.setProcessInstanceKey(1L);
    v1.setElementInstanceKey(2L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new MessageSubscriptionRecord();
    setAllProperties(current);

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
    final var current = new MessageSubscriptionRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.message.legacy.MessageSubscriptionRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getProcessInstanceKey()).isEqualTo(70000L);
    assertThat(v1.getElementInstanceKey()).isEqualTo(300L);
    assertThat(v1.getProcessDefinitionKey()).isEqualTo(5L);
    assertThat(v1.getMessageName()).isEqualTo("message");
    assertThat(v1.getCorrelationKey()).isEqualTo("key");
    assertThat(v1.isInterrupting()).isFalse();
    assertThat(v1.getBpmnProcessId()).isEqualTo("process");
    assertThat(v1.getVariablesBuffer()).isEqualTo(VARIABLES);
    assertThat(v1.getTenantId()).isEqualTo(TENANT);
    assertThat(v1.getBusinessId()).isEqualTo("business");
    assertThat(v1.getElementId()).isEqualTo("element");
    assertThat(v1.getStorageOrdinal()).isEqualTo(300);
    assertThat(v1.getElementType()).isEqualTo(BpmnElementType.SERVICE_TASK);
    assertThat(v1.getSubscriptionKey()).isEqualTo(8L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = v1.write(rewritten, 0);
    assertThat(hex(rewritten, rewrittenLength)).isEqualTo(hex(bytes, length));
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new MessageSubscriptionRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has 16 entries, which needs a 0xde header with a 2 byte count: increment the count
    stored.putByte(2, (byte) (stored.getByte(2) + 1));

    // when read, modified and written again
    final var instance = new MessageSubscriptionRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setMessageKey(9L);
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
        new io.camunda.zeebe.protocol.impl.record.value.message.legacy.MessageSubscriptionRecord();
    final var current = new MessageSubscriptionRecord();
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
      final io.camunda.zeebe.protocol.impl.record.value.message.legacy.MessageSubscriptionRecord
          record) {
    record.setProcessInstanceKey(70000L);
    record.setElementInstanceKey(300L);
    record.setProcessDefinitionKey(5L);
    record.setMessageKey(6L);
    record.setMessageName(utf8("message"));
    record.setCorrelationKey(utf8("key"));
    record.setInterrupting(false);
    record.setBpmnProcessId(utf8("process"));
    record.setVariables(VARIABLES);
    record.setTenantId(TENANT);
    record.setBusinessId("business");
    record.setElementId("element");
    record.setRootProcessInstanceKey(7L);
    record.setStorageOrdinal(300);
    record.setElementType(BpmnElementType.SERVICE_TASK);
    record.setSubscriptionKey(8L);
  }

  private static void setAllProperties(final MessageSubscriptionRecord record) {
    record.setProcessInstanceKey(70000L);
    record.setElementInstanceKey(300L);
    record.setProcessDefinitionKey(5L);
    record.setMessageKey(6L);
    record.setMessageName(utf8("message"));
    record.setCorrelationKey(utf8("key"));
    record.setInterrupting(false);
    record.setBpmnProcessId(utf8("process"));
    record.setVariables(VARIABLES);
    record.setTenantId(TENANT);
    record.setBusinessId("business");
    record.setElementId("element");
    record.setRootProcessInstanceKey(7L);
    record.setStorageOrdinal(300);
    record.setElementType(BpmnElementType.SERVICE_TASK);
    record.setSubscriptionKey(8L);
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
