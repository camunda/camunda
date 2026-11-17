/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.apply;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.chunk;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.uid;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class PolicyUpdateCommitVersionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCommitThePolicyVersionOfTheAppliedUpdate() {
    // given
    final var chunk = chunk().setSeqNo(42);

    // when
    apply(ENGINE, chunk);

    // then
    final var committed = versionCommitted(chunk.getUpdateId()).getValue();
    assertThat(committed.getPolicyId()).isEqualTo(chunk.getPolicyId());
    assertThat(committed.getPolicyVersion()).isEqualTo(chunk.getPolicyVersion());
    assertThat(committed.getSeqNo()).isEqualTo(42);
  }

  @Test
  public void shouldCommitAfterTheIdentityCommandsOfTheUpdate() {
    // given
    final var roleId = "role-" + uid();
    final var chunk =
        chunk().setRoles(List.<RoleRecordValue>of(new RoleRecord().setRoleId(roleId)));

    // when
    apply(ENGINE, chunk);

    // then
    final var committed = versionCommitted(chunk.getUpdateId());
    final var roleCreated =
        RecordingExporter.roleRecords(RoleIntent.CREATED).withRoleId(roleId).getFirst();
    assertThat(roleCreated.getPosition()).isLessThan(committed.getPosition());
  }

  @Test
  public void shouldRejectACommitSentAsClientRequest() {
    // given
    final var update = chunk();

    // when
    commitVersion(RecordToWrite.commandFromRequest("user-" + uid()), update);

    // then
    assertThat(commitRejection(update.getUpdateId()).getRejectionType())
        .isEqualTo(RejectionType.FORBIDDEN);
  }

  @Test
  public void shouldRejectAClientRequestThatLooksLikeADistributedCommit() {
    // given a client request with the key of another partition
    final var update = chunk();

    // when
    ENGINE.writeRecords(
        RecordToWrite.commandFromRequest("user-" + uid())
            .key(Protocol.encodePartitionId(2, 1L))
            .policyUpdate(PolicyUpdateIntent.COMMIT_VERSION, update));

    // then
    assertThat(commitRejection(update.getUpdateId()).getRejectionType())
        .isEqualTo(RejectionType.FORBIDDEN);
  }

  /** Writes the commit as a new command, like the follow-up of the last applied chunk. */
  private static void commitVersion(final RecordToWrite command, final PolicyUpdateRecord update) {
    ENGINE.writeRecords(command.key(-1L).policyUpdate(PolicyUpdateIntent.COMMIT_VERSION, update));
  }

  static Record<PolicyUpdateRecordValue> versionCommitted(final String updateId) {
    return RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.VERSION_COMMITTED)
        .filter(record -> record.getValue().getUpdateId().equals(updateId))
        .getFirst();
  }

  private static Record<PolicyUpdateRecordValue> commitRejection(final String updateId) {
    return RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.COMMIT_VERSION)
        .onlyCommandRejections()
        .filter(record -> record.getValue().getUpdateId().equals(updateId))
        .getFirst();
  }
}
