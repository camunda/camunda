/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance.migration;

import static io.camunda.zeebe.engine.processing.processinstance.migration.MigrationTestUtil.extractProcessDefinitionKeyByProcessId;
import static io.camunda.zeebe.model.bpmn.impl.ZeebeConstants.AD_HOC_SUB_PROCESS_ELEMENTS;
import static io.camunda.zeebe.model.bpmn.impl.ZeebeConstants.AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX;
import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
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

  // ==================== NON Ad-Hoc Sub-Process Migration Test ====================

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

  // ==================== Basic Ad-Hoc Sub-Process Migration Tests ====================

  @Test
  public void shouldMigrateAdHocSubProcessAndUpdateVariables() {
    // given
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(createProcessWithAdHocSubProcess(sourceProcessId, "ahsp-1", "taskA"))
            .withXmlResource(createProcessWithAdHocSubProcess(targetProcessId, "ahsp-2", "taskB"))
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
        .activateElement("ahsp-1")
        .modify();

    final long adHocSubProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("ahsp-1")
            .getFirst()
            .getKey();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("ahsp-1", "ahsp-2")
        .addMappingInstruction("taskA", "taskB")
        .migrate();

    // then - verify element migrated and variables were updated for ad-hoc subprocess
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("ahsp-2")
                .getFirst()
                .getValue())
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("ahsp-2")
        .hasBpmnElementType(BpmnElementType.AD_HOC_SUB_PROCESS);

    assertThat(
            RecordingExporter.variableRecords(VariableIntent.UPDATED)
                .withScopeKey(adHocSubProcessInstanceKey)
                .withName(AD_HOC_SUB_PROCESS_ELEMENTS)
                .exists())
        .isTrue();

    final var updatedVariable =
        RecordingExporter.variableRecords(VariableIntent.UPDATED)
            .withScopeKey(adHocSubProcessInstanceKey)
            .withName(AD_HOC_SUB_PROCESS_ELEMENTS)
            .getFirst()
            .getValue();

    assertThat(updatedVariable)
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId);
  }

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
                        "ahsp-1", ahsp -> ahsp.serviceTask("taskA1", t -> t.zeebeJobType("taskA1")))
                    .adHocSubProcess(
                        "ahsp-2", ahsp -> ahsp.serviceTask("taskA2", t -> t.zeebeJobType("taskA2")))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .adHocSubProcess(
                        "ahsp-3", ahsp -> ahsp.serviceTask("taskB1", t -> t.zeebeJobType("taskB1")))
                    .adHocSubProcess(
                        "ahsp-4", ahsp -> ahsp.serviceTask("taskB2", t -> t.zeebeJobType("taskB2")))
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
        .activateElement("ahsp-1")
        .modify();

    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("ahsp-2")
        .modify();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("ahsp-2")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("ahsp-1", "ahsp-3")
        .addMappingInstruction("taskA1", "taskB1")
        .addMappingInstruction("ahsp-2", "ahsp-4")
        .addMappingInstruction("taskA2", "taskB2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("ahsp-3")
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("ahsp-4")
                .exists())
        .isTrue();
  }

  // ==================== #innerInstance Suffix Validation Tests ====================

  @Test
  public void shouldMigrateAdHocSubProcessWithInnerInstanceAndUpdateVariables() {
    // given
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(createProcessWithAdHocSubProcess(sourceProcessId, "ahsp-1", "taskA"))
            .withXmlResource(createProcessWithAdHocSubProcess(targetProcessId, "ahsp-2", "taskB"))
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
        .activateElement("ahsp-1")
        .modify();

    final long adHocSubProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("ahsp-1")
            .getFirst()
            .getKey();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("ahsp-1", "ahsp-2")
        .addMappingInstruction(
            "ahsp-1" + AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX,
            "ahsp-2" + AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX)
        .migrate();

    // then - verify element migrated and variables were updated for ad-hoc subprocess
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("ahsp-2")
                .getFirst()
                .getValue())
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("ahsp-2")
        .hasBpmnElementType(BpmnElementType.AD_HOC_SUB_PROCESS);

    assertThat(
            RecordingExporter.variableRecords(VariableIntent.UPDATED)
                .withScopeKey(adHocSubProcessInstanceKey)
                .withName(AD_HOC_SUB_PROCESS_ELEMENTS)
                .exists())
        .isTrue();

    final var updatedVariable =
        RecordingExporter.variableRecords(VariableIntent.UPDATED)
            .withScopeKey(adHocSubProcessInstanceKey)
            .withName(AD_HOC_SUB_PROCESS_ELEMENTS)
            .getFirst()
            .getValue();

    assertThat(updatedVariable)
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId);
  }

  @Test
  public void shouldRejectMigrationWhenInnerInstanceInSourceButNotInTarget() {
    // given
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(createProcessWithAdHocSubProcess(sourceProcessId, "ahsp-1", "taskA"))
            .withXmlResource(createProcessWithAdHocSubProcess(targetProcessId, "ahsp-2", "taskB"))
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
        .activateElement("ahsp-1")
        .modify();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("ahsp-1")
        .await();

    // when - using #innerInstance in source but not in target
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("ahsp-1", "ahsp-2")
        .addMappingInstruction("ahsp-1" + AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX, "ahsp-4")
        .expectRejection()
        .migrate();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            String.format(
                "Expected to migrate process instance '%s' but mapping instructions contain a non-existing target element id 'ahsp-4'. Elements provided in mapping instructions must exist in the target process definition.",
                processInstanceKey));
  }

  @Test
  public void shouldRejectMigrationWhenInnerInstanceInTargetButNotInSource() {
    // given
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(createProcessWithAdHocSubProcess(sourceProcessId, "ahsp-1", "taskA"))
            .withXmlResource(createProcessWithAdHocSubProcess(targetProcessId, "ahsp-2", "taskB"))
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
        .activateElement("ahsp-1")
        .modify();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("ahsp-1")
        .await();

    // when - using #innerInstance in target but not in source
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("ahsp-1", "ahsp-2")
        .addMappingInstruction("ahsp-3", "ahsp-2" + AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX)
        .expectRejection()
        .migrate();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            String.format(
                "Expected to migrate process instance '%s' but mapping instructions contain a non-existing source element id 'ahsp-3'. Elements provided in mapping instructions must exist in the source process definition.",
                processInstanceKey));
  }

  @Test
  public void shouldRejectMigrationWhenInnerInstanceUsedWithoutBaseElementMapping() {
    // given
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(createProcessWithAdHocSubProcess(sourceProcessId, "ahsp-1", "taskA"))
            .withXmlResource(createProcessWithAdHocSubProcess(targetProcessId, "ahsp-2", "taskB"))
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
        .activateElement("ahsp-1")
        .modify();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("ahsp-1")
        .await();

    // when - using #innerInstance without providing mapping for the base element
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction(
            "ahsp-1" + AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX,
            "ahsp-2" + AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX)
        .expectRejection()
        .migrate();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            String.format(
                "Expected to migrate process instance '%s' but no mapping instruction defined for active element with id 'ahsp-1'. Elements cannot be migrated without a mapping.",
                processInstanceKey));
  }

  @Test
  public void shouldRejectMigrationWhenInnerInstanceSuffixUsedWithoutAdHocSubProcess() {
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

    // when - attempting to use #innerInstance suffix on a non-ad-hoc element
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("taskA", "taskB")
        .addMappingInstruction(
            "taskA" + AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX,
            "taskB" + AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX)
        .expectRejection()
        .migrate();

    // then - fails because element id with ad-hoc sub-process reference doesn't exist
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            String.format(
                "Expected to migrate process instance '%s' but mapping instructions contain a non-existing source element id 'taskA#innerInstance'. Elements provided in mapping instructions must exist in the source process definition.",
                processInstanceKey));
  }

  @Test
  public void shouldRejectMigrationWhenInnerInstanceSuffixUsedWithBlankBase() {
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

    // when - attempting to use #innerInstance suffix on a non-ad-hoc element
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("taskA", "taskB")
        .addMappingInstruction(
            AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX,
            AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX)
        .expectRejection()
        .migrate();

    // then - fails because element id with ad-hoc sub-process reference doesn't exist
    final var rejectionRecord =
        RecordingExporter.processInstanceMigrationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            String.format(
                "Expected to migrate process instance '%s' but mapping instructions contain a non-existing source element id '#innerInstance'. Elements provided in mapping instructions must exist in the source process definition.",
                processInstanceKey));
  }

  // ==================== Multi-Instance Ad-Hoc Sub-Process Tests ====================

  @Test
  public void shouldMigrateAdHocSubProcessWithinParallelMultiInstanceBody() {
    // given
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                createProcessWithParallelMultiInstanceAdHocSubProcess(
                    sourceProcessId, "adHocSubProcess", "taskA"))
            .withXmlResource(
                createProcessWithParallelMultiInstanceAdHocSubProcess(
                    targetProcessId, "adHocSubProcess", "taskB", "taskC"))
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(sourceProcessId)
            .withVariable("items", java.util.Arrays.asList(1, 2))
            .create();

    // Wait for the first multi-instance iteration to activate the ad-hoc subprocess
    final long adHocSubProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("adHocSubProcess")
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .getFirst()
            .getKey();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("adHocSubProcess", "adHocSubProcess")
        .addMappingInstruction("taskA", "taskB")
        .migrate();

    // then - verify multi-instance body migrated
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("adHocSubProcess")
                .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
                .exists())
        .isTrue();

    // then - verify ad-hoc subprocess migrated
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("adHocSubProcess")
                .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
                .getFirst()
                .getValue())
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("adHocSubProcess");

    // then - verify adHocSubProcessElements variable was updated
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.UPDATED)
                .withScopeKey(adHocSubProcessInstanceKey)
                .withName(AD_HOC_SUB_PROCESS_ELEMENTS)
                .exists())
        .isTrue();

    final var updatedVariable =
        RecordingExporter.variableRecords(VariableIntent.UPDATED)
            .withScopeKey(adHocSubProcessInstanceKey)
            .withName(AD_HOC_SUB_PROCESS_ELEMENTS)
            .getFirst()
            .getValue();

    assertThat(updatedVariable)
        .isNotNull()
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId);
  }

  @Test
  public void shouldMigrateAdHocSubProcessWithinSequentialMultiInstanceBody() {
    // given
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                createProcessWithSequentialMultiInstanceAdHocSubProcess(
                    sourceProcessId, "adHocSubProcess", "taskA"))
            .withXmlResource(
                createProcessWithSequentialMultiInstanceAdHocSubProcess(
                    targetProcessId, "adHocSubProcess", "taskB", "taskC"))
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(sourceProcessId)
            .withVariable("items", java.util.Arrays.asList(1, 2))
            .create();

    // Wait for the first sequential iteration to activate the ad-hoc subprocess
    final long adHocSubProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("adHocSubProcess")
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .getFirst()
            .getKey();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("adHocSubProcess", "adHocSubProcess")
        .addMappingInstruction("taskA", "taskB")
        .migrate();

    // then - verify multi-instance body migrated (sequential)
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("adHocSubProcess")
                .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
                .exists())
        .isTrue();

    // then - verify ad-hoc subprocess migrated
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("adHocSubProcess")
                .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
                .exists())
        .isTrue();

    // then - verify adHocSubProcessElements variable was updated
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.UPDATED)
                .withScopeKey(adHocSubProcessInstanceKey)
                .withName(AD_HOC_SUB_PROCESS_ELEMENTS)
                .exists())
        .isTrue();

    final var updatedVariable =
        RecordingExporter.variableRecords(VariableIntent.UPDATED)
            .withScopeKey(adHocSubProcessInstanceKey)
            .withName(AD_HOC_SUB_PROCESS_ELEMENTS)
            .getFirst()
            .getValue();

    assertThat(updatedVariable)
        .isNotNull()
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId);
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

  private BpmnModelInstance createProcessWithParallelMultiInstanceAdHocSubProcess(
      final String processId, final String adHocId, final String... taskIds) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .adHocSubProcess(
            adHocId,
            ahsp -> {
              ahsp.multiInstance(
                  mi ->
                      mi.parallel()
                          .zeebeInputCollectionExpression("items")
                          .zeebeInputElement("item"));
              for (final String taskId : taskIds) {
                ahsp.serviceTask(taskId, t -> t.zeebeJobType(taskId));
              }
            })
        .endEvent()
        .done();
  }

  private BpmnModelInstance createProcessWithSequentialMultiInstanceAdHocSubProcess(
      final String processId, final String adHocId, final String... taskIds) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .adHocSubProcess(
            adHocId,
            ahsp -> {
              ahsp.multiInstance(
                  mi ->
                      mi.sequential()
                          .zeebeInputCollectionExpression("items")
                          .zeebeInputElement("item"));
              for (final String taskId : taskIds) {
                ahsp.serviceTask(taskId, t -> t.zeebeJobType(taskId));
              }
            })
        .endEvent()
        .done();
  }
}
