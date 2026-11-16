/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.msgpack.value.ValueArray;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link EvaluatedDecisionRecordV1} is a frozen copy of the property based implementation
 * the generated codec replaced.
 */
final class EvaluatedDecisionRecordSerdeTest {

  private static final String ID = "a decision id that is longer than forty bytes of utf8 text";
  private static final String NAME = "a decision name that is longer than forty bytes of utf8";
  private static final String TYPE = "decisionTable";
  private static final String INSTANCE_KEY = "an evaluation instance key of more than forty bytes";
  private static final String TENANT = "a tenant id that is longer than forty bytes of utf8";
  private static final DirectBuffer OUTPUT = utf8("\"an output value that is longer than forty\"");

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new EvaluatedDecisionRecordV1();
    final var current = new EvaluatedDecisionRecord();
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
    // given only the properties without a default are set, the rest stays default
    final var v1 = new EvaluatedDecisionRecordV1();
    final var current = new EvaluatedDecisionRecord();
    setRequiredProperties(v1);
    setRequiredProperties(current);

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
    final var v1 = new EvaluatedDecisionRecordV1();
    final var current = new EvaluatedDecisionRecord();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new EvaluatedDecisionRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new EvaluatedDecisionRecord();
    current
        .setDecisionId("dirty")
        .setDecisionName("dirty")
        .setDecisionKey(7L)
        .setDecisionVersion(7)
        .setDecisionType("dirty")
        .setDecisionOutput(utf8("dirty"))
        .setTenantId("dirty");
    addInput(current.evaluatedInputs(), "dirty");

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getDecisionId()).isEqualTo(ID);
    assertThat(current.getDecisionName()).isEqualTo(NAME);
    assertThat(current.getDecisionKey()).isEqualTo(42L);
    assertThat(current.getDecisionVersion()).isEqualTo(3);
    assertThat(current.getDecisionType()).isEqualTo(TYPE);
    assertThat(current.getDecisionOutputBuffer()).isEqualTo(OUTPUT);
    assertThat(current.getTenantId()).isEqualTo(TENANT);
    assertThat(current.getEvaluatedInputs()).hasSize(2);
    assertThat(current.getMatchedRules()).hasSize(1);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with the lists empty and the other properties at their defaults
    final var v1 = new EvaluatedDecisionRecordV1();
    setRequiredProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new EvaluatedDecisionRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then re-encoding gives the bytes of the defaults, so no dirtied property survived
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(bytes, length));
    assertThat(current.evaluatedInputs().size()).isZero();
    assertThat(current.matchedRules().size()).isZero();
  }

  @Test
  void shouldFailToReadWithoutTheKeyLikeV1() {
    // given an empty map
    final var bytes = new ExpandableArrayBuffer(1);
    bytes.putByte(0, (byte) 0x80);

    // when
    final var v1Failure = catchThrowable(() -> new EvaluatedDecisionRecordV1().wrap(bytes, 0, 1));
    final var failure = catchThrowable(() -> new EvaluatedDecisionRecord().wrap(bytes, 0, 1));

    // then the same failure as V1
    assertThat(failure.getCause()).isExactlyInstanceOf(v1Failure.getCause().getClass());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new EvaluatedDecisionRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new EvaluatedDecisionRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getDecisionId()).isEqualTo(ID);
    assertThat(v1.getDecisionKey()).isEqualTo(42L);
    assertThat(v1.getDecisionOutputBuffer()).isEqualTo(OUTPUT);
    assertThat(v1.getEvaluatedInputs()).hasSize(2);
    assertThat(v1.getMatchedRules()).hasSize(1);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new EvaluatedDecisionRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has ten entries, plus one unknown: 0x8a becomes 0x8b
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new EvaluatedDecisionRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setDecisionVersion(9);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new EvaluatedDecisionRecordV1();
    final var current = new EvaluatedDecisionRecord();
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

  private static void setRequiredProperties(final EvaluatedDecisionRecordV1 record) {
    record
        .setDecisionId(ID)
        .setDecisionName(NAME)
        .setDecisionKey(42L)
        .setDecisionVersion(3)
        .setDecisionType(TYPE)
        .setDecisionOutput(OUTPUT);
  }

  private static void setRequiredProperties(final EvaluatedDecisionRecord record) {
    record
        .setDecisionId(ID)
        .setDecisionName(NAME)
        .setDecisionKey(42L)
        .setDecisionVersion(3)
        .setDecisionType(TYPE)
        .setDecisionOutput(OUTPUT);
  }

  private static void setAllProperties(final EvaluatedDecisionRecordV1 record) {
    setRequiredProperties(record);
    record.setDecisionEvaluationInstanceKey(INSTANCE_KEY).setTenantId(TENANT);
    addV1Input(record.evaluatedInputs(), "first");
    addV1Input(record.evaluatedInputs(), "second");
    record.matchedRules().add().setRuleId("rule").setRuleIndex(1);
  }

  private static void setAllProperties(final EvaluatedDecisionRecord record) {
    setRequiredProperties(record);
    record.setDecisionEvaluationInstanceKey(INSTANCE_KEY).setTenantId(TENANT);
    addInput(record.evaluatedInputs(), "first");
    addInput(record.evaluatedInputs(), "second");
    record.matchedRules().add().setRuleId("rule").setRuleIndex(1);
  }

  private static void addInput(final ValueArray<EvaluatedInputRecord> inputs, final String id) {
    inputs.add().setInputId(id).setInputName("name").setInputValue(OUTPUT);
  }

  private static void addV1Input(final ValueArray<EvaluatedInputRecordV1> inputs, final String id) {
    inputs.add().setInputId(id).setInputName("name").setInputValue(OUTPUT);
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
