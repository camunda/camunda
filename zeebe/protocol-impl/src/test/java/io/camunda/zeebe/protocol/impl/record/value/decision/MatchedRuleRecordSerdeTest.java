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
 * break. {@link MatchedRuleRecordV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class MatchedRuleRecordSerdeTest {

  private static final String RULE_ID = "a rule id that is longer than forty bytes of utf8 text";
  private static final DirectBuffer VALUE = utf8("\"an output value that is longer than forty\"");

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new MatchedRuleRecordV1();
    final var current = new MatchedRuleRecord();
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
    // given only the properties without a default are set, the list stays empty
    final var v1 = new MatchedRuleRecordV1();
    final var current = new MatchedRuleRecord();
    v1.setRuleId(RULE_ID).setRuleIndex(3);
    current.setRuleId(RULE_ID).setRuleIndex(3);

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
    final var v1 = new MatchedRuleRecordV1();
    final var current = new MatchedRuleRecord();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new MatchedRuleRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new MatchedRuleRecord();
    current.setRuleId("dirty").setRuleIndex(7);
    addOutput(current.evaluatedOutputs(), "dirty");

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getRuleId()).isEqualTo(RULE_ID);
    assertThat(current.getRuleIndex()).isEqualTo(3);
    assertThat(current.getEvaluatedOutputs()).hasSize(2);
    assertThat(current.getEvaluatedOutputs().get(1).getOutputId()).isEqualTo("second");
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with the list empty
    final var v1 = new MatchedRuleRecordV1();
    v1.setRuleId(RULE_ID).setRuleIndex(3);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new MatchedRuleRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then re-encoding gives the bytes of the defaults, so no dirtied property survived
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(bytes, length));
    assertThat(current.evaluatedOutputs().size()).isZero();
  }

  @Test
  void shouldFailToReadWithoutTheKeyLikeV1() {
    // given an empty map
    final var bytes = new ExpandableArrayBuffer(1);
    bytes.putByte(0, (byte) 0x80);

    // when
    final var v1Failure = catchThrowable(() -> new MatchedRuleRecordV1().wrap(bytes, 0, 1));
    final var failure = catchThrowable(() -> new MatchedRuleRecord().wrap(bytes, 0, 1));

    // then the same failure as V1
    assertThat(failure.getCause()).isExactlyInstanceOf(v1Failure.getCause().getClass());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new MatchedRuleRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new MatchedRuleRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getRuleId()).isEqualTo(RULE_ID);
    assertThat(v1.getRuleIndex()).isEqualTo(3);
    assertThat(v1.getEvaluatedOutputs()).hasSize(2);
    assertThat(v1.getEvaluatedOutputs().get(1).getOutputId()).isEqualTo("second");
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new MatchedRuleRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has three entries, plus one unknown: 0x83 becomes 0x84
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new MatchedRuleRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setRuleIndex(9);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new MatchedRuleRecordV1();
    final var current = new MatchedRuleRecord();
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

  private static void setAllProperties(final MatchedRuleRecordV1 record) {
    record.setRuleId(RULE_ID).setRuleIndex(3);
    addV1Output(record.evaluatedOutputs(), "first");
    addV1Output(record.evaluatedOutputs(), "second");
  }

  private static void setAllProperties(final MatchedRuleRecord record) {
    record.setRuleId(RULE_ID).setRuleIndex(3);
    addOutput(record.evaluatedOutputs(), "first");
    addOutput(record.evaluatedOutputs(), "second");
  }

  private static void addV1Output(
      final ValueArray<EvaluatedOutputRecordV1> outputs, final String id) {
    outputs.add().setOutputId(id).setOutputName("name").setOutputValue(VALUE);
  }

  private static void addOutput(final ValueArray<EvaluatedOutputRecord> outputs, final String id) {
    outputs.add().setOutputId(id).setOutputName("name").setOutputValue(VALUE);
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
