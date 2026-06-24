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
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateSubprocessTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldMigrateSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("start")
                    .subProcess(
                        "sub1",
                        s ->
                            s.embeddedSubProcess()
                                .startEvent()
                                .serviceTask("A", t -> t.zeebeJobType("task"))
                                .endEvent())
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent("start")
                    .subProcess(
                        "sub2",
                        s ->
                            s.embeddedSubProcess()
                                .startEvent()
                                .serviceTask("B", t -> t.zeebeJobType("task"))
                                .endEvent())
                    .endEvent("end")
                    .done())
            .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .addMappingInstruction("sub1", "sub2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SUB_PROCESS)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key is changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("sub2")
        .describedAs("Expect that version number did not change")
        .hasVersion(1);
  }

  @Test
  public void shouldMigrateNestedSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("start")
                    .subProcess(
                        "sub1",
                        s ->
                            s.embeddedSubProcess()
                                .startEvent()
                                .subProcess(
                                    "subsub1",
                                    ss ->
                                        ss.embeddedSubProcess()
                                            .startEvent()
                                            .serviceTask("A", t -> t.zeebeJobType("task"))
                                            .endEvent())
                                .endEvent())
                    .endEvent("end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent("start")
                    .subProcess(
                        "sub2",
                        s ->
                            s.embeddedSubProcess()
                                .startEvent()
                                .subProcess(
                                    "subsub2",
                                    ss ->
                                        ss.embeddedSubProcess()
                                            .startEvent()
                                            .serviceTask("B", t -> t.zeebeJobType("task"))
                                            .endEvent())
                                .endEvent())
                    .endEvent("end")
                    .done())
            .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .addMappingInstruction("sub1", "sub2")
        .addMappingInstruction("subsub1", "subsub2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SUB_PROCESS)
                .limit(2))
        .hasSize(2)
        .extracting(Record::getValue)
        .extracting(
            ProcessInstanceRecordValue::getElementId,
            ProcessInstanceRecordValue::getProcessDefinitionKey,
            ProcessInstanceRecordValue::getBpmnProcessId,
            ProcessInstanceRecordValue::getVersion)
        .containsSequence(
            tuple("sub2", targetProcessDefinitionKey, targetProcessId, 1),
            tuple("subsub2", targetProcessDefinitionKey, targetProcessId, 1));
  }
}
