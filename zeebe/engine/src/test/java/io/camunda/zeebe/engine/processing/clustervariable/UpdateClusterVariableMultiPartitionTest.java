/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clustervariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class UpdateClusterVariableMultiPartitionTest {

  private static final int PARTITION_COUNT = 3;

  @ClassRule
  public static final EngineRule ENGINE_RULE = EngineRule.multiplePartition(PARTITION_COUNT);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void updateGlobalScopedClusterVariableMultiPartition() {
    // given
    ENGINE_RULE
        .clusterVariables()
        .withName("KEY_1")
        .setGlobalScope()
        .withValue("\"VALUE\"")
        .create();

    RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
        .withDistributionIntent(ClusterVariableIntent.CREATE)
        .await();

    // when
    ENGINE_RULE
        .clusterVariables()
        .setGlobalScope()
        .withName("KEY_1")
        .withValue("\"UPDATED_VALUE\"")
        .update();

    // then
    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limitByCount(
                    record -> record.getIntent().equals(CommandDistributionIntent.FINISHED), 3)
                .filter(
                    record ->
                        record.getValueType() == ValueType.CLUSTER_VARIABLE
                            || (record.getValueType() == ValueType.COMMAND_DISTRIBUTION
                                && ((CommandDistributionRecordValue) record.getValue()).getIntent()
                                    == ClusterVariableIntent.UPDATE)))
        .extracting(
            Record::getIntent,
            Record::getRecordType,
            r ->
                r.getValue() instanceof CommandDistributionRecordValue
                    ? ((CommandDistributionRecordValue) r.getValue()).getPartitionId()
                    : r.getPartitionId())
        .containsSubsequence(
            tuple(ClusterVariableIntent.UPDATE, RecordType.COMMAND, 1),
            tuple(ClusterVariableIntent.UPDATED, RecordType.EVENT, 1),
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
                  .limit(record -> record.getIntent().equals(ClusterVariableIntent.UPDATED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .containsSubsequence(ClusterVariableIntent.UPDATE, ClusterVariableIntent.UPDATED);
    }
  }

  @Test
  public void updateTenantScopedClusterVariableMultiPartition() {
    // given
    ENGINE_RULE
        .clusterVariables()
        .withName("KEY_2")
        .setTenantScope()
        .withTenantId("tenant_2")
        .withValue("\"VALUE\"")
        .create();

    RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
        .withDistributionIntent(ClusterVariableIntent.CREATE)
        .await();

    // when
    ENGINE_RULE
        .clusterVariables()
        .withName("KEY_2")
        .setTenantScope()
        .withTenantId("tenant_2")
        .withValue("\"UPDATED_VALUE\"")
        .update();

    // then
    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limitByCount(
                    record -> record.getIntent().equals(CommandDistributionIntent.FINISHED), 3)
                .filter(
                    record ->
                        record.getValueType() == ValueType.CLUSTER_VARIABLE
                            || (record.getValueType() == ValueType.COMMAND_DISTRIBUTION
                                && ((CommandDistributionRecordValue) record.getValue()).getIntent()
                                    == ClusterVariableIntent.UPDATE)))
        .extracting(
            Record::getIntent,
            Record::getRecordType,
            r ->
                r.getValue() instanceof CommandDistributionRecordValue
                    ? ((CommandDistributionRecordValue) r.getValue()).getPartitionId()
                    : r.getPartitionId())
        .containsSubsequence(
            tuple(ClusterVariableIntent.UPDATE, RecordType.COMMAND, 1),
            tuple(ClusterVariableIntent.UPDATED, RecordType.EVENT, 1),
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
                  .limit(record -> record.getIntent().equals(ClusterVariableIntent.UPDATED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .containsSubsequence(ClusterVariableIntent.UPDATE, ClusterVariableIntent.UPDATED);
    }
  }
}
