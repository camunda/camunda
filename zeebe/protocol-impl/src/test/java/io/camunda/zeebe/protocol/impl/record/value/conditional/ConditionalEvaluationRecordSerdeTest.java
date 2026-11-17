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
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link
 * io.camunda.zeebe.protocol.impl.record.value.conditional.legacy.ConditionalEvaluationRecord} is a
 * frozen copy of the property based implementation the generated codec replaced.
 */
final class ConditionalEvaluationRecordSerdeTest {

  // the msgpack map {"a": 1}
  private static final DirectBuffer VARIABLES =
      new UnsafeBuffer(new byte[] {(byte) 0x81, (byte) 'a', 0x01});
  private static final String TENANT = "a tenant id that is longer than forty bytes of utf8";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.conditional.legacy
            .ConditionalEvaluationRecord();
    final var current = new ConditionalEvaluationRecord();
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
        new io.camunda.zeebe.protocol.impl.record.value.conditional.legacy
            .ConditionalEvaluationRecord();
    final var current = new ConditionalEvaluationRecord();

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
        new io.camunda.zeebe.protocol.impl.record.value.conditional.legacy
            .ConditionalEvaluationRecord();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ConditionalEvaluationRecord();
    current
        .setProcessDefinitionKey(7L)
        .setVariables(new UnsafeBuffer(new byte[] {(byte) 0x80}))
        .setTenantId("dirty")
        .addStartedProcessInstance(1L, 2L, 3);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getProcessDefinitionKey()).isEqualTo(42L);
    assertThat(current.getVariablesBuffer()).isEqualTo(VARIABLES);
    assertThat(current.getTenantId()).isEqualTo(TENANT);
    assertThat(current.getStartedProcessInstances()).hasSize(2);
    assertThat(current.getStartedProcessInstances().get(1).getStorageOrdinal()).isEqualTo(300);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with every property at its default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.conditional.legacy
            .ConditionalEvaluationRecord();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ConditionalEvaluationRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then re-encoding gives the bytes of the defaults, so no dirtied property survived
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(bytes, length));
    assertThat(current.getStartedProcessInstances()).isEmpty();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new ConditionalEvaluationRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.conditional.legacy
            .ConditionalEvaluationRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getProcessDefinitionKey()).isEqualTo(42L);
    assertThat(v1.getVariablesBuffer()).isEqualTo(VARIABLES);
    assertThat(v1.getTenantId()).isEqualTo(TENANT);
    assertThat(v1.getStartedProcessInstances()).hasSize(2);
    assertThat(v1.getStartedProcessInstances().get(1).getStorageOrdinal()).isEqualTo(300);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ConditionalEvaluationRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has four entries, plus one unknown: 0x84 becomes 0x85
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new ConditionalEvaluationRecord();
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
        new io.camunda.zeebe.protocol.impl.record.value.conditional.legacy
            .ConditionalEvaluationRecord();
    final var current = new ConditionalEvaluationRecord();
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

  @Test
  void shouldCopyTheValuesOfAnotherRecord() throws Exception {
    // given
    final var source = new ConditionalEvaluationRecord();
    setAllProperties(source);
    final var expectedJson = source.toJson();
    final var target = new ConditionalEvaluationRecord();
    target.addStartedProcessInstance(1L, 2L, 3);

    // when the target is filled from the source and the source changes afterwards
    target.wrap(source);
    source.addStartedProcessInstance(5L, 6L, 7);

    // then the target holds copies of the source values, and its previous instances are gone
    final var mapper = new ObjectMapper();
    assertThat(mapper.readTree(target.toJson())).isEqualTo(mapper.readTree(expectedJson));
  }

  private static void setAllProperties(
      final io.camunda.zeebe.protocol.impl.record.value.conditional.legacy
              .ConditionalEvaluationRecord
          record) {
    record
        .setProcessDefinitionKey(42L)
        .setVariables(VARIABLES)
        .setTenantId(TENANT)
        .addStartedProcessInstance(7L, 8L, 9)
        .addStartedProcessInstance(10L, 11L, 300);
  }

  private static void setAllProperties(final ConditionalEvaluationRecord record) {
    record
        .setProcessDefinitionKey(42L)
        .setVariables(VARIABLES)
        .setTenantId(TENANT)
        .addStartedProcessInstance(7L, 8L, 9)
        .addStartedProcessInstance(10L, 11L, 300);
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
