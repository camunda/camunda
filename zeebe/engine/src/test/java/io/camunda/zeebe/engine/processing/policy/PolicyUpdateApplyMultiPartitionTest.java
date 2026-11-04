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
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.chunk;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.uid;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

public final class PolicyUpdateApplyMultiPartitionTest {

  private static final int PARTITION_COUNT = 3;

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldApplyTheUpdateOnEveryPartition() {
    // given
    final var roleId = "role-" + uid();
    final var chunk =
        chunk()
            .setRoles(List.<RoleRecordValue>of(new RoleRecord().setRoleId(roleId).setName(roleId)));

    // when
    apply(engine, chunk);

    // then
    appliedRecord(chunk.getUpdateId());
    for (int partitionId = 1; partitionId <= PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.roleRecords(RoleIntent.CREATED)
                  .withRoleId(roleId)
                  .withPartitionId(partitionId)
                  .exists())
          .describedAs("role created on partition %d", partitionId)
          .isTrue();
    }
  }

  @Test
  public void shouldRejectAChunkOnAnotherPartition() {
    // given
    final var chunk = chunk();

    // when
    engine.writeRecordsOnPartition(
        2,
        RecordToWrite.commandWithoutClaims().policyUpdate(PolicyUpdateIntent.APPLY_CHUNK, chunk));

    // then
    final var rejection =
        RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.APPLY_CHUNK)
            .onlyCommandRejections()
            .withPartitionId(2)
            .filter(record -> record.getValue().getUpdateId().equals(chunk.getUpdateId()))
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("partition 1");
  }

  @Test
  public void shouldSwitchEveryPartitionAfterItAppliedTheUpdate() {
    // given
    final var roleId = "role-" + uid();
    final var chunk =
        chunk().setRoles(List.<RoleRecordValue>of(new RoleRecord().setRoleId(roleId)));

    // when
    apply(engine, chunk);

    // then
    for (int partitionId = 1; partitionId <= PARTITION_COUNT; partitionId++) {
      final var roleCreated =
          RecordingExporter.roleRecords(RoleIntent.CREATED)
              .withRoleId(roleId)
              .withPartitionId(partitionId)
              .getFirst();
      final var switched =
          RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.VERSION_SWITCHED)
              .withPartitionId(partitionId)
              .filter(record -> record.getValue().getUpdateId().equals(chunk.getUpdateId()))
              .getFirst();
      assertThat(switched.getValue().getPolicyVersion()).isEqualTo(chunk.getPolicyVersion());
      assertThat(roleCreated.getPosition())
          .describedAs("role created before the switch on partition %d", partitionId)
          .isLessThan(switched.getPosition());
    }
  }

  @Test
  public void shouldRejectADistributedSwitchToAVersionThatIsNotNewer() {
    // given a switch distributed from partition 1, to a version partition 2 already has
    final var update = chunk().setPolicyVersion(0);

    // when
    engine.writeRecordsOnPartition(
        2,
        RecordToWrite.commandWithoutClaims()
            .key(Protocol.encodePartitionId(Protocol.DEPLOYMENT_PARTITION, 1L))
            .policyUpdate(PolicyUpdateIntent.SWITCH_VERSION, update));

    // then
    final var rejection =
        RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.SWITCH_VERSION)
            .onlyCommandRejections()
            .withPartitionId(2)
            .filter(record -> record.getValue().getUpdateId().equals(update.getUpdateId()))
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
  }
}
