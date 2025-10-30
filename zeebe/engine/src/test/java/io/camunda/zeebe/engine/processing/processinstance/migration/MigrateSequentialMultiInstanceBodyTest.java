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
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateSequentialMultiInstanceBodyTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldMigrateMultiInstanceServiceTask() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(
                        "serviceTask1",
                        t ->
                            t.zeebeJobTypeExpression("jobType")
                                .multiInstance(
                                    b ->
                                        b.sequential()
                                            .zeebeInputCollectionExpression("jobTypes")
                                            .zeebeInputElement("jobType")))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask(
                        "serviceTask2",
                        t ->
                            t.zeebeJobTypeExpression("jobType")
                                .multiInstance(
                                    b ->
                                        b.sequential()
                                            .zeebeInputCollectionExpression("jobTypes")
                                            .zeebeInputElement("jobType")))
                    .endEvent("multi_instance_target_process_end")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("jobTypes", Arrays.asList("a", "b", "c"))
            .create();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withType("a")
                .withElementId("serviceTask1")
                .findAny())
        .describedAs("Expect that first job in the multi-instance body is created")
        .isPresent();

    ENGINE.job().ofInstance(processInstanceKey).withType("a").complete();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withType("b")
                .withElementId("serviceTask1")
                .findAny())
        .describedAs("Expect that second job in the multi-instance body is created")
        .isPresent();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("serviceTask1", "serviceTask2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key is changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("serviceTask2")
        .describedAs("Expect that version number did not change")
        .hasVersion(1);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SERVICE_TASK)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key is changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("serviceTask2")
        .describedAs("Expect that version number did not change")
        .hasVersion(1);

    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasProcessDefinitionVersion(1)
        .describedAs("Expect that element id changed due to mapping")
        .hasElementId("serviceTask2")
        .describedAs(
            "Expect that the type did not change even though it's different in the target process."
                + " Re-evaluation of the job type expression is not enabled for this migration")
        .hasType("b");

    ENGINE.job().ofInstance(processInstanceKey).withType("b").complete();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withType("c")
                .withElementId("serviceTask2")
                .findAny())
        .describedAs("Expect that third job in the multi-instance body is created")
        .isPresent();

    ENGINE.job().ofInstance(processInstanceKey).withType("c").complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(targetProcessDefinitionKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("multi_instance_target_process_end")
                .findAny())
        .describedAs("Expect that the process instance is continued in the target process")
        .isPresent();
  }

  @Test
  public void shouldMigrateMultiInstanceReceiveTask() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final String sourceMessageName = helper.getMessageName();
    final String targetMessageName = helper.getMessageName() + 2;

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .receiveTask(
                        "receive1",
                        t ->
                            t.message(
                                    m ->
                                        m.name(sourceMessageName)
                                            .zeebeCorrelationKeyExpression("key"))
                                .multiInstance(
                                    b ->
                                        b.sequential()
                                            .zeebeInputCollectionExpression("keys")
                                            .zeebeInputElement("key")))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .receiveTask(
                        "receive2",
                        t ->
                            t.message(
                                    m ->
                                        m.name(targetMessageName)
                                            .zeebeCorrelationKeyExpression("key"))
                                .multiInstance(
                                    b ->
                                        b.sequential()
                                            .zeebeInputCollectionExpression("keys")
                                            .zeebeInputElement("key")))
                    .endEvent("multi_instance_target_process_end")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("keys", Arrays.asList("a", "b", "c"))
            .create();

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(sourceMessageName)
                .withCorrelationKey("a")
                .findAny())
        .describedAs("Expect that first message subscription in the multi-instance body is created")
        .isPresent();

    ENGINE.message().withName(sourceMessageName).withCorrelationKey("a").publish();

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(sourceMessageName)
                .withCorrelationKey("b")
                .findAny())
        .describedAs(
            "Expect that second message subscription in the multi-instance body is created")
        .isPresent();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("receive1", "receive2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key is changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("receive2")
        .describedAs("Expect that version number did not change")
        .hasVersion(1);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.RECEIVE_TASK)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key is changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("receive2")
        .describedAs("Expect that version number did not change")
        .hasVersion(1);

    Assertions.assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(sourceMessageName)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("receive2")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("b");

    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(sourceMessageName)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("b");

    ENGINE.message().withName(sourceMessageName).withCorrelationKey("b").publish();

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(targetMessageName) // msg sub is created with target message name
                .withCorrelationKey("c")
                .findAny())
        .describedAs(
            "Expect that third message subscription in the multi-instance body is created "
                + "but with the target message name")
        .isPresent();

    ENGINE.message().withName(targetMessageName).withCorrelationKey("c").publish();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(targetProcessDefinitionKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("multi_instance_target_process_end")
                .findAny())
        .describedAs("Expect that the process instance is continued in the target process")
        .isPresent();
  }

  @Test
  public void shouldMigrateMultiInstanceSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .subProcess(
                        "sub1",
                        sub ->
                            sub.multiInstance(
                                b ->
                                    b.sequential()
                                        .zeebeInputCollectionExpression("[1,2,3]")
                                        .zeebeInputElement("index")))
                    .embeddedSubProcess()
                    .startEvent()
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .endEvent()
                    .subProcessDone()
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .subProcess(
                        "sub2",
                        sub ->
                            sub.multiInstance(
                                b ->
                                    b.sequential()
                                        .zeebeInputCollectionExpression("[1,2,3]")
                                        .zeebeInputElement("index")))
                    .embeddedSubProcess()
                    .startEvent()
                    .serviceTask("B", t -> t.zeebeJobType("B"))
                    .endEvent()
                    .subProcessDone()
                    .endEvent("multi_instance_target_process_end")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withType("A")
                .withElementId("A")
                .skip(1)
                .getFirst()
                .getValue())
        .describedAs("Expect that second job in the multi-instance body is created")
        .isNotNull();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("sub1", "sub2")
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key is changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("sub2")
        .describedAs("Expect that version number did not change")
        .hasVersion(1);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SUB_PROCESS)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("sub2")
        .describedAs("Expect that version number did not change")
        .hasVersion(1);

    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasProcessDefinitionVersion(1)
        .describedAs("Expect that element id changed due to mapping")
        .hasElementId("B")
        .describedAs(
            "Expect that the type did not change even though it's different in the target process."
                + " Re-evaluation of the job type expression is not enabled for this migration")
        .hasType("A");

    final var secondJob =
        RecordingExporter.jobRecords(JobIntent.MIGRATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("A")
            .getFirst();

    ENGINE.job().ofInstance(processInstanceKey).withKey(secondJob.getKey()).complete();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withType("B") // new job is created with target job type
                .withElementId("B")
                .getFirst()
                .getValue())
        .describedAs(
            "Expect that third job in the multi-instance body is created with target job type")
        .isNotNull();

    final var thirdJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("B")
            .withElementId("B")
            .getFirst();

    ENGINE.job().ofInstance(processInstanceKey).withKey(thirdJob.getKey()).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(targetProcessDefinitionKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("multi_instance_target_process_end")
                .findAny())
        .describedAs("Expect that the process instance is continued in the target process")
        .isPresent();
  }

  @Test
  public void shouldMigrateMultiInstanceCallActivity() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final String childProcessId = helper.getBpmnProcessId() + "_source_child";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .callActivity(
                        "callActivity1",
                        c ->
                            c.zeebeProcessId(childProcessId)
                                .multiInstance(
                                    b ->
                                        b.sequential()
                                            .zeebeInputCollectionExpression("[1,2,3]")
                                            .zeebeInputElement("index")))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .callActivity(
                        "callActivity2",
                        c ->
                            c.zeebeProcessId(childProcessId)
                                .multiInstance(
                                    b ->
                                        b.sequential()
                                            .zeebeInputCollectionExpression("[1,2,3]")
                                            .zeebeInputElement("index")))
                    .endEvent("multi_instance_target_process_end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(childProcessId)
                    .startEvent()
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final var firstChildProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst()
            .getValue();

    ENGINE
        .job()
        .ofInstance(firstChildProcessInstance.getProcessInstanceKey())
        .withType("A")
        .complete();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withType("A")
                .withElementId("A")
                .skip(1)
                .getFirst()
                .getValue())
        .describedAs("Expect that second job in the multi-instance body is created")
        .isNotNull();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("callActivity1", "callActivity2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key is changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("callActivity2")
        .describedAs("Expect that version number did not change")
        .hasVersion(1);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.CALL_ACTIVITY)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key is changed")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("callActivity2")
        .describedAs("Expect that version number did not change")
        .hasVersion(1);

    final var secondChildProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .skip(1)
            .getFirst()
            .getValue();

    ENGINE
        .job()
        .ofInstance(secondChildProcessInstance.getProcessInstanceKey())
        .withType("A")
        .complete();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withType("A")
                .withElementId("A")
                .skip(2)
                .getFirst()
                .getValue())
        .describedAs("Expect that third job in the multi-instance body is created")
        .isNotNull();

    final var thirdChildProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .skip(2)
            .getFirst()
            .getValue();

    ENGINE
        .job()
        .ofInstance(thirdChildProcessInstance.getProcessInstanceKey())
        .withType("A")
        .complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(targetProcessDefinitionKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("multi_instance_target_process_end")
                .findAny())
        .describedAs("Expect that the process instance is continued in the target process")
        .isPresent();
  }

  @Test
  public void shouldResolveIncidentCausedByOutputElementWithMultiInstanceMigration() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(
                        "serviceTask1",
                        t ->
                            t.zeebeJobType("A")
                                .multiInstance(
                                    b ->
                                        b.sequential()
                                            .zeebeInputCollectionExpression("[1,2,3]")
                                            .zeebeInputElement("index")
                                            .zeebeOutputElementExpression("assert(x, x != null)")
                                            .zeebeOutputCollection("results")))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask(
                        "serviceTask2",
                        t ->
                            t.zeebeJobType("B")
                                .multiInstance(
                                    b ->
                                        b.sequential()
                                            .zeebeInputCollectionExpression("[1,2,3]")
                                            .zeebeInputElement("index")
                                            .zeebeOutputElementExpression("result")
                                            .zeebeOutputCollection("results")))
                    .endEvent("multi_instance_target_process_end")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final var job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("A")
            .getFirst();

    ENGINE.job().withKey(job.getKey()).complete();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("serviceTask1", "serviceTask2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.MIGRATED)
                .withRecordKey(incident.getKey())
                .getFirst()
                .getValue())
        .isNotNull();

    // after migrating, we can successfully resolve the incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    final var secondJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("B")
            .withElementId("serviceTask2")
            .getFirst();

    ENGINE.job().ofInstance(processInstanceKey).withKey(secondJob.getKey()).complete();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withType("B") // new job is created with target job type
                .withElementId("serviceTask2")
                .getFirst()
                .getValue())
        .describedAs(
            "Expect that third job in the multi-instance body is created with target job type")
        .isNotNull();

    final var thirdJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("B")
            .withElementId("serviceTask2")
            .skip(1)
            .getFirst();

    ENGINE.job().ofInstance(processInstanceKey).withKey(thirdJob.getKey()).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(targetProcessDefinitionKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("multi_instance_target_process_end")
                .findAny())
        .describedAs("Expect that the process instance is continued in the target process")
        .isPresent();
  }

  @Test
  public void shouldResolveIncidentCausedByCompletionConditionWithMultiInstanceMigration() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(
                        "serviceTask1",
                        t ->
                            t.zeebeJobType("A")
                                .multiInstance(
                                    b ->
                                        b.sequential()
                                            .zeebeInputCollectionExpression("[1,2,3]")
                                            .zeebeInputElement("index")
                                            .zeebeOutputElementExpression("result")
                                            .zeebeOutputCollection("results")
                                            .completionCondition("x > 3")))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask(
                        "serviceTask2",
                        t ->
                            t.zeebeJobType("B")
                                .multiInstance(
                                    b ->
                                        b.sequential()
                                            .zeebeInputCollectionExpression("[1,2,3]")
                                            .zeebeInputElement("index")
                                            .zeebeOutputElementExpression("result")
                                            .zeebeOutputCollection("results")
                                            .completionCondition(
                                                "= numberOfCompletedInstances = 3")))
                    .endEvent("multi_instance_target_process_end")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final var job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("A")
            .getFirst();

    ENGINE.job().withKey(job.getKey()).complete();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("serviceTask1", "serviceTask2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.MIGRATED)
                .withRecordKey(incident.getKey())
                .getFirst()
                .getValue())
        .isNotNull();

    // after migrating, we can successfully resolve the incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    final var secondJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("B")
            .withElementId("serviceTask2")
            .getFirst();

    ENGINE.job().ofInstance(processInstanceKey).withKey(secondJob.getKey()).complete();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withType("B") // new job is created with target job type
                .withElementId("serviceTask2")
                .getFirst()
                .getValue())
        .describedAs(
            "Expect that third job in the multi-instance body is created with target job type")
        .isNotNull();

    final var thirdJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("B")
            .withElementId("serviceTask2")
            .skip(1)
            .getFirst();

    ENGINE.job().ofInstance(processInstanceKey).withKey(thirdJob.getKey()).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withProcessDefinitionKey(targetProcessDefinitionKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("multi_instance_target_process_end")
                .findAny())
        .describedAs("Expect that the process instance is continued in the target process")
        .isPresent();
  }

  @Test
  public void shouldRaiseIncidentOnCompletionAfterChangingOutputCollection() {
    // given
    final String sourceProcessId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(sourceProcessId)
                    .startEvent()
                    .serviceTask(
                        "serviceTask1",
                        t ->
                            t.zeebeJobType("A")
                                .multiInstance(
                                    b ->
                                        b.sequential()
                                            .zeebeInputCollectionExpression("[1,2,3]")
                                            .zeebeInputElement("index")
                                            .zeebeOutputElementExpression("index")
                                            .zeebeOutputCollection("results")))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask(
                        "serviceTask2",
                        t ->
                            t.zeebeJobType("B")
                                .multiInstance(
                                    b ->
                                        b.sequential()
                                            .zeebeInputCollectionExpression("[1,2,3]")
                                            .zeebeInputElement("index")
                                            .zeebeOutputElementExpression("index")
                                            .zeebeOutputCollection("results2")))
                    .endEvent("multi_instance_target_process_end")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(sourceProcessId).create();

    final var job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("A")
            .getFirst();

    ENGINE.job().withKey(job.getKey()).complete();

    final var resultsVariable =
        RecordingExporter.variableRecords(VariableIntent.UPDATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("results")
            .withValue("[1,null,null]")
            .getFirst();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("serviceTask1", "serviceTask2")
        .migrate();

    // then
    // after migrating, we can complete the second job
    final var secondJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("A")
            .withElementId("serviceTask1")
            .skip(1)
            .getFirst();

    ENGINE.job().ofInstance(processInstanceKey).withKey(secondJob.getKey()).complete();

    // but that raises an incident
    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(incident.getValue())
        .hasErrorMessage(
            "Expected the output collection variable 'results2' to be of type list, but it was NIL");

    // we can resolve the incident by creating the new output collection variable 'results2'
    ENGINE
        .variables()
        .ofScope(resultsVariable.getValue().getScopeKey())
        .withDocument("{\"results2\": [1, null, null]}")
        .withUpdateSemantic(VariableDocumentUpdateSemantic.LOCAL)
        .update();

    // and when we resolve the incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then the new output collection can be filled correctly
    Assertions.assertThat(
            RecordingExporter.variableRecords(VariableIntent.UPDATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("results2")
                .getFirst()
                .getValue())
        .describedAs("Expect that the second entry is collected as output")
        .hasValue("[1,2,null]");
  }
}
