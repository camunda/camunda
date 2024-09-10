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
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
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
                    .endEvent()
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

    assertThat(
            RecordingExporter.jobRecords(JobIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withType("a")
                .withElementId("serviceTask2")
                .findAny())
        .describedAs("Expect that one of the service tasks in the multi-instance body is completed")
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
                    .endEvent()
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

    Assertions.assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(sourceMessageName)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .hasElementId("receive2")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("a");

    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(sourceMessageName)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("a");
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
                    .endEvent("multi_instance_child_process_end")
                    .subProcessDone()
                    .endEvent()
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
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

    engine.job().ofInstance(processInstanceKey).withType("A").complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("multi_instance_child_process_end")
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
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(childProcessId)
                    .startEvent()
                    .serviceTask("A", t -> t.zeebeJobType("A"))
                    .endEvent("multi_instance_child_process_end")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementId("A")
                .limit(3))
        .describedAs("Wait until all service tasks have activated")
        .hasSize(3);

    final var childProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId("A")
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

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

    engine.job().ofInstance(childProcessInstanceKey).withType("A").complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(childProcessInstanceKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("multi_instance_child_process_end")
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
                    .endEvent()
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

    assertThat(
            RecordingExporter.jobRecords(JobIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withType("b")
                .withElementId("serviceTask2")
                .findAny())
        .describedAs("Expect that one of the service tasks in the multi-instance body is completed")
        .isPresent();
  }
}
