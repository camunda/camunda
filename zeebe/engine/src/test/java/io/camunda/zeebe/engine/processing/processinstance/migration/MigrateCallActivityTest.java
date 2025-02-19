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
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
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

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withElementId("A")
        .withElementType(BpmnElementType.CALL_ACTIVITY)
        .withProcessInstanceKey(processInstanceKey)
        .await();

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
        .hasVersion(1);
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
        .hasVersion(1);
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
}
