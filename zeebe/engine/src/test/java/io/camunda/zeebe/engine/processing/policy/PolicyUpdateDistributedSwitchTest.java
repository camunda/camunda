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
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;

/**
 * Applies a policy update on the coordinator partition and checks that every partition applies the
 * update and then switches to its version, in this order.
 */
public final class PolicyUpdateDistributedSwitchTest {

  private static final int PARTITION_COUNT = 3;

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);

  @Test
  public void shouldSwitchEveryPartitionAfterItAppliedTheUpdate() {
    // when
    engine.writeRecordsOnPartition(
        1,
        RecordToWrite.command()
            .policyUpdate(
                PolicyUpdateIntent.APPEND_CHUNK,
                chunk(1)
                    .setRoles(List.<RoleRecordValue>of(new RoleRecord().setRoleId("r1")))
                    .setGroups(List.<GroupRecordValue>of(new GroupRecord().setGroupId("g1")))));

    // then
    for (int partition = 1; partition <= PARTITION_COUNT; partition++) {
      final Record<?> switched = versionSwitched(partition, 1);
      final Record<?> roleCreated = onPartition(partition, ValueType.ROLE, RoleIntent.CREATED);
      final Record<?> groupCreated = onPartition(partition, ValueType.GROUP, GroupIntent.CREATED);
      assertThat(roleCreated.getPosition())
          .describedAs("role is created before partition %d switches", partition)
          .isLessThan(switched.getPosition());
      assertThat(groupCreated.getPosition())
          .describedAs("group is created before partition %d switches", partition)
          .isLessThan(switched.getPosition());
    }
  }

  @Test
  public void shouldRejectSwitchToTheCurrentVersion() {
    // given
    engine.writeRecordsOnPartition(
        1,
        RecordToWrite.command()
            .policyUpdate(
                PolicyUpdateIntent.APPEND_CHUNK,
                chunk(1).setRoles(List.<RoleRecordValue>of(new RoleRecord().setRoleId("r1")))));
    versionSwitched(1, 1);

    // when
    engine.writeRecordsOnPartition(
        1,
        RecordToWrite.command()
            .policyUpdate(
                PolicyUpdateIntent.SWITCH_VERSION,
                new PolicyUpdateRecord().setEnvelopeId("again").setPolicyVersion(1)));

    // then
    final var rejection =
        RecordingExporter.records()
            .onlyCommandRejections()
            .withValueType(ValueType.POLICY_UPDATE)
            .withIntent(PolicyUpdateIntent.SWITCH_VERSION)
            .filter(record -> record.getPartitionId() == 1)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.ALREADY_EXISTS);
  }

  private static Record<?> versionSwitched(final int partition, final long version) {
    return RecordingExporter.records()
        .withValueType(ValueType.POLICY_UPDATE)
        .withIntent(PolicyUpdateIntent.VERSION_SWITCHED)
        .filter(
            record ->
                record.getPartitionId() == partition
                    && ((PolicyUpdateRecordValue) record.getValue()).getPolicyVersion() == version)
        .getFirst();
  }

  private static Record<?> onPartition(
      final int partition,
      final ValueType valueType,
      final io.camunda.zeebe.protocol.record.intent.Intent intent) {
    return RecordingExporter.records()
        .withValueType(valueType)
        .withIntent(intent)
        .filter(record -> record.getPartitionId() == partition)
        .getFirst();
  }

  private static PolicyUpdateRecord chunk(final long version) {
    return new PolicyUpdateRecord()
        .setEnvelopeId(UUID.randomUUID().toString())
        .setPolicyVersion(version)
        .setChunkCount(1)
        .setChunkIndex(0);
  }
}
