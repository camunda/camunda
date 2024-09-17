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
import static org.assertj.core.groups.Tuple.tuple;

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
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateMultiInstanceBodyTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldMigrateParallelMultiInstanceServiceTask() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
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
                                        b.zeebeInputCollectionExpression("jobTypes")
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
                                        b.zeebeInputCollectionExpression("jobTypes")
                                            .zeebeInputElement("jobType")))
                    .endEvent("multi_instance_target_process_end")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("jobTypes", Arrays.asList("a", "b", "c"))
            .create();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .hasSize(3)
        .extracting(Record::getValue)
        .extracting(JobRecordValue::getType)
        .containsExactly("a", "b", "c");

    // when
    engine
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

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SERVICE_TASK)
                .limit(3))
        .extracting(Record::getValue)
        .extracting(
            r ->
                tuple(
                    r.getProcessDefinitionKey(),
                    r.getBpmnProcessId(),
                    r.getElementId(),
                    r.getVersion()))
        .containsExactly(
            tuple(targetProcessDefinitionKey, targetProcessId, "serviceTask2", 1),
            tuple(targetProcessDefinitionKey, targetProcessId, "serviceTask2", 1),
            tuple(targetProcessDefinitionKey, targetProcessId, "serviceTask2", 1));

    assertThat(
            RecordingExporter.jobRecords(JobIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .extracting(Record::getValue)
        .extracting(
            r ->
                tuple(
                    r.getProcessDefinitionKey(),
                    r.getBpmnProcessId(),
                    r.getElementId(),
                    r.getType()))
        .containsExactly(
            tuple(targetProcessDefinitionKey, targetProcessId, "serviceTask2", "a"),
            tuple(targetProcessDefinitionKey, targetProcessId, "serviceTask2", "b"),
            tuple(targetProcessDefinitionKey, targetProcessId, "serviceTask2", "c"));

    engine.job().ofInstance(processInstanceKey).withType("a").complete();
    engine.job().ofInstance(processInstanceKey).withType("b").complete();
    engine.job().ofInstance(processInstanceKey).withType("c").complete();

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
  public void shouldMigrateParallelMultiInstanceReceiveTask() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final String sourceMessageName = helper.getMessageName();
    final String targetMessageName = helper.getMessageName() + 2;

    final var deployment =
        engine
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
                                        b.zeebeInputCollectionExpression("keys")
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
                                        b.zeebeInputCollectionExpression("keys")
                                            .zeebeInputElement("key")))
                    .endEvent("multi_instance_target_process_end")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("keys", Arrays.asList("a", "b", "c"))
            .create();

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .hasSize(3)
        .extracting(Record::getValue)
        .extracting(MessageSubscriptionRecordValue::getCorrelationKey)
        .containsExactly("a", "b", "c");

    // when
    engine
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

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.RECEIVE_TASK)
                .limit(3))
        .extracting(Record::getValue)
        .extracting(
            r ->
                tuple(
                    r.getProcessDefinitionKey(),
                    r.getBpmnProcessId(),
                    r.getElementId(),
                    r.getVersion()))
        .containsExactly(
            tuple(targetProcessDefinitionKey, targetProcessId, "receive2", 1),
            tuple(targetProcessDefinitionKey, targetProcessId, "receive2", 1),
            tuple(targetProcessDefinitionKey, targetProcessId, "receive2", 1));

    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(sourceMessageName)
                .limit(3))
        .extracting(Record::getValue)
        .extracting(r -> tuple(r.getBpmnProcessId(), r.getElementId(), r.getCorrelationKey()))
        .containsExactly(
            tuple(targetProcessId, "receive2", "a"),
            tuple(targetProcessId, "receive2", "b"),
            tuple(targetProcessId, "receive2", "c"));

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(sourceMessageName)
                .limit(3))
        .extracting(Record::getValue)
        .extracting(r -> tuple(r.getBpmnProcessId(), r.getCorrelationKey()))
        .containsExactly(
            tuple(targetProcessId, "a"), tuple(targetProcessId, "b"), tuple(targetProcessId, "c"));

    engine.message().withName(sourceMessageName).withCorrelationKey("a").publish();
    engine.message().withName(sourceMessageName).withCorrelationKey("b").publish();
    engine.message().withName(sourceMessageName).withCorrelationKey("c").publish();

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
  public void shouldMigrateParallelMultiInstanceSubprocess() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .subProcess(
                        "sub1",
                        sub ->
                            sub.multiInstance(
                                b ->
                                    b.zeebeInputCollectionExpression("[1,2,3]")
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
                                    b.zeebeInputCollectionExpression("[1,2,3]")
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

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("A")
                .limit(3))
        .describedAs("Wait until all service tasks have activated")
        .hasSize(3);

    // when
    engine
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

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SUB_PROCESS)
                .limit(3))
        .extracting(Record::getValue)
        .extracting(
            r ->
                tuple(
                    r.getProcessDefinitionKey(),
                    r.getBpmnProcessId(),
                    r.getElementId(),
                    r.getVersion()))
        .containsExactly(
            tuple(targetProcessDefinitionKey, targetProcessId, "sub2", 1),
            tuple(targetProcessDefinitionKey, targetProcessId, "sub2", 1),
            tuple(targetProcessDefinitionKey, targetProcessId, "sub2", 1));

    assertThat(
            RecordingExporter.jobRecords(JobIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .extracting(Record::getValue)
        .extracting(
            r ->
                tuple(
                    r.getProcessDefinitionKey(),
                    r.getBpmnProcessId(),
                    r.getElementId(),
                    r.getType()))
        .containsExactly(
            tuple(targetProcessDefinitionKey, targetProcessId, "B", "A"),
            tuple(targetProcessDefinitionKey, targetProcessId, "B", "A"),
            tuple(targetProcessDefinitionKey, targetProcessId, "B", "A"));

    final var jobs =
        RecordingExporter.jobRecords(JobIntent.MIGRATED)
            .withType("A")
            .withProcessInstanceKey(processInstanceKey)
            .limit(3)
            .toList();

    jobs.forEach(job -> engine.job().withKey(job.getKey()).complete());

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
  public void shouldMigrateParallelMultiInstanceCallActivity() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final String childProcessId = helper.getBpmnProcessId() + "_source_child";

    final var deployment =
        engine
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
                                        b.zeebeInputCollectionExpression("[1,2,3]")
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
                                        b.zeebeInputCollectionExpression("[1,2,3]")
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

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withParentProcessInstanceKey(processInstanceKey)
                .withElementId("A")
                .limit(3))
        .describedAs("Wait until all service tasks have activated")
        .hasSize(3);

    final var childProcessInstances =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .limit(3)
            .toList();

    // when
    engine
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

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.CALL_ACTIVITY)
                .limit(3))
        .extracting(Record::getValue)
        .extracting(
            r ->
                tuple(
                    r.getProcessDefinitionKey(),
                    r.getBpmnProcessId(),
                    r.getElementId(),
                    r.getVersion()))
        .containsExactly(
            tuple(targetProcessDefinitionKey, targetProcessId, "callActivity2", 1),
            tuple(targetProcessDefinitionKey, targetProcessId, "callActivity2", 1),
            tuple(targetProcessDefinitionKey, targetProcessId, "callActivity2", 1));

    childProcessInstances.forEach(
        instance ->
            engine
                .job()
                .ofInstance(instance.getValue().getProcessInstanceKey())
                .withType("A")
                .complete());

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
  public void shouldMigratePartiallyCompleteParallelMultiInstance() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
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
                                        b.zeebeInputCollectionExpression("jobTypes")
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
                                        b.zeebeInputCollectionExpression("jobTypes")
                                            .zeebeInputElement("jobType")))
                    .endEvent("multi_instance_target_process_end")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("jobTypes", Arrays.asList("a", "b", "c"))
            .create();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .hasSize(3)
        .extracting(Record::getValue)
        .extracting(JobRecordValue::getType)
        .containsExactly("a", "b", "c");

    engine.job().ofInstance(processInstanceKey).withType("a").complete();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withType("a")
                .withElementId("serviceTask1")
                .findAny())
        .describedAs("Expect that one of the service tasks in the multi-instance body is completed")
        .isPresent();

    // when
    engine
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

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SERVICE_TASK)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            r ->
                tuple(
                    r.getProcessDefinitionKey(),
                    r.getBpmnProcessId(),
                    r.getElementId(),
                    r.getVersion()))
        .containsExactly(
            tuple(targetProcessDefinitionKey, targetProcessId, "serviceTask2", 1),
            tuple(targetProcessDefinitionKey, targetProcessId, "serviceTask2", 1));

    assertThat(
            RecordingExporter.jobRecords(JobIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            r ->
                tuple(
                    r.getProcessDefinitionKey(),
                    r.getBpmnProcessId(),
                    r.getElementId(),
                    r.getType()))
        .containsExactly(
            tuple(targetProcessDefinitionKey, targetProcessId, "serviceTask2", "b"),
            tuple(targetProcessDefinitionKey, targetProcessId, "serviceTask2", "c"));

    engine.job().ofInstance(processInstanceKey).withType("b").complete();
    engine.job().ofInstance(processInstanceKey).withType("c").complete();

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
        engine
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
                                        b.zeebeInputCollectionExpression("[1,2,3]")
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
                            t.zeebeJobType("A")
                                .multiInstance(
                                    b ->
                                        b.zeebeInputCollectionExpression("[1,2,3]")
                                            .zeebeInputElement("index")
                                            .zeebeOutputElementExpression("result")
                                            .zeebeOutputCollection("results")))
                    .endEvent("multi_instance_target_process_end")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final var jobs =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("A")
            .limit(3)
            .toList();

    engine.job().withKey(jobs.getFirst().getKey()).complete();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    engine
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
    engine.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // and complete the two still active jobs without problems
    jobs.stream().skip(1).forEach(job -> engine.job().withKey(job.getKey()).complete());

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
        engine
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
                                        b.zeebeInputCollectionExpression("[1,2,3]")
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
                            t.zeebeJobType("A")
                                .multiInstance(
                                    b ->
                                        b.zeebeInputCollectionExpression("[1,2,3]")
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

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final var jobs =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("A")
            .limit(3)
            .toList();

    engine.job().withKey(jobs.getFirst().getKey()).complete();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    engine
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
    engine.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // and complete the two still active jobs without problems
    jobs.stream().skip(1).forEach(job -> engine.job().withKey(job.getKey()).complete());

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
        engine
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
                                        b.zeebeInputCollectionExpression("[1,2,3]")
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
                            t.zeebeJobType("A")
                                .multiInstance(
                                    b ->
                                        b.zeebeInputCollectionExpression("[1,2,3]")
                                            .zeebeInputElement("index")
                                            .zeebeOutputElementExpression("index")
                                            .zeebeOutputCollection("results2")))
                    .endEvent("multi_instance_target_process_end")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(sourceProcessId).create();

    final var jobs =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("A")
            .limit(3)
            .toList();

    engine.job().withKey(jobs.getFirst().getKey()).complete();

    final var resultsVariable =
        RecordingExporter.variableRecords(VariableIntent.UPDATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("results")
            .withValue("[1,null,null]")
            .getFirst();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("serviceTask1", "serviceTask2")
        .migrate();

    // then
    // after migrating, we can complete one of the still active jobs
    engine.job().withKey(jobs.get(1).getKey()).complete();

    // but that raises an incident
    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(incident.getValue())
        .hasErrorMessage(
            "Expected the output collection variable 'results2' to be of type list, but it was NIL");

    // we can resolve the incident by creating the new output collection variable 'results2'
    engine
        .variables()
        .ofScope(resultsVariable.getValue().getScopeKey())
        .withDocument("{\"results2\": [1, null, null]}")
        .withUpdateSemantic(VariableDocumentUpdateSemantic.LOCAL)
        .update();

    // and when we resolve the incident
    engine.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

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
