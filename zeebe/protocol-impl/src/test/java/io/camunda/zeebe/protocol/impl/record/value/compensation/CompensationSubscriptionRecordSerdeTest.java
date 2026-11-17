/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.compensation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link
 * io.camunda.zeebe.protocol.impl.record.value.compensation.legacy.CompensationSubscriptionRecord}
 * is a frozen copy of the property based implementation the generated codec replaced.
 */
final class CompensationSubscriptionRecordSerdeTest {

  // the msgpack map {"a": 1}
  private static final DirectBuffer VARIABLES =
      new UnsafeBuffer(new byte[] {(byte) 0x81, (byte) 'a', 0x01});
  private static final String TENANT = "a tenant id that is longer than forty bytes of utf8";
  private static final String ACTIVITY_ID = "a compensable activity id of more than forty bytes";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.compensation.legacy
            .CompensationSubscriptionRecord();
    final var current = new CompensationSubscriptionRecord();
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
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.compensation.legacy
            .CompensationSubscriptionRecord();
    final var current = new CompensationSubscriptionRecord();

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
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.compensation.legacy
            .CompensationSubscriptionRecord();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new CompensationSubscriptionRecord();
    current
        .setTenantId("dirty")
        .setProcessInstanceKey(7L)
        .setCompensableActivityId("dirty")
        .setThrowEventId("dirty")
        .setCompensationHandlerId("dirty")
        .setStorageOrdinal(7)
        .setVariables(new UnsafeBuffer(new byte[] {(byte) 0x80}));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getTenantId()).isEqualTo(TENANT);
    assertThat(current.getProcessInstanceKey()).isEqualTo(42L);
    assertThat(current.getCompensableActivityId()).isEqualTo(ACTIVITY_ID);
    assertThat(current.getThrowEventId()).isEqualTo("throw");
    assertThat(current.getCompensationHandlerId()).isEqualTo("handler");
    assertThat(current.getStorageOrdinal()).isEqualTo(300);
    assertThat(current.getVariablesBuffer()).isEqualTo(VARIABLES);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with every property at its default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.compensation.legacy
            .CompensationSubscriptionRecord();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new CompensationSubscriptionRecord();
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
    final var current = new CompensationSubscriptionRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.compensation.legacy
            .CompensationSubscriptionRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getTenantId()).isEqualTo(TENANT);
    assertThat(v1.getProcessInstanceKey()).isEqualTo(42L);
    assertThat(v1.getCompensableActivityId()).isEqualTo(ACTIVITY_ID);
    assertThat(v1.getThrowEventId()).isEqualTo("throw");
    assertThat(v1.getCompensationHandlerId()).isEqualTo("handler");
    assertThat(v1.getStorageOrdinal()).isEqualTo(300);
    assertThat(v1.getVariablesBuffer()).isEqualTo(VARIABLES);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new CompensationSubscriptionRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has twelve entries, plus one unknown: 0x8c becomes 0x8d
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new CompensationSubscriptionRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setProcessDefinitionKey(9L);
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
        new io.camunda.zeebe.protocol.impl.record.value.compensation.legacy
            .CompensationSubscriptionRecord();
    final var current = new CompensationSubscriptionRecord();
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
      final io.camunda.zeebe.protocol.impl.record.value.compensation.legacy
              .CompensationSubscriptionRecord
          record) {
    record
        .setTenantId(TENANT)
        .setProcessInstanceKey(42L)
        .setProcessDefinitionKey(43L)
        .setCompensableActivityId(ACTIVITY_ID)
        .setThrowEventId("throw")
        .setThrowEventInstanceKey(44L)
        .setCompensationHandlerId("handler")
        .setCompensationHandlerInstanceKey(45L)
        .setCompensableActivityScopeKey(46L)
        .setCompensableActivityInstanceKey(47L)
        .setStorageOrdinal(300)
        .setVariables(VARIABLES);
  }

  private static void setAllProperties(final CompensationSubscriptionRecord record) {
    record
        .setTenantId(TENANT)
        .setProcessInstanceKey(42L)
        .setProcessDefinitionKey(43L)
        .setCompensableActivityId(ACTIVITY_ID)
        .setThrowEventId("throw")
        .setThrowEventInstanceKey(44L)
        .setCompensationHandlerId("handler")
        .setCompensationHandlerInstanceKey(45L)
        .setCompensableActivityScopeKey(46L)
        .setCompensableActivityInstanceKey(47L)
        .setStorageOrdinal(300)
        .setVariables(VARIABLES);
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
