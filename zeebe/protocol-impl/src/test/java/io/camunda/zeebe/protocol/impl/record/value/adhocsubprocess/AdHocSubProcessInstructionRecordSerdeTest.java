/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link AdHocSubProcessInstructionRecordV1} is a frozen copy of the property based
 * implementation the generated codec replaced.
 */
final class AdHocSubProcessInstructionRecordSerdeTest {

  private static final String TENANT_ID = "a tenant id that is longer than forty bytes of utf8";
  private static final byte[] VARIABLES = MsgPackConverter.convertToMsgPack(Map.of("a", 1));

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new AdHocSubProcessInstructionRecordV1();
    final var current = new AdHocSubProcessInstructionRecord();
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
    final var v1 = new AdHocSubProcessInstructionRecordV1();
    final var current = new AdHocSubProcessInstructionRecord();

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldFailToWriteAnElementWithoutIdLikeV1() {
    // given an element that has no element id
    final var v1 = new AdHocSubProcessInstructionRecordV1();
    v1.activateElements().add();
    final var current = new AdHocSubProcessInstructionRecord();
    current.activateElements().add();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new AdHocSubProcessInstructionRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new AdHocSubProcessInstructionRecord();
    setAllProperties(current);
    current
        .setAdHocSubProcessInstanceKey(7L)
        .setTenantId("dirty")
        .setCancelRemainingInstances(false)
        .setCompletionConditionFulfilled(false)
        .setStorageOrdinal(7);
    current.activateElements().add().setElementId("dirty");

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getAdHocSubProcessInstanceKey()).isEqualTo(42L);
    assertThat(current.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(current.isCancelRemainingInstances()).isTrue();
    assertThat(current.isCompletionConditionFulfilled()).isTrue();
    assertThat(current.getStorageOrdinal()).isEqualTo(3);
    assertThat(current.getActivateElements()).hasSize(2);
    assertThat(current.getActivateElements().get(1).getElementId()).isEqualTo("second");
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with defaults everywhere
    final var bytes = new ExpandableArrayBuffer();
    final int length = new AdHocSubProcessInstructionRecordV1().write(bytes, 0);
    final var current = new AdHocSubProcessInstructionRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getAdHocSubProcessInstanceKey()).isEqualTo(-1L);
    assertThat(current.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(current.isCancelRemainingInstances()).isFalse();
    assertThat(current.isCompletionConditionFulfilled()).isFalse();
    assertThat(current.getStorageOrdinal()).isEqualTo(0);
    assertThat(current.getActivateElements()).isEmpty();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new AdHocSubProcessInstructionRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new AdHocSubProcessInstructionRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getAdHocSubProcessInstanceKey()).isEqualTo(42L);
    assertThat(v1.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(v1.isCancelRemainingInstances()).isTrue();
    assertThat(v1.getActivateElements()).hasSize(2);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new AdHocSubProcessInstructionRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has six entries, plus one unknown: 0x86 becomes 0x87
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new AdHocSubProcessInstructionRecord();
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
    final var v1 = new AdHocSubProcessInstructionRecordV1();
    final var current = new AdHocSubProcessInstructionRecord();
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

  private static void setAllProperties(final AdHocSubProcessInstructionRecordV1 record) {
    record
        .setAdHocSubProcessInstanceKey(42L)
        .setTenantId(TENANT_ID)
        .setCancelRemainingInstances(true)
        .setCompletionConditionFulfilled(true)
        .setStorageOrdinal(3);
    record.activateElements().add().setElementId("first").setVariables(new UnsafeBuffer(VARIABLES));
    record.activateElements().add().setElementId("second");
  }

  private static void setAllProperties(final AdHocSubProcessInstructionRecord record) {
    record
        .setAdHocSubProcessInstanceKey(42L)
        .setTenantId(TENANT_ID)
        .setCancelRemainingInstances(true)
        .setCompletionConditionFulfilled(true)
        .setStorageOrdinal(3);
    record.activateElements().add().setElementId("first").setVariables(new UnsafeBuffer(VARIABLES));
    record.activateElements().add().setElementId("second");
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
