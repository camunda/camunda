/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.uid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyStatusRecord;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.PolicyStatusIntent;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.protocol.record.value.PolicyStatusRecordValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateStatus;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

public final class PolicyStatusQueryTest {

  // a fresh engine per test, because a committed policy or an open update stays for later tests
  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldAnswerNoPolicyAndNoOpenUpdate() {
    // when
    final var status = queryStatus();

    // then
    assertThat(status.getPolicyId()).isEmpty();
    assertThat(status.getSeqNo()).isZero();
    assertThat(status.getUpdateStatus()).isEqualTo(PolicyUpdateStatus.NONE);
    assertThat(status.getOpenUpdateId()).isEmpty();
    verify(engine.getCommandResponseWriter(), timeout(5000).atLeastOnce())
        .intent(PolicyStatusIntent.QUERIED);
  }

  @Test
  public void shouldAnswerTheCommittedPolicy() {
    // given
    final var chunk = chunk(0, 1).setSeqNo(42);
    append(chunk);
    RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.VERSION_COMMITTED).getFirst();

    // when
    final var status = queryStatus();

    // then
    assertThat(status.getPolicyId()).isEqualTo(chunk.getPolicyId());
    assertThat(status.getPolicyVersion()).isEqualTo(chunk.getPolicyVersion());
    assertThat(status.getSeqNo()).isEqualTo(42);
    assertThat(status.getUpdateStatus()).isEqualTo(PolicyUpdateStatus.NONE);
  }

  @Test
  public void shouldAnswerAnUpdateThatIsStillReceivingChunks() {
    // given
    final var chunk = chunk(0, 2);
    append(chunk);

    // when
    final var status = queryStatus();

    // then
    assertThat(status.getUpdateStatus()).isEqualTo(PolicyUpdateStatus.PENDING);
    assertThat(status.getOpenUpdateId()).isEqualTo(chunk.getUpdateId());
  }

  private PolicyStatusRecordValue queryStatus() {
    engine.writeRecords(
        RecordToWrite.commandFromRequest("user-" + uid())
            .policyStatus(PolicyStatusIntent.QUERY, new PolicyStatusRecord()));
    return (PolicyStatusRecordValue)
        RecordingExporter.records()
            .withValueType(ValueType.POLICY_STATUS)
            .withIntent(PolicyStatusIntent.QUERIED)
            .getFirst()
            .getValue();
  }

  private void append(final PolicyUpdateRecord chunk) {
    engine.writeRecords(
        RecordToWrite.commandWithoutClaims().policyUpdate(PolicyUpdateIntent.APPEND_CHUNK, chunk));
  }

  private static PolicyUpdateRecord chunk(final int chunkIndex, final int chunkCount) {
    return new PolicyUpdateRecord()
        .setPolicyId("policy-" + uid())
        .setPolicyVersion(1)
        .setUpdateId("update-" + uid())
        .setChunkIndex(chunkIndex)
        .setChunkCount(chunkCount);
  }
}
