/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class RemoveEntityTenantMultiPartitionTest {

  private static final int PARTITION_COUNT = 3;
  @ClassRule public static final EngineRule ENGINE = EngineRule.multiplePartition(PARTITION_COUNT);
  private static long userKey;
  @Rule public final TestWatcher testWatcher = new RecordingExporterTestWatcher();

  @BeforeClass
  public static void setUp() {
    userKey =
        ENGINE
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getKey();
  }

  @Before
  public void beforeEach() {
    RecordingExporter.reset();
  }

  @Test
  public void shouldDistributeTenantRemoveEntityCommand() {
    // when
    final var tenantId = UUID.randomUUID().toString();
    final var tenantKey =
        ENGINE.tenant().newTenant().withTenantId(tenantId).create().getValue().getTenantKey();
    ENGINE
        .tenant()
        .addEntity(tenantKey)
        .withEntityKey(userKey)
        .withEntityType(EntityType.USER)
        .add();
    ENGINE
        .tenant()
        .removeEntity(tenantKey)
        .withEntityKey(userKey)
        .withEntityType(EntityType.USER)
        .remove();

    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limitByCount(
                    record -> record.getIntent().equals(CommandDistributionIntent.FINISHED), 3))
        .extracting(
            Record::getIntent,
            Record::getRecordType,
            r ->
                // We want to verify the partition id where the creation was distributing to and
                // where it was completed. Since only the CommandDistribution records have a
                // value that contains the partition id, we use the partition id the record was
                // written on for the other records.
                r.getValue() instanceof CommandDistributionRecordValue
                    ? ((CommandDistributionRecordValue) r.getValue()).getPartitionId()
                    : r.getPartitionId())
        .containsSubsequence(
            tuple(TenantIntent.REMOVE_ENTITY, RecordType.COMMAND, 1),
            tuple(TenantIntent.ENTITY_REMOVED, RecordType.EVENT, 1),
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
              RecordingExporter.tenantRecords()
                  .withPartitionId(partitionId)
                  .limit(record -> record.getIntent().equals(TenantIntent.ENTITY_REMOVED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .containsSubsequence(TenantIntent.REMOVE_ENTITY, TenantIntent.ENTITY_REMOVED);
    }
  }

  @Test
  public void shouldDistributeInIdentityQueue() {
    // when
    final var tenantId = UUID.randomUUID().toString();
    final var tenantKey =
        ENGINE.tenant().newTenant().withTenantId(tenantId).create().getValue().getTenantKey();
    ENGINE
        .tenant()
        .addEntity(tenantKey)
        .withEntityKey(userKey)
        .withEntityType(EntityType.USER)
        .add();
    ENGINE
        .tenant()
        .removeEntity(tenantKey)
        .withEntityKey(userKey)
        .withEntityType(EntityType.USER)
        .remove();

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords()
                .limitByCount(r -> r.getIntent().equals(CommandDistributionIntent.FINISHED), 2)
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

    // when
    final var tenantId = UUID.randomUUID().toString();
    final var tenantKey =
        ENGINE.tenant().newTenant().withTenantId(tenantId).create().getValue().getTenantKey();
    ENGINE
        .tenant()
        .addEntity(tenantKey)
        .withEntityKey(userKey)
        .withEntityType(EntityType.USER)
        .add();
    ENGINE
        .tenant()
        .removeEntity(tenantKey)
        .withEntityKey(userKey)
        .withEntityType(EntityType.USER)
        .remove();

    // Increase time to trigger a redistribution
    ENGINE.increaseTime(Duration.ofMinutes(1));

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
                .limit(3))
        .extracting(r -> r.getValue().getValueType(), r -> r.getValue().getIntent())
        .containsExactly(
            tuple(ValueType.TENANT, TenantIntent.CREATE),
            tuple(ValueType.TENANT, TenantIntent.ADD_ENTITY),
            tuple(ValueType.TENANT, TenantIntent.REMOVE_ENTITY));
  }

  private void interceptUserCreateForPartition(final int partitionId) {
    final var hasInterceptedPartition = new AtomicBoolean(false);
    ENGINE.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) -> {
          if (hasInterceptedPartition.get()) {
            return true;
          }
          hasInterceptedPartition.set(true);
          return !(receiverPartitionId == partitionId && intent == TenantIntent.CREATE);
        });
  }
}
