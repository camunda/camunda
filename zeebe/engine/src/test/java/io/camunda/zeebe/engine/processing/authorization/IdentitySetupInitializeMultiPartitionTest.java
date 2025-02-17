/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class IdentitySetupInitializeMultiPartitionTest {

  private static final int PARTITION_COUNT = 3;

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldTestLifecycle() {
    // when
    final var role = new RoleRecord().setRoleKey(1).setName("roleName");
    final var user =
        new UserRecord()
            .setUserKey(2)
            .setUsername("username")
            .setName("name")
            .setPassword("password")
            .setEmail("email");
    final var tenant =
        new TenantRecord().setTenantKey(3).setTenantId("tenant-id").setName("tenant-name");

    final var identitySetupKey =
        engine
            .identitySetup()
            .initialize()
            .withRole(role)
            .withUser(user)
            .withTenant(tenant)
            .initialize()
            .getKey();

    // then
    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limit(r -> r.getIntent().equals(CommandDistributionIntent.FINISHED)))
        .extracting(
            Record::getIntent,
            Record::getRecordType,
            r ->
                // We want to verify the partition id where the deletion was distributing to and
                // where it was completed. Since only the CommandDistribution records have a
                // value that contains the partition id, we use the partition id the record was
                // written on for the other records.
                r.getValue() instanceof CommandDistributionRecordValue
                    ? ((CommandDistributionRecordValue) r.getValue()).getPartitionId()
                    : r.getPartitionId())
        .containsSubsequence(
            tuple(IdentitySetupIntent.INITIALIZE, RecordType.COMMAND, 1),
            tuple(IdentitySetupIntent.INITIALIZED, RecordType.EVENT, 1),
            tuple(CommandDistributionIntent.STARTED, RecordType.EVENT, 1))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 2))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 3))
        .endsWith(tuple(CommandDistributionIntent.FINISHED, RecordType.EVENT, 1));

    for (int partitionId = 2; partitionId < PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.identitySetupRecords()
                  .withRecordKey(identitySetupKey)
                  .withPartitionId(partitionId)
                  .limit(r -> r.getIntent().equals(IdentitySetupIntent.INITIALIZED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .containsSubsequence(IdentitySetupIntent.INITIALIZE, IdentitySetupIntent.INITIALIZED);
    }
  }

  @Test
  public void shouldDistributeInIdentityQueue() {
    // when
    final var role = new RoleRecord().setRoleKey(1).setName("roleName");
    final var user =
        new UserRecord()
            .setUserKey(2)
            .setUsername("username")
            .setName("name")
            .setPassword("password")
            .setEmail("email");
    final var tenant =
        new TenantRecord().setTenantKey(3).setTenantId("tenant-id").setName("tenant-name");

    engine
        .identitySetup()
        .initialize()
        .withRole(role)
        .withUser(user)
        .withTenant(tenant)
        .initialize();

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords()
                .limitByCount(r -> r.getIntent().equals(CommandDistributionIntent.FINISHED), 3)
                .withIntent(CommandDistributionIntent.ENQUEUED))
        .extracting(r -> r.getValue().getQueueId())
        .containsOnly(DistributionQueue.IDENTITY.getQueueId());
  }

  @Test
  public void distributionShouldNotOvertakeOtherCommandsInSameQueue() {
    // given the role creation distribution is intercepted
    for (int partitionId = 2; partitionId <= PARTITION_COUNT; partitionId++) {
      interceptRoleCreateForPartition(partitionId);
    }

    engine.role().newRole("foo").create();

    // when
    final var role = new RoleRecord().setRoleKey(1).setName("roleName");
    final var user =
        new UserRecord()
            .setUserKey(2)
            .setUsername("username")
            .setName("name")
            .setPassword("password")
            .setEmail("email");
    final var tenant =
        new TenantRecord().setTenantKey(3).setTenantId("tenant-id").setName("tenant-name");

    engine
        .identitySetup()
        .initialize()
        .withRole(role)
        .withUser(user)
        .withTenant(tenant)
        .initialize();

    // Increase time to trigger a redistribution
    engine.increaseTime(Duration.ofMinutes(1));

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
                .limit(2))
        .extracting(r -> r.getValue().getValueType(), r -> r.getValue().getIntent())
        .containsExactly(
            tuple(ValueType.ROLE, RoleIntent.CREATE),
            tuple(ValueType.IDENTITY_SETUP, IdentitySetupIntent.INITIALIZE));
  }

  private void interceptRoleCreateForPartition(final int partitionId) {
    final var hasInterceptedPartition = new AtomicBoolean(false);
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) -> {
          if (hasInterceptedPartition.get()) {
            return true;
          }
          hasInterceptedPartition.set(true);
          return !(receiverPartitionId == partitionId && intent == RoleIntent.CREATE);
        });
  }
}
