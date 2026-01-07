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
import static io.camunda.zeebe.protocol.record.value.ErrorType.CALLED_ELEMENT_ERROR;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateCallActivityTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldMigrateCallActivity() {
    // given
    final String processId = helper.getBpmnProcessId() + "_source";
    final String childProcessId = helper.getBpmnProcessId() + "_child";
    final String targetProcessId = helper.getBpmnProcessId() + "_target";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("start")
                    .callActivity("A", c -> c.zeebeProcessId(childProcessId))
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent("start")
                    .callActivity("B", c -> c.zeebeProcessId(childProcessId))
                    .userTask("C")
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(childProcessId)
                    .startEvent("start")
                    .userTask("task")
                    .endEvent("end")
                    .done())
            .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var callActivity =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId("A")
            .withElementType(BpmnElementType.CALL_ACTIVITY)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final var callActivityElementInstanceKey = callActivity.getKey();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.CALL_ACTIVITY)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key is changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("B")
        .describedAs("Expect that version number did not change")
        .hasVersion(1)
        .hasElementInstancePath(List.of(processInstanceKey, callActivityElementInstanceKey))
        .hasProcessDefinitionPath(List.of(targetProcessDefinitionKey))
        .hasCallingElementPath(List.of());
  }

  @Test
  public void shouldAllowToContinueFlowAfterMigratingParentProcessInstance() {
    // given
    final String processId = helper.getBpmnProcessId() + "_source";
    final String childProcessId = helper.getBpmnProcessId() + "_child";
    final String targetProcessId = helper.getBpmnProcessId() + "_target";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("start")
                    .callActivity("A", c -> c.zeebeProcessId(childProcessId))
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent("start")
                    .callActivity("B", c -> c.zeebeProcessId(childProcessId))
                    .userTask("C")
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(childProcessId)
                    .startEvent("start")
                    .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                    .endEvent("end")
                    .done())
            .deploy();
    final long parentProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var childUserTask =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(parentProcessInstanceKey)
            .withElementType(BpmnElementType.USER_TASK)
            .withElementId("task")
            .getFirst();
    final var childProcessInstanceKey = childUserTask.getValue().getProcessInstanceKey();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(parentProcessInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    ENGINE.userTask().ofInstance(childProcessInstanceKey).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(childProcessInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .getFirst())
        .describedAs("Expect that the child process instance completed")
        .isNotNull();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(parentProcessInstanceKey)
                .withElementType(BpmnElementType.USER_TASK)
                .withElementId("C")
                .getFirst())
        .describedAs(
            "Expect that the parent process instance continues in the target process after migrating")
        .isNotNull();
  }

  @Test
  public void shouldAllowToTerminateFlowAfterMigratingParentProcessInstance() {
    // given
    final String processId = helper.getBpmnProcessId() + "_source";
    final String childProcessId = helper.getBpmnProcessId() + "_child";
    final String targetProcessId = helper.getBpmnProcessId() + "_target";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("start")
                    .callActivity("A", c -> c.zeebeProcessId(childProcessId))
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent("start")
                    .callActivity("B", c -> c.zeebeProcessId(childProcessId))
                    .userTask("C")
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(childProcessId)
                    .startEvent("start")
                    .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                    .endEvent("end")
                    .done())
            .deploy();
    final long parentProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var childProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(parentProcessInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getKey();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(parentProcessInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    ENGINE.processInstance().withInstanceKey(parentProcessInstanceKey).cancel();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withElementType(BpmnElementType.PROCESS)
                .limit(2)
                .map(Record::getValue)
                .map(ProcessInstanceRecordValue::getProcessInstanceKey))
        .describedAs("Expect that both the parent and child process instance terminated")
        .contains(childProcessInstanceKey, parentProcessInstanceKey);
  }

  @Test
  public void shouldMigrateChildProcessInstance() {
    // given
    final String processId = helper.getBpmnProcessId() + "_source";
    final String childProcessId = helper.getBpmnProcessId() + "_child";
    final String targetChildProcessId = helper.getBpmnProcessId() + "_target";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("start")
                    .callActivity("callActivity", c -> c.zeebeProcessId(childProcessId))
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(childProcessId)
                    .startEvent("start")
                    .userTask("A", AbstractUserTaskBuilder::zeebeUserTask)
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetChildProcessId)
                    .startEvent("start")
                    .userTask("B", AbstractUserTaskBuilder::zeebeUserTask)
                    .endEvent("end")
                    .done())
            .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final var callActivity =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId("callActivity")
            .withElementType(BpmnElementType.CALL_ACTIVITY)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final var callActivityElementInstanceKey = callActivity.getKey();

    final var childUserTask =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.USER_TASK)
            .withElementId("A")
            .getFirst();
    final var childProcessInstanceKey = childUserTask.getValue().getProcessInstanceKey();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetChildProcessId);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(childProcessInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(childProcessInstanceKey)
                .withElementType(BpmnElementType.USER_TASK)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key is changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(targetChildProcessId)
        .hasElementId("B")
        .describedAs("Expect that version number did not change")
        .hasVersion(1);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(childProcessInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key changed for child process")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed for child process")
        .hasBpmnProcessId(targetChildProcessId)
        .hasElementId(targetChildProcessId)
        .describedAs("Expect that version number did not change for child process")
        .hasVersion(1)
        .hasElementInstancePath(
            List.of(
                List.of(processInstanceKey, callActivityElementInstanceKey),
                List.of(childProcessInstanceKey)))
        .hasProcessDefinitionPath(List.of(targetProcessDefinitionKey))
        .hasCallingElementPath(List.of(0));
  }

  @Test
  public void shouldAllowToContinueFlowAfterMigratingChildProcessInstance() {
    // given
    final String processId = helper.getBpmnProcessId() + "_source";
    final String childProcessId = helper.getBpmnProcessId() + "_child";
    final String targetChildProcessId = helper.getBpmnProcessId() + "_target";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("start")
                    .callActivity("callActivity", c -> c.zeebeProcessId(childProcessId))
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(childProcessId)
                    .startEvent("start")
                    .userTask("A", AbstractUserTaskBuilder::zeebeUserTask)
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetChildProcessId)
                    .startEvent("start")
                    .userTask("B", AbstractUserTaskBuilder::zeebeUserTask)
                    .endEvent("end")
                    .done())
            .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final var childUserTask =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.USER_TASK)
            .withElementId("A")
            .getFirst();
    final var childProcessInstanceKey = childUserTask.getValue().getProcessInstanceKey();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetChildProcessId);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(childProcessInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    ENGINE.userTask().ofInstance(childProcessInstanceKey).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(childProcessInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .getFirst())
        .describedAs("Expect that the child process instance completed")
        .isNotNull();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .getFirst())
        .describedAs("Expect that the parent process instance completes after migrating")
        .isNotNull();
  }

  @Test
  public void shouldAllowToTerminateFlowAfterMigratingChildProcessInstance() {
    // given
    final String parentProcessId = helper.getBpmnProcessId() + "_source";
    final String childProcessId = helper.getBpmnProcessId() + "_child";
    final String targetChildProcessId = helper.getBpmnProcessId() + "_target";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(parentProcessId)
                    .startEvent("start")
                    .callActivity("callActivity", c -> c.zeebeProcessId(childProcessId))
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(childProcessId)
                    .startEvent("start")
                    .userTask("A", AbstractUserTaskBuilder::zeebeUserTask)
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetChildProcessId)
                    .startEvent("start")
                    .userTask("B", AbstractUserTaskBuilder::zeebeUserTask)
                    .endEvent("end")
                    .done())
            .deploy();
    final long parentProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();

    final var childUserTask =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(parentProcessInstanceKey)
            .withElementType(BpmnElementType.USER_TASK)
            .withElementId("A")
            .getFirst();
    final var childProcessInstanceKey = childUserTask.getValue().getProcessInstanceKey();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetChildProcessId);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(childProcessInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    ENGINE.processInstance().withInstanceKey(parentProcessInstanceKey).cancel();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withElementType(BpmnElementType.PROCESS)
                .limit(2)
                .map(Record::getValue)
                .map(ProcessInstanceRecordValue::getProcessInstanceKey))
        .describedAs("Expect that both the parent and child process instance terminated")
        .contains(childProcessInstanceKey, parentProcessInstanceKey);
  }

  @Test
  public void shouldReflectChangesInCallingElementPath() {
    // given
    final String processId = helper.getBpmnProcessId() + "_source";
    final String targetProcessId = helper.getBpmnProcessId() + "_target";
    final String level1ChildProcessId = helper.getBpmnProcessId() + "_child1";
    final String level2ChildProcessId = helper.getBpmnProcessId() + "_child2";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("start")
                    .callActivity("call1", c -> c.zeebeProcessId(level1ChildProcessId))
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(level1ChildProcessId)
                    .startEvent("start")
                    .callActivity("callActivity", c -> c.zeebeProcessId(level2ChildProcessId))
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent("start")
                    .callActivity("callActivity1", c -> c.zeebeProcessId(level2ChildProcessId))
                    .callActivity("callActivity0", c -> c.zeebeProcessIdExpression("processIdExpr"))
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(level2ChildProcessId)
                    .startEvent("start")
                    .userTask("A", AbstractUserTaskBuilder::zeebeUserTask)
                    .endEvent("end")
                    .done())
            .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final long parentProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, processId);
    final long level1ChildProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, level1ChildProcessId);
    final long level2ChildProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, level2ChildProcessId);
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var call1 =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId("call1")
            .withElementType(BpmnElementType.CALL_ACTIVITY)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final var call1ElementInstanceKey = call1.getKey();

    final var callActivity =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId("callActivity")
            .withElementType(BpmnElementType.CALL_ACTIVITY)
            .withParentProcessInstanceKey(processInstanceKey)
            .getFirst();

    final var callActivityElementInstanceKey = callActivity.getKey();
    final long level1ChildProcessInstanceKey = callActivity.getValue().getProcessInstanceKey();

    final var childUserTask =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withParentProcessInstanceKey(level1ChildProcessInstanceKey)
            .withElementType(BpmnElementType.USER_TASK)
            .withElementId("A")
            .getFirst();
    final var level2ChildProcessInstanceKey = childUserTask.getValue().getProcessInstanceKey();

    assertThat(childUserTask.getValue())
        .describedAs("Expect that the user task instance is activated with the correct paths")
        .hasOnlyProcessDefinitionPath(
            parentProcessDefinitionKey,
            level1ChildProcessDefinitionKey,
            level2ChildProcessDefinitionKey)
        .describedAs("Expect both call activities to have id 0 initially")
        .hasOnlyCallingElementPath(0, 0);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(level1ChildProcessInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("callActivity", "callActivity1")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ANCESTOR_MIGRATED)
                .withProcessInstanceKey(level2ChildProcessInstanceKey)
                .withElementType(BpmnElementType.USER_TASK)
                .withElementId("A")
                .getFirst()
                .getValue())
        .describedAs("Expect that element instance path has not changed during migration")
        .hasOnlyElementInstancePath(
            List.of(processInstanceKey, call1ElementInstanceKey),
            List.of(level1ChildProcessInstanceKey, callActivityElementInstanceKey),
            List.of(level2ChildProcessInstanceKey, childUserTask.getKey()))
        .describedAs(
            "Expect that the process definition path is updated to the target process definition key")
        .hasOnlyProcessDefinitionPath(
            parentProcessDefinitionKey, targetProcessDefinitionKey, level2ChildProcessDefinitionKey)
        .describedAs(
            "Expect that the calling element path is updated to the new call activity lexicographical id")
        .hasOnlyCallingElementPath(0, 1);

    ENGINE.userTask().ofInstance(level2ChildProcessInstanceKey).complete();

    final var endEventInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(level2ChildProcessInstanceKey)
            .withElementType(BpmnElementType.END_EVENT)
            .getFirst();
    assertThat(endEventInstance.getValue())
        .describedAs(
            "Expect that the end event instance is activated with the correct paths after migration")
        .hasOnlyElementInstancePath(
            List.of(processInstanceKey, call1ElementInstanceKey),
            List.of(level1ChildProcessInstanceKey, callActivityElementInstanceKey),
            List.of(level2ChildProcessInstanceKey, endEventInstance.getKey()))
        .hasOnlyProcessDefinitionPath(
            parentProcessDefinitionKey, targetProcessDefinitionKey, level2ChildProcessDefinitionKey)
        .hasOnlyCallingElementPath(0, 1);
  }

  @Test
  public void shouldMigrateCallActivityWithIncident() {
    // given
    final String processId = helper.getBpmnProcessId() + "_source";
    final String targetProcessId = helper.getBpmnProcessId() + "_target";
    final String nonExistentProcessId = helper.getBpmnProcessId() + "_nonexistent";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("start")
                    .callActivity("callActivity1", c -> c.zeebeProcessId(nonExistentProcessId))
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent("start")
                    .callActivity("callActivity2", c -> c.zeebeProcessId(nonExistentProcessId))
                    .endEvent("end")
                    .done())
            .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    assertThat(RecordingExporter.incidentRecords(IncidentIntent.CREATED).getFirst().getValue())
        .describedAs("Expect that incident exists on call activity 1")
        .hasElementId("callActivity1")
        .describedAs("Expect that incident error type is CALLED_ELEMENT_ERROR")
        .hasErrorType(CALLED_ELEMENT_ERROR);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("callActivity1", "callActivity2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key is changed to target")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that process bpmn process id changed")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that process version number did not change")
        .hasVersion(1);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.CALL_ACTIVITY)
                .getFirst()
                .getValue())
        .describedAs("Expect that call activity process definition key is changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that call activity bpmn process id and element id changed")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("callActivity2")
        .describedAs("Expect that version number did not change")
        .hasVersion(1);

    assertThat(RecordingExporter.incidentRecords(IncidentIntent.MIGRATED).getFirst().getValue())
        .describedAs("Expect that incident migrated and exists on call activity 2")
        .hasElementId("callActivity2")
        .describedAs("Expect that incident error type is CALLED_ELEMENT_ERROR")
        .hasErrorType(CALLED_ELEMENT_ERROR);

    assertThat(
            RecordingExporter.processInstanceMigrationRecords(
                    ProcessInstanceMigrationIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .findFirst())
        .describedAs("Expect that process instance is migrated")
        .isPresent()
        .map(record -> record.getValue().getRootProcessInstanceKey())
        .hasValue(processInstanceKey);
  }

  @Test
  public void shouldMigrateSubsequentCallActivitiesWithIncident() {
    // given
    final String processId = helper.getBpmnProcessId() + "_source";
    final String targetProcessId = helper.getBpmnProcessId() + "_target";
    final String level1ChildProcessId = helper.getBpmnProcessId() + "_child1";
    final String level2ChildProcessId = helper.getBpmnProcessId() + "_child2";
    final String nonExistentProcessId = helper.getBpmnProcessId() + "_nonexistent";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("start")
                    .callActivity("callActivity1", c -> c.zeebeProcessId(level1ChildProcessId))
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(level1ChildProcessId)
                    .startEvent("start")
                    .callActivity("None", c -> c.zeebeProcessId(nonExistentProcessId))
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent("start")
                    .callActivity("callActivity2", c -> c.zeebeProcessId(level2ChildProcessId))
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(level2ChildProcessId)
                    .startEvent("start")
                    .callActivity("None", c -> c.zeebeProcessId(nonExistentProcessId))
                    .endEvent("end")
                    .done())
            .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var childProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();
    final long childProcessInstanceKey = childProcessInstance.getKey();

    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withElementId("None")
                .getFirst()
                .getValue())
        .describedAs("Expect that incident exists on child call activity 'None'")
        .hasElementId("None")
        .describedAs("Expect that incident error type is CALLED_ELEMENT_ERROR")
        .hasErrorType(CALLED_ELEMENT_ERROR);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("callActivity1", "callActivity2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .getFirst()
                .getValue())
        .describedAs("Expect that parent process definition key is changed to target")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that parent process bpmn process id changed")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that parent process version number did not change")
        .hasVersion(1);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.CALL_ACTIVITY)
                .getFirst()
                .getValue())
        .describedAs("Expect that call activity process definition key is changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that call activity bpmn process id and element id changed")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("callActivity2")
        .describedAs("Expect that version number did not change")
        .hasVersion(1);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ANCESTOR_MIGRATED)
                .withProcessInstanceKey(childProcessInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .describedAs("Expect that ANCESTOR_MIGRATED event exists for the child process")
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ANCESTOR_MIGRATED)
                .withProcessInstanceKey(childProcessInstanceKey)
                .withElementType(BpmnElementType.CALL_ACTIVITY)
                .exists())
        .describedAs("Expect that ANCESTOR_MIGRATED event exists for the child call activity")
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceMigrationRecords(
                    ProcessInstanceMigrationIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .findFirst())
        .describedAs("Expect that process instance is migrated")
        .isPresent()
        .map(record -> record.getValue().getRootProcessInstanceKey())
        .hasValue(processInstanceKey);
  }
}
