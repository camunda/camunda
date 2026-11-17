/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * break. {@link
 * io.camunda.zeebe.protocol.impl.record.value.decision.legacy.DecisionEvaluationRecord} is a frozen
 * copy of the property based implementation the generated codec replaced.
 */
final class DecisionEvaluationRecordSerdeTest {

  private static final String TENANT = "a tenant id that is longer than forty bytes of utf8";
  private static final String FAILURE = "an evaluation failure message of more than forty bytes";
  // the msgpack map {"a": 1}
  private static final DirectBuffer VARIABLES =
      new UnsafeBuffer(new byte[] {(byte) 0x81, (byte) 'a', 0x01});
  private static final DirectBuffer OUTPUT = utf8("\"an output value that is longer than forty\"");

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.decision.legacy.DecisionEvaluationRecord();
    final var current = new DecisionEvaluationRecord();
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
    // given every property at its default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.decision.legacy.DecisionEvaluationRecord();
    final var current = new DecisionEvaluationRecord();

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
        new io.camunda.zeebe.protocol.impl.record.value.decision.legacy.DecisionEvaluationRecord();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new DecisionEvaluationRecord();
    current
        .setDecisionKey(7L)
        .setDecisionId("dirty")
        .setDecisionVersion(7)
        .setDecisionOutput(utf8("dirty"))
        .setVariables(new UnsafeBuffer(new byte[] {(byte) 0x80}))
        .setTenantId("dirty")
        .setStorageOrdinal(7);
    addDecision(current.evaluatedDecisions(), "dirty");

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getDecisionKey()).isEqualTo(1L);
    assertThat(current.getDecisionId()).isEqualTo("decision");
    assertThat(current.getDecisionVersion()).isEqualTo(2);
    assertThat(current.getDecisionOutputBuffer()).isEqualTo(OUTPUT);
    assertThat(current.getVariablesBuffer()).isEqualTo(VARIABLES);
    assertThat(current.getTenantId()).isEqualTo(TENANT);
    assertThat(current.getStorageOrdinal()).isEqualTo(300);
    assertThat(current.getEvaluatedDecisions()).hasSize(2);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with every property at its default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.decision.legacy.DecisionEvaluationRecord();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new DecisionEvaluationRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then re-encoding gives the bytes of the defaults, so no dirtied property survived
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(bytes, length));
    assertThat(current.evaluatedDecisions().size()).isZero();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new DecisionEvaluationRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.decision.legacy.DecisionEvaluationRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getDecisionKey()).isEqualTo(1L);
    assertThat(v1.getDecisionId()).isEqualTo("decision");
    assertThat(v1.getDecisionOutputBuffer()).isEqualTo(OUTPUT);
    assertThat(v1.getVariablesBuffer()).isEqualTo(VARIABLES);
    assertThat(v1.getTenantId()).isEqualTo(TENANT);
    assertThat(v1.getStorageOrdinal()).isEqualTo(300);
    assertThat(v1.getEvaluatedDecisions()).hasSize(2);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new DecisionEvaluationRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has twenty entries, so its header is 0xde and a two byte count: 20 becomes 21
    stored.putByte(2, (byte) (stored.getByte(2) + 1));

    // when read, modified and written again
    final var instance = new DecisionEvaluationRecord();
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
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.decision.legacy.DecisionEvaluationRecord();
    final var current = new DecisionEvaluationRecord();
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
      final io.camunda.zeebe.protocol.impl.record.value.decision.legacy.DecisionEvaluationRecord
          record) {
    record
        .setDecisionKey(1L)
        .setDecisionId("decision")
        .setDecisionName("name")
        .setDecisionVersion(2)
        .setDecisionRequirementsId("requirements")
        .setDecisionRequirementsKey(3L)
        .setDecisionOutput(OUTPUT)
        .setVariables(VARIABLES)
        .setBpmnProcessId("process")
        .setProcessDefinitionKey(4L)
        .setProcessInstanceKey(5L)
        .setElementId("element")
        .setElementInstanceKey(6L)
        .setEvaluationFailureMessage(FAILURE)
        .setFailedDecisionId("failed")
        .setTenantId(TENANT)
        .setRootProcessInstanceKey(7L)
        .setStorageOrdinal(300)
        .setBusinessId("business");
    addV1Decision(record.evaluatedDecisions(), "first");
    addV1Decision(record.evaluatedDecisions(), "second");
  }

  private static void setAllProperties(final DecisionEvaluationRecord record) {
    record
        .setDecisionKey(1L)
        .setDecisionId("decision")
        .setDecisionName("name")
        .setDecisionVersion(2)
        .setDecisionRequirementsId("requirements")
        .setDecisionRequirementsKey(3L)
        .setDecisionOutput(OUTPUT)
        .setVariables(VARIABLES)
        .setBpmnProcessId("process")
        .setProcessDefinitionKey(4L)
        .setProcessInstanceKey(5L)
        .setElementId("element")
        .setElementInstanceKey(6L)
        .setEvaluationFailureMessage(FAILURE)
        .setFailedDecisionId("failed")
        .setTenantId(TENANT)
        .setRootProcessInstanceKey(7L)
        .setStorageOrdinal(300)
        .setBusinessId("business");
    addDecision(record.evaluatedDecisions(), "first");
    addDecision(record.evaluatedDecisions(), "second");
  }

  private static void addV1Decision(
      final ValueArray<
              io.camunda.zeebe.protocol.impl.record.value.decision.legacy.EvaluatedDecisionRecord>
          decisions,
      final String id) {
    decisions
        .add()
        .setDecisionId(id)
        .setDecisionName("name")
        .setDecisionKey(8L)
        .setDecisionVersion(1)
        .setDecisionType("decisionTable")
        .setDecisionOutput(OUTPUT);
  }

  private static void addDecision(
      final ValueArray<EvaluatedDecisionRecord> decisions, final String id) {
    decisions
        .add()
        .setDecisionId(id)
        .setDecisionName("name")
        .setDecisionKey(8L)
        .setDecisionVersion(1)
        .setDecisionType("decisionTable")
        .setDecisionOutput(OUTPUT);
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
