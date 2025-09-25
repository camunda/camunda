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

import io.camunda.zeebe.engine.common.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class UpdateTenantMultiPartitionTest {

  private static final int PARTITION_COUNT = 3;

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);

  @Rule public final TestWatcher testWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldDistributeTenantUpdateCommand() {
    // when
    final var tenantId = UUID.randomUUID().toString();
    engine
        .tenant()
        .newTenant()
        .withTenantId(tenantId)
        .withName("Tenant 1")
        .create()
        .getValue()
        .getTenantKey();
    engine.tenant().updateTenant(tenantId).withName("Tenant 2").update();

    RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
        .withDistributionIntent(TenantIntent.CREATE)
        .await();

    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limitByCount(
                    record -> record.getIntent().equals(CommandDistributionIntent.FINISHED), 2)
                .filter(
                    record ->
                        record.getValueType() == ValueType.TENANT
                            || (record.getValueType() == ValueType.COMMAND_DISTRIBUTION
                                && ((CommandDistributionRecordValue) record.getValue()).getIntent()
                                    == TenantIntent.UPDATE)))
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
            tuple(TenantIntent.UPDATE, RecordType.COMMAND, 1),
            tuple(TenantIntent.UPDATED, RecordType.EVENT, 1),
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
                  .limit(record -> record.getIntent().equals(TenantIntent.UPDATED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .containsSubsequence(TenantIntent.UPDATE, TenantIntent.UPDATED);
    }
  }

  @Test
  public void shouldDistributeInIdentityQueue() {
    // when
    final var tenantId = UUID.randomUUID().toString();
    engine.tenant().newTenant().withTenantId(tenantId).withName("Tenant 1").create();
    engine.tenant().updateTenant(tenantId).withName("Tenant 2").update();

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
    // when
    for (int partitionId = 2; partitionId <= PARTITION_COUNT; partitionId++) {
      engine.interceptInterPartitionIntent(partitionId, TenantIntent.CREATE);
    }
    final var tenantId = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    engine
        .tenant()
        .newTenant()
        .withTenantId(tenantId)
        .withName(name)
        .create()
        .getValue()
        .getTenantKey();
    engine.tenant().updateTenant(tenantId).withName(name + "-updated").update();

    // Increase time to trigger a redistribution
    engine.increaseTime(Duration.ofMinutes(1));

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
                .limit(2))
        .extracting(r -> r.getValue().getValueType(), r -> r.getValue().getIntent())
        .containsExactly(
            tuple(ValueType.TENANT, TenantIntent.CREATE),
            tuple(ValueType.TENANT, TenantIntent.UPDATE));
  }
}
