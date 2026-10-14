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
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;

/**
 * Checks that chunks close to the 4 MB record limit are accepted and applied. An entity is bounded
 * at 4 MB by the change-set contract, and a chunk is bounded by the chunk budget, so both sizes
 * must fit the engine's limits end to end.
 */
public final class PolicyUpdateRecordSizeTest {

  private static final int RECORD_LIMIT_BYTES = 4 * 1024 * 1024;
  private static final int LARGE_ENTITY_BYTES = 3_500_000;
  private static final int MEDIUM_ENTITY_BYTES = 1_000_000;

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Test
  public void shouldAcceptAndApplyEntityNearTheRecordLimit() {
    // given
    final var envelopeId = UUID.randomUUID().toString();
    final var chunk =
        chunk(envelopeId, 1, 1, 0)
            .setRoles(List.<RoleRecordValue>of(role("large", LARGE_ENTITY_BYTES)));
    assertThat(chunk.getLength()).isLessThan(RECORD_LIMIT_BYTES);

    // when
    engine.writeRecords(
        RecordToWrite.command().policyUpdate(PolicyUpdateIntent.APPEND_CHUNK, chunk));

    // then
    final Record<?> created =
        RecordingExporter.records()
            .withValueType(ValueType.ROLE)
            .withIntent(RoleIntent.CREATED)
            .getFirst();
    assertThat(((RoleRecordValue) created.getValue()).getDescription()).hasSize(LARGE_ENTITY_BYTES);
  }

  @Test
  public void shouldApplyChunkNearTheBudgetInOnePage() {
    // given
    final var envelopeId = UUID.randomUUID().toString();
    final var chunk =
        chunk(envelopeId, 1, 1, 0)
            .setRoles(
                List.<RoleRecordValue>of(
                    role("first", MEDIUM_ENTITY_BYTES),
                    role("second", MEDIUM_ENTITY_BYTES),
                    role("third", MEDIUM_ENTITY_BYTES)));
    assertThat(chunk.getLength()).isLessThan(RECORD_LIMIT_BYTES);

    // when
    engine.writeRecords(
        RecordToWrite.command().policyUpdate(PolicyUpdateIntent.APPEND_CHUNK, chunk));

    // then
    assertThat(
            RecordingExporter.records()
                .withValueType(ValueType.ROLE)
                .withIntent(RoleIntent.CREATED)
                .limit(3)
                .count())
        .isEqualTo(3);
    final Record<?> applied =
        RecordingExporter.records()
            .withValueType(ValueType.POLICY_UPDATE)
            .withIntent(PolicyUpdateIntent.APPLIED)
            .filter(record -> ((PolicyUpdateRecordValue) record.getValue()).isLastPage())
            .getFirst();
    assertThat(applied.getValue()).isNotNull();
  }

  private static RoleRecord role(final String id, final int descriptionBytes) {
    return new RoleRecord()
        .setRoleId(id)
        .setName("Role " + id)
        .setDescription("x".repeat(descriptionBytes));
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
