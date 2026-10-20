/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;

public final class PolicyUpdateAppendChunkTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Test
  public void shouldAppendChunkEvent() {
    // given
    final var envelopeId = UUID.randomUUID().toString();

    // when
    engine.writeRecords(
        RecordToWrite.command()
            .policyUpdate(PolicyUpdateIntent.APPEND_CHUNK, chunk(envelopeId, 1, 2, 0)));

    // then
    final Record<?> event =
        RecordingExporter.records()
            .withValueType(ValueType.POLICY_UPDATE)
            .withIntent(PolicyUpdateIntent.CHUNK_APPENDED)
            .getFirst();
    assertThat(envelopeOf(event)).isEqualTo(envelopeId);
  }

  @Test
  public void shouldRejectChunkThatWasAlreadyReceived() {
    // given
    final var envelopeId = UUID.randomUUID().toString();
    engine.writeRecords(
        RecordToWrite.command()
            .policyUpdate(PolicyUpdateIntent.APPEND_CHUNK, chunk(envelopeId, 1, 2, 0)));

    // when
    engine.writeRecords(
        RecordToWrite.command()
            .policyUpdate(PolicyUpdateIntent.APPEND_CHUNK, chunk(envelopeId, 1, 2, 0)));

    // then
    final var rejection =
        RecordingExporter.records()
            .onlyCommandRejections()
            .withValueType(ValueType.POLICY_UPDATE)
            .withIntent(PolicyUpdateIntent.APPEND_CHUNK)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.ALREADY_EXISTS);
  }

  @Test
  public void shouldAbortOpenAssemblyOfOlderVersion() {
    // given
    final var olderEnvelope = UUID.randomUUID().toString();
    engine.writeRecords(
        RecordToWrite.command()
            .policyUpdate(PolicyUpdateIntent.APPEND_CHUNK, chunk(olderEnvelope, 1, 2, 0)));

    // when
    final var newerEnvelope = UUID.randomUUID().toString();
    engine.writeRecords(
        RecordToWrite.command()
            .policyUpdate(PolicyUpdateIntent.APPEND_CHUNK, chunk(newerEnvelope, 2, 1, 0)));

    // then
    final Record<?> aborted =
        RecordingExporter.records()
            .withValueType(ValueType.POLICY_UPDATE)
            .withIntent(PolicyUpdateIntent.ABORTED)
            .getFirst();
    assertThat(envelopeOf(aborted)).isEqualTo(olderEnvelope);
    final Record<?> appended =
        RecordingExporter.records()
            .withValueType(ValueType.POLICY_UPDATE)
            .withIntent(PolicyUpdateIntent.CHUNK_APPENDED)
            .filter(record -> newerEnvelope.equals(envelopeOf(record)))
            .getFirst();
    assertThat(((PolicyUpdateRecordValue) appended.getValue()).getPolicyVersion()).isEqualTo(2);
  }

  @Test
  public void shouldRejectChunkOfOlderVersionThanOpenAssembly() {
    // given
    engine.writeRecords(
        RecordToWrite.command()
            .policyUpdate(
                PolicyUpdateIntent.APPEND_CHUNK, chunk(UUID.randomUUID().toString(), 2, 2, 0)));

    // when
    engine.writeRecords(
        RecordToWrite.command()
            .policyUpdate(
                PolicyUpdateIntent.APPEND_CHUNK, chunk(UUID.randomUUID().toString(), 1, 1, 0)));

    // then
    final var rejection =
        RecordingExporter.records()
            .onlyCommandRejections()
            .withValueType(ValueType.POLICY_UPDATE)
            .withIntent(PolicyUpdateIntent.APPEND_CHUNK)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
  }

  private static String envelopeOf(final Record<?> record) {
    return ((PolicyUpdateRecordValue) record.getValue()).getEnvelopeId();
  }

  private static PolicyUpdateRecord chunk(
      final String envelopeId, final long version, final int count, final int index) {
    return new PolicyUpdateRecord()
        .setEnvelopeId(envelopeId)
        .setPolicyVersion(version)
        .setChunkCount(count)
        .setChunkIndex(index);
  }
}
