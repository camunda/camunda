/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.appliedRecord;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.apply;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.uid;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

/**
 * After a restart, the engine rebuilds the buffer of policy update chunks from the last snapshot
 * and the events written after it. Each test restarts the engine, so each gets its own.
 */
public final class PolicyUpdateRecoveryTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldApplyAChunkThatWasBufferedBeforeARestart() {
    // given
    final var updateId = "update-" + uid();
    final var roleId = "role-" + uid();
    engine.snapshot();
    apply(engine, chunk(updateId, 0).setRoles(List.<RoleRecordValue>of(role(roleId))));
    RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.CHUNK_APPENDED)
        .filter(record -> record.getValue().getUpdateId().equals(updateId))
        .await();

    // when
    engine.stop();
    engine.start();
    apply(engine, chunk(updateId, 1));

    // then
    assertThat(appliedRecord(updateId).getValue().getChunkIndex()).isZero();
    assertThat(RecordingExporter.roleRecords(RoleIntent.CREATED).withRoleId(roleId).exists())
        .isTrue();
  }

  private static PolicyUpdateRecord chunk(final String updateId, final int chunkIndex) {
    return new PolicyUpdateRecord()
        .setPolicyId("policy")
        .setPolicyVersion(2)
        .setBasePolicyVersion(1)
        .setSeqNo(7)
        .setUpdateId(updateId)
        .setChunkIndex(chunkIndex)
        .setChunkCount(2);
  }

  private static RoleRecord role(final String roleId) {
    return new RoleRecord().setRoleId(roleId).setName(roleId);
  }
}
