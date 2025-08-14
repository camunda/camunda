/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class CreateGlobalVariableMultiPartitionTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.multiplePartition(3);

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void createGlobalVariable() {
    ENGINE_RULE.variable().withGlobalVariable("KEY_1", "VALUE").expectCreated().create();

    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limit(record -> record.getIntent().equals(CommandDistributionIntent.FINISHED)))
        .extracting(
            io.camunda.zeebe.protocol.record.Record::getIntent,
            io.camunda.zeebe.protocol.record.Record::getRecordType,
            r ->
                r.getValue() instanceof CommandDistributionRecordValue
                    ? ((CommandDistributionRecordValue) r.getValue()).getPartitionId()
                    : r.getPartitionId())
        .startsWith(
            tuple(VariableIntent.CREATE, RecordType.COMMAND, 1),
            tuple(VariableIntent.CREATED, RecordType.EVENT, 1),
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

    /*    for (int partitionId = 2; partitionId <= PARTITION_COUNT; partitionId++) {
      assertThat(
          RecordingExporter.tenantRecords()
              .withTenantId(tenantId)
              .withPartitionId(partitionId)
              .limit(record -> record.getIntent().equals(TenantIntent.CREATED))
              .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .containsSubsequence(TenantIntent.CREATE, TenantIntent.CREATED);
    }*/
  }

  /* @Test
  public void globalVariableAlreadyExists() {
    ENGINE_RULE.variable().withGlobalVariable("KEY_2", "VALUE").create();
    final var record =
        ENGINE_RULE.variable().withGlobalVariable("KEY_2", "VALUE_2").expectRejection().create();

    Assertions.assertThat(record)
        .hasIntent(VariableIntent.CREATE)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason("This variable already exists");
  }*/
}
