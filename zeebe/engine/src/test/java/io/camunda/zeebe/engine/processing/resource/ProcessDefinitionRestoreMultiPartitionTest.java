/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionRestoreIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ProcessDefinitionRestoreMultiPartitionTest {

  private static final int PARTITION_COUNT = 3;

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRestoreStrayDefinitionsOnEveryPartitionIndependently() {
    // given - a deployment replicates the process definition's PROCESS_CACHE row to every
    // partition (it's a GLOBAL column family), then each of partitions 2 and 3 independently
    // develops its own stray PENDING_DELETION row
    final var processId = Strings.newRandomValidBpmnId();
    final var v1 =
        engine
            .deployment()
            .withXmlResource(simpleProcess(processId))
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst();

    engine.stop();
    engine.writeRecordsOnPartition(
        2,
        RecordToWrite.event()
            .key(v1.getProcessDefinitionKey())
            .process(ProcessIntent.DELETING, deletingRecord(v1)));
    engine.writeRecordsOnPartition(
        3,
        RecordToWrite.event()
            .key(v1.getProcessDefinitionKey())
            .process(ProcessIntent.DELETING, deletingRecord(v1)));
    engine.start();

    RecordingExporter.processRecords()
        .withPartitionId(2)
        .withIntent(ProcessIntent.DELETING)
        .withProcessDefinitionKey(v1.getProcessDefinitionKey())
        .await();
    RecordingExporter.processRecords()
        .withPartitionId(3)
        .withIntent(ProcessIntent.DELETING)
        .withProcessDefinitionKey(v1.getProcessDefinitionKey())
        .await();

    // when - enable the restore and restart
    engine.stop();
    engine.withEngineConfig(cfg -> cfg.setEnableProcessDefinitionRestore(true));
    engine.start();

    // then - each partition's own loop finishes and restores its own stray row
    RecordingExporter.processDefinitionRestoreRecords(ProcessDefinitionRestoreIntent.FINISHED)
        .withPartitionId(2)
        .getFirst();
    RecordingExporter.processDefinitionRestoreRecords(ProcessDefinitionRestoreIntent.FINISHED)
        .withPartitionId(3)
        .getFirst();

    assertThat(
            RecordingExporter.processDefinitionRestoreRecords(
                    ProcessDefinitionRestoreIntent.RESTORED)
                .withPartitionId(2)
                .withRecordKey(v1.getProcessDefinitionKey())
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.processDefinitionRestoreRecords(
                    ProcessDefinitionRestoreIntent.RESTORED)
                .withPartitionId(3)
                .withRecordKey(v1.getProcessDefinitionKey())
                .exists())
        .isTrue();

    // and partition 1, which never saw a stray row, finishes with no RESTORED events of its own
    RecordingExporter.processDefinitionRestoreRecords(ProcessDefinitionRestoreIntent.FINISHED)
        .withPartitionId(1)
        .getFirst();
    assertThat(
            RecordingExporter.records()
                .limit(
                    r ->
                        r.getPartitionId() == 1
                            && r.getValueType() == ValueType.PROCESS_DEFINITION_RESTORE
                            && r.getIntent() == ProcessDefinitionRestoreIntent.FINISHED)
                .withPartitionId(1)
                .withValueType(ValueType.PROCESS_DEFINITION_RESTORE)
                .withIntent(ProcessDefinitionRestoreIntent.RESTORED)
                .asList())
        .isEmpty();
  }

  private static BpmnModelInstance simpleProcess(final String processId) {
    return Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
  }

  private static ProcessRecord deletingRecord(final ProcessMetadataValue metadata) {
    return new ProcessRecord()
        .setKey(metadata.getProcessDefinitionKey())
        .setBpmnProcessId(metadata.getBpmnProcessId())
        .setVersion(metadata.getVersion())
        .setResourceName(metadata.getResourceName())
        .setTenantId(metadata.getTenantId());
  }
}
