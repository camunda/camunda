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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class RemovePermissionAuthorizationMultiPartitionTest {

  private static final int PARTITION_COUNT = 3;

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldTestLifecycle() {
    // when
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    final var ownerKey = createUserWithPermission(resourceId, resourceType, permissionType);

    engine
        .authorization()
        .permission()
        .withOwnerKey(ownerKey)
        .withResourceType(resourceType)
        .withPermission(permissionType, resourceId)
        .remove();

    // then
    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limitByCount(r -> r.getIntent().equals(CommandDistributionIntent.FINISHED), 4)
                .filter(
                    record ->
                        record.getValueType() == ValueType.AUTHORIZATION
                            || (record.getValueType() == ValueType.COMMAND_DISTRIBUTION
                                && ((CommandDistributionRecordValue) record.getValue()).getIntent()
                                    == AuthorizationIntent.REMOVE_PERMISSION)))
        .extracting(
            io.camunda.zeebe.protocol.record.Record::getIntent,
            io.camunda.zeebe.protocol.record.Record::getRecordType,
            r ->
                // We want to verify the partition id where the deletion was distributing to and
                // where it was completed. Since only the CommandDistribution records have a
                // value that contains the partition id, we use the partition id the record was
                // written on for the other records.
                r.getValue() instanceof CommandDistributionRecordValue
                    ? ((CommandDistributionRecordValue) r.getValue()).getPartitionId()
                    : r.getPartitionId())
        .containsSubsequence(
            tuple(AuthorizationIntent.REMOVE_PERMISSION, RecordType.COMMAND, 1),
            tuple(AuthorizationIntent.PERMISSION_REMOVED, RecordType.EVENT, 1),
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
              RecordingExporter.records()
                  .withPartitionId(partitionId)
                  .limit(r -> r.getIntent().equals(AuthorizationIntent.PERMISSION_REMOVED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .endsWith(AuthorizationIntent.REMOVE_PERMISSION, AuthorizationIntent.PERMISSION_REMOVED);
    }
  }

  @Test
  public void shouldDistributeInIdentityQueue() {
    // given
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    final var ownerKey = createUserWithPermission(resourceId, resourceType, permissionType);

    // when
    engine
        .authorization()
        .permission()
        .withOwnerKey(ownerKey)
        .withResourceType(resourceType)
        .withPermission(permissionType, resourceId)
        .remove();

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords()
                .limitByCount(r -> r.getIntent().equals(CommandDistributionIntent.FINISHED), 4)
                .withIntent(CommandDistributionIntent.ENQUEUED))
        .extracting(r -> r.getValue().getQueueId())
        .containsOnly(DistributionQueue.IDENTITY.getQueueId());
  }

  @Test
  public void distributionShouldNotOvertakeOtherCommandsInSameQueue() {
    // given the user creation distribution is intercepted
    for (int partitionId = 2; partitionId <= PARTITION_COUNT; partitionId++) {
      interceptUserCreateForPartition(partitionId);
    }

    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.CREATE;
    final var resourceId = UUID.randomUUID().toString();
    final var ownerKey = createUserWithPermission(resourceId, resourceType, permissionType);

    // when
    engine
        .authorization()
        .permission()
        .withOwnerKey(ownerKey)
        .withResourceType(resourceType)
        .withPermission(permissionType, resourceId)
        .remove();

    // Increase time to trigger a redistribution
    engine.increaseTime(Duration.ofMinutes(1));

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
                .limit(4))
        .extracting(r -> r.getValue().getValueType(), r -> r.getValue().getIntent())
        .containsExactly(
            tuple(ValueType.USER, UserIntent.CREATE),
            tuple(ValueType.AUTHORIZATION, AuthorizationIntent.ADD_PERMISSION),
            tuple(ValueType.AUTHORIZATION, AuthorizationIntent.ADD_PERMISSION),
            tuple(ValueType.AUTHORIZATION, AuthorizationIntent.REMOVE_PERMISSION));
  }

  private void interceptUserCreateForPartition(final int partitionId) {
    final var hasInterceptedPartition = new AtomicBoolean(false);
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) -> {
          if (hasInterceptedPartition.get()) {
            return true;
          }
          hasInterceptedPartition.set(true);
          return !(receiverPartitionId == partitionId && intent == UserIntent.CREATE);
        });
  }

  private long createUserWithPermission(
      final String resourceId,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    final var ownerKey =
        engine
            .user()
            .newUser(UUID.randomUUID().toString())
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getKey();
    engine
        .authorization()
        .permission()
        .withOwnerKey(ownerKey)
        .withResourceType(resourceType)
        .withPermission(permissionType, resourceId)
        .add();
    return ownerKey;
  }
}
