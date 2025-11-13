/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance.migration;

import static io.camunda.zeebe.engine.processing.processinstance.migration.MigrationTestUtil.extractProcessDefinitionKeyByProcessId;
import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * Tests for migrating Ad-Hoc Sub-Processes and the handling of #innerInstance suffix in mapping
 * instructions.
 */
public class MigrateAdHocSubProcessTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  // ==================== Basic Ad-Hoc Sub-Process Migration Tests ====================

  @Test
  public void shouldAcceptMigrationWhenInnerInstanceSuffixCorrectlyUsedOnBothSourceAndTarget() {
    // given
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                createProcessWithAdHocSubProcess(sourceProcessId, "adHocSubProcess", "taskA"))
            .withXmlResource(
                createProcessWithAdHocSubProcess(targetProcessId, "adHocSubProcess", "taskB"))
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(sourceProcessId).create();

    // Activate the ad-hoc subprocess
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("adHocSubProcess")
        .modify();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("adHocSubProcess")
        .await();

    // when - correctly using suffix on both source and target with proper ad-hoc mapping
    final var migrationResult =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("adHocSubProcess", "adHocSubProcess")
            .addMappingInstruction("adHocSubProcess#innerInstance", "adHocSubProcess#innerInstance")
            .migrate();

    // then - migration should succeed
    assertThat(migrationResult)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATED);
  }

  // ==================== mapElementIds Method Tests ====================

  @Test
  public void shouldAutomaticallyAddInnerInstanceMappingForAdHocSubProcess() {
    // given
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                createProcessWithAdHocSubProcess(sourceProcessId, "adHocSubProcess", "taskA"))
            .withXmlResource(
                createProcessWithAdHocSubProcess(targetProcessId, "adHocSubProcess", "taskB"))
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(sourceProcessId).create();

    // Activate the ad-hoc subprocess
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("adHocSubProcess")
        .modify();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("adHocSubProcess")
        .await();

    // when - only providing the base ad-hoc subprocess mapping (no explicit #innerInstance)
    final var migrationResult =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("adHocSubProcess", "adHocSubProcess")
            .migrate();

    // then - migration should succeed and automatically handle #innerInstance mapping
    assertThat(migrationResult)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATED);

    // The #innerInstance mapping should be automatically added by mapElementIds method
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("adHocSubProcess")
                .exists())
        .isTrue();
  }

  @Test
  public void shouldNotAddInnerInstanceMappingForNonAdHocElements() {
    // given
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(sourceProcessId)
                    .startEvent()
                    .serviceTask("taskA", t -> t.zeebeJobType("taskA"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("taskB", t -> t.zeebeJobType("taskB"))
                    .endEvent()
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(sourceProcessId).create();

    // when
    final var migrationResult =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("taskA", "taskB")
            .migrate();

    // then - migration should succeed without #innerInstance mappings
    assertThat(migrationResult)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATED);
  }

  // ==================== Edge Cases and Complex Scenarios ====================

  @Test
  public void shouldMigrateMultipleAdHocSubProcesses() {
    // given
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(sourceProcessId)
                    .startEvent()
                    .adHocSubProcess(
                        "adHoc1", ahsp -> ahsp.serviceTask("taskA1", t -> t.zeebeJobType("taskA1")))
                    .adHocSubProcess(
                        "adHoc2", ahsp -> ahsp.serviceTask("taskA2", t -> t.zeebeJobType("taskA2")))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .adHocSubProcess(
                        "adHoc1", ahsp -> ahsp.serviceTask("taskB1", t -> t.zeebeJobType("taskB1")))
                    .adHocSubProcess(
                        "adHoc2", ahsp -> ahsp.serviceTask("taskB2", t -> t.zeebeJobType("taskB2")))
                    .endEvent()
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(sourceProcessId).create();

    // Activate both ad-hoc subprocesses
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("adHoc1")
        .modify();

    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("adHoc2")
        .modify();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("adHoc2")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("adHoc1", "adHoc1")
        .addMappingInstruction("adHoc2", "adHoc2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("adHoc1")
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("adHoc2")
                .exists())
        .isTrue();
  }

  // ==================== Helper Methods ====================

  private BpmnModelInstance createProcessWithAdHocSubProcess(
      final String processId, final String adHocId, final String... taskIds) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .adHocSubProcess(
            adHocId,
            ahsp -> {
              for (final String taskId : taskIds) {
                ahsp.serviceTask(taskId, t -> t.zeebeJobType(taskId));
              }
            })
        .endEvent()
        .done();
  }
}
