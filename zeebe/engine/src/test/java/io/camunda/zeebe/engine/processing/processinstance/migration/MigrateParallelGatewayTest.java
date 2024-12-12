/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance.migration;

import static io.camunda.zeebe.engine.processing.processinstance.migration.MigrationTestUtil.extractProcessDefinitionKeyByProcessId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateParallelGatewayTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldMigrateParallelGatewayWithIncident() {
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "_target";

    final String executionListenerJobType = "executionListenerJobType";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(sourceProcessId)
                    .startEvent()
                    .parallelGateway("parallel1")
                    .zeebeStartExecutionListener(executionListenerJobType)
                    .endEvent("end1")
                    .moveToLastGateway()
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .parallelGateway("parallel2")
                    .zeebeStartExecutionListener(executionListenerJobType)
                    .endEvent("end2")
                    .moveToLastGateway()
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(sourceProcessId).create();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(executionListenerJobType)
        .withRetries(0)
        .fail();

    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("parallel1")
            .getFirst();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("parallel1", "parallel2")
        .migrate();

    RecordingExporter.incidentRecords(IncidentIntent.MIGRATED)
        .withRecordKey(incident.getKey())
        .await();

    // then
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(executionListenerJobType)
        .withRetries(1)
        .updateRetries();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();
    ENGINE.job().ofInstance(processInstanceKey).withType(executionListenerJobType).complete();

    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.END_EVENT))
        .extracting(r -> r.getValue().getElementId())
        .describedAs(
            "Expected to successfully evaluate execution listener job type and resolve incident")
        .contains("end2");
  }

  @Test
  public void shouldMigrateJoiningParallelGatewayWithOneSequenceFlowTaken() {
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "_v2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(sourceProcessId)
                    .startEvent()
                    .parallelGateway("fork")
                    .serviceTask("task1", b -> b.zeebeJobType("type1"))
                    .sequenceFlowId("flow1")
                    .parallelGateway("join1")
                    .endEvent()
                    .moveToNode("fork")
                    .serviceTask("task2", b -> b.zeebeJobType("type2"))
                    .connectTo("join1")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .parallelGateway("fork")
                    .serviceTask("task1", b -> b.zeebeJobType("type1"))
                    .sequenceFlowId("flow1")
                    .parallelGateway("join2")
                    .endEvent()
                    .moveToNode("fork")
                    .serviceTask("task3", b -> b.zeebeJobType("type3"))
                    .connectTo("join2")
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(sourceProcessId).create();

    AssertionsForInterfaceTypes.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SERVICE_TASK)
                .limit(2))
        .hasSize(2);

    ENGINE.job().ofInstance(processInstanceKey).withType("type1").complete();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("flow1")
                .getFirst()
                .getValue())
        .describedAs("Expected to take the sequence flow to the joining gateway")
        .isNotNull();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("task2", "task3")
        .addMappingInstruction("join1", "join2")
        .migrate();

    // then
    ENGINE.job().ofInstance(processInstanceKey).withType("type2").complete();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PARALLEL_GATEWAY)
                .withElementId("join2")
                .getFirst()
                .getValue())
        .describedAs("Expected to activate the joining parallel gateway")
        .isNotNull();
  }

  @Test
  public void shouldMigrateJoiningParallelGatewayWithMultipleSequenceFlowsTaken() {
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "_v2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(sourceProcessId)
                    .startEvent()
                    .parallelGateway("fork")
                    .serviceTask("task1", b -> b.zeebeJobType("type1"))
                    .sequenceFlowId("flow1")
                    .parallelGateway("join1")
                    .endEvent()
                    .moveToNode("fork")
                    .serviceTask("task2", b -> b.zeebeJobType("type2"))
                    .sequenceFlowId("flow2")
                    .connectTo("join1")
                    .moveToNode("fork")
                    .serviceTask("task3", b -> b.zeebeJobType("type3"))
                    .connectTo("join1")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .parallelGateway("fork")
                    .serviceTask("task1", b -> b.zeebeJobType("type1"))
                    .sequenceFlowId("flow1")
                    .parallelGateway("join2")
                    .endEvent()
                    .moveToNode("fork")
                    .serviceTask("task2", b -> b.zeebeJobType("type2"))
                    .sequenceFlowId("flow2")
                    .connectTo("join2")
                    .moveToNode("fork")
                    .serviceTask("task4", b -> b.zeebeJobType("type4"))
                    .connectTo("join2")
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(sourceProcessId).create();

    AssertionsForInterfaceTypes.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SERVICE_TASK)
                .limit(2))
        .hasSize(2);

    ENGINE.job().ofInstance(processInstanceKey).withType("type1").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("type2").complete();

    assertThat(
            RecordingExporter.records()
                .skipUntil(
                    r ->
                        r.getValue() instanceof ProcessInstanceRecord
                            && ((ProcessInstanceRecord) r.getValue()).getElementId().equals("task1")
                            && r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED)
                .processInstanceRecords()
                .withIntent(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SEQUENCE_FLOW)
                .limit(2))
        .extracting(r -> r.getValue().getElementId())
        .describedAs("Expected to take the sequence flows to the joining gateway")
        .contains("flow1", "flow2");

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("task3", "task4")
        .addMappingInstruction("join1", "join2")
        .migrate();

    // then
    ENGINE.job().ofInstance(processInstanceKey).withType("type3").complete();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PARALLEL_GATEWAY)
                .withElementId("join2")
                .getFirst()
                .getValue())
        .describedAs("Expected to activate the joining parallel gateway")
        .isNotNull();
  }

  @Test
  public void shouldMigrateJoiningParallelGatewayInsideSubprocess() {
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "_v2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(sourceProcessId)
                    .startEvent()
                    .subProcess(
                        "sub1",
                        s ->
                            s.embeddedSubProcess()
                                .startEvent()
                                .parallelGateway("fork")
                                .serviceTask("task1", b -> b.zeebeJobType("type1"))
                                .sequenceFlowId("flow1")
                                .parallelGateway("join1")
                                .endEvent()
                                .moveToNode("fork")
                                .serviceTask("task2", b -> b.zeebeJobType("type2"))
                                .sequenceFlowId("flow2")
                                .connectTo("join1")
                                .moveToNode("fork")
                                .serviceTask("task3", b -> b.zeebeJobType("type3"))
                                .connectTo("join1"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .subProcess(
                        "sub2",
                        s ->
                            s.embeddedSubProcess()
                                .startEvent()
                                .parallelGateway("fork")
                                .serviceTask("task1", b -> b.zeebeJobType("type1"))
                                .sequenceFlowId("flow1")
                                .parallelGateway("join2")
                                .endEvent()
                                .moveToNode("fork")
                                .serviceTask("task2", b -> b.zeebeJobType("type2"))
                                .sequenceFlowId("flow2")
                                .connectTo("join2")
                                .moveToNode("fork")
                                .serviceTask("task4", b -> b.zeebeJobType("type4"))
                                .connectTo("join2"))
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(sourceProcessId).create();

    AssertionsForInterfaceTypes.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SERVICE_TASK)
                .limit(2))
        .hasSize(2);

    ENGINE.job().ofInstance(processInstanceKey).withType("type1").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("type2").complete();

    assertThat(
            RecordingExporter.records()
                .skipUntil(
                    r ->
                        r.getValue() instanceof ProcessInstanceRecord
                            && ((ProcessInstanceRecord) r.getValue()).getElementId().equals("task1")
                            && r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED)
                .processInstanceRecords()
                .withIntent(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SEQUENCE_FLOW)
                .limit(2))
        .extracting(r -> r.getValue().getElementId())
        .describedAs("Expected to take the sequence flows to the joining gateway")
        .contains("flow1", "flow2");

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("sub1", "sub2")
        .addMappingInstruction("task3", "task4")
        .addMappingInstruction("join1", "join2")
        .migrate();

    // then
    ENGINE.job().ofInstance(processInstanceKey).withType("type3").complete();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PARALLEL_GATEWAY)
                .withElementId("join2")
                .getFirst()
                .getValue())
        .describedAs("Expected to activate the joining parallel gateway")
        .isNotNull();
  }
}
