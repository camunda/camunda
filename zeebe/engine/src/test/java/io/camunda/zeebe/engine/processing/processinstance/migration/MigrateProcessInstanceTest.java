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

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationMappingInstruction;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateProcessInstanceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldWriteMigratedEventForProcessInstance() {
    // given
    final String processId1 = helper.getBpmnProcessId();
    final String processId2 = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId1)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(processId2)
                    .startEvent()
                    .serviceTask("B", a -> a.zeebeJobType("B"))
                    .endEvent()
                    .done())
            .deploy();
    final long otherProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, processId2);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId1).create();

    // when
    final var event =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(otherProcessDefinitionKey)
            .addMappingInstruction("A", "B")
            .migrate();

    // then
    assertThat(event)
        .hasKey(processInstanceKey)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATED);

    assertThat(event.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasTargetProcessDefinitionKey(otherProcessDefinitionKey)
        .hasMappingInstructions(
            new ProcessInstanceMigrationMappingInstruction()
                .setSourceElementId("A")
                .setTargetElementId("B"));
  }

  @Test
  public void shouldWriteElementMigratedEventForProcessInstance() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String otherProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(otherProcessId)
                    .startEvent()
                    .serviceTask("B", a -> a.zeebeJobType("B"))
                    .endEvent()
                    .done())
            .deploy();
    final long otherProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, otherProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(otherProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key changed")
        .hasProcessDefinitionKey(otherProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(otherProcessId)
        .hasElementId(otherProcessId)
        .describedAs("Expect that version number did not change")
        .hasVersion(1)
        .hasElementInstancePath(List.of(processInstanceKey))
        .describedAs(
            "Expect that process definition path changed to contain new process definition key")
        .hasProcessDefinitionPath(List.of(otherProcessDefinitionKey))
        .hasCallingElementPath(List.of());
  }

  @Test
  public void shouldWriteElementMigratedEventForProcessInstanceToNewVersion() {
    // given
    final String processId = helper.getBpmnProcessId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .endEvent()
                .done())
        .deploy();
    final var secondVersionDeployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .userTask()
                    .endEvent()
                    .done())
            .deploy();

    final long v2ProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(secondVersionDeployment, processId);

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVersion(1).create();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(v2ProcessDefinitionKey)
        .addMappingInstruction("A", "A")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .onlyEvents()
                .withIntent(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key changed")
        .hasProcessDefinitionKey(v2ProcessDefinitionKey)
        .describedAs("Expect that version number changed")
        .hasVersion(2)
        .describedAs("Expect that bpmn process id and element id did not change")
        .hasBpmnProcessId(processId)
        .hasElementInstancePath(List.of(processInstanceKey))
        .describedAs(
            "Expect that process definition path changed to contain new process definition key")
        .hasProcessDefinitionPath(List.of(v2ProcessDefinitionKey))
        .hasCallingElementPath(List.of());
  }
}
