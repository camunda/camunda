/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.assertj.core.api.Assertions;
import org.assertj.core.groups.Tuple;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ModifyProcessInstanceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldWriteModifiedEventForProcessInstance() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var event =
        ENGINE.processInstance().withInstanceKey(processInstanceKey).modification().modify();

    // then
    assertThat(event)
        .hasKey(processInstanceKey)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(ProcessInstanceModificationIntent.MODIFIED);

    assertThat(event.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasNoActivateInstructions()
        .hasNoTerminateInstructions();
  }

  @Test
  public void shouldActivateRootElement() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .serviceTask("B", b -> b.zeebeJobType("B"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var processInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .modify();

    // then
    final var elementInstanceEvents =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withElementId("B")
            .withProcessInstanceKey(processInstanceKey)
            .limit("B", ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .toList();

    Assertions.assertThat(elementInstanceEvents)
        .extracting(Record::getIntent)
        .describedAs("Expect the element instance to have been activated")
        .containsExactly(
            ProcessInstanceIntent.ELEMENT_ACTIVATING, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    Assertions.assertThat(elementInstanceEvents)
        .extracting(Record::getKey)
        .describedAs("Expect each element instance event to refer to the same entity")
        .containsOnly(elementInstanceEvents.get(0).getKey());

    Assertions.assertThat(elementInstanceEvents)
        .extracting(Record::getValue)
        .describedAs("Expect each element instance event to contain the complete record value")
        .extracting(
            ProcessInstanceRecordValue::getBpmnProcessId,
            ProcessInstanceRecordValue::getProcessDefinitionKey,
            ProcessInstanceRecordValue::getProcessInstanceKey,
            ProcessInstanceRecordValue::getBpmnElementType,
            ProcessInstanceRecordValue::getElementId,
            ProcessInstanceRecordValue::getFlowScopeKey,
            ProcessInstanceRecordValue::getVersion,
            ProcessInstanceRecordValue::getParentProcessInstanceKey,
            ProcessInstanceRecordValue::getParentElementInstanceKey)
        .containsOnly(
            Tuple.tuple(
                PROCESS_ID,
                processInstance.getProcessDefinitionKey(),
                processInstanceKey,
                BpmnElementType.SERVICE_TASK,
                "B",
                processInstanceKey,
                processInstance.getVersion(),
                -1L,
                -1L));
  }

  @Test
  public void shouldActivateMultipleRootElement() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .parallelGateway()
                .serviceTask("B", b -> b.zeebeJobType("B"))
                .moveToLastGateway()
                .userTask("C")
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst()
        .getValue();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .activateElement("C")
        .modify();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .onlyEvents()
                .withElementId("B")
                .withProcessInstanceKey(processInstanceKey)
                .limit("B", ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .toList())
        .extracting(Record::getIntent)
        .describedAs("Expect the service task to have been activated")
        .containsExactly(
            ProcessInstanceIntent.ELEMENT_ACTIVATING, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .onlyEvents()
                .withElementId("C")
                .withProcessInstanceKey(processInstanceKey)
                .limit("C", ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .toList())
        .extracting(Record::getIntent)
        .describedAs("Expect the user task to have been activated")
        .containsExactly(
            ProcessInstanceIntent.ELEMENT_ACTIVATING, ProcessInstanceIntent.ELEMENT_ACTIVATED);
  }

  @Test
  public void shouldBeAbleToCompleteActivatedRootElement() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .serviceTask("B", b -> b.zeebeJobType("B"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .modify();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .onlyEvents()
                .withElementId("B")
                .withProcessInstanceKey(processInstanceKey)
                .limit("B", ProcessInstanceIntent.ELEMENT_COMPLETED))
        .describedAs("Expect the element instance to have been completed")
        .extracting(Record::getIntent)
        .containsSequence(
            ProcessInstanceIntent.ELEMENT_COMPLETING, ProcessInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldBeAbleToCompleteModfiedProcessInstance() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .serviceTask("B", b -> b.zeebeJobType("B"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .modify();

    // when
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(3)
        .map(Record::getKey)
        .forEach(jobKey -> ENGINE.job().withKey(jobKey).complete());

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .limitToProcessInstanceCompleted()
                .findAny())
        .describedAs("Expect the process instance to have been completed")
        .isPresent();
  }

  @Test
  public void shouldActivateInsideExistingFlowScope() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "subprocess",
                    sp ->
                        sp.embeddedSubProcess()
                            .startEvent()
                            .serviceTask("A", a -> a.zeebeJobType("A"))
                            .serviceTask("B", b -> b.zeebeJobType("B"))
                            .endEvent())
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var subProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SUB_PROCESS)
            .getFirst();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .modify();

    // then
    final var elementInstanceEvents =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withElementId("B")
            .withProcessInstanceKey(processInstanceKey)
            .limit("B", ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .toList();

    Assertions.assertThat(elementInstanceEvents)
        .extracting(Record::getIntent)
        .describedAs("Expect the element instance to have been activated")
        .containsExactly(
            ProcessInstanceIntent.ELEMENT_ACTIVATING, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    Assertions.assertThat(elementInstanceEvents)
        .extracting(Record::getKey)
        .describedAs("Expect each element instance event to refer to the same entity")
        .containsOnly(elementInstanceEvents.get(0).getKey());

    Assertions.assertThat(elementInstanceEvents)
        .extracting(Record::getValue)
        .describedAs("Expect each element instance event to contain the complete record value")
        .extracting(
            ProcessInstanceRecordValue::getBpmnProcessId,
            ProcessInstanceRecordValue::getProcessDefinitionKey,
            ProcessInstanceRecordValue::getProcessInstanceKey,
            ProcessInstanceRecordValue::getBpmnElementType,
            ProcessInstanceRecordValue::getElementId,
            ProcessInstanceRecordValue::getFlowScopeKey,
            ProcessInstanceRecordValue::getVersion,
            ProcessInstanceRecordValue::getParentProcessInstanceKey,
            ProcessInstanceRecordValue::getParentElementInstanceKey)
        .containsOnly(
            Tuple.tuple(
                PROCESS_ID,
                subProcessInstance.getValue().getProcessDefinitionKey(),
                subProcessInstance.getValue().getProcessInstanceKey(),
                BpmnElementType.SERVICE_TASK,
                "B",
                subProcessInstance.getKey(),
                subProcessInstance.getValue().getVersion(),
                -1L,
                -1L));
  }
}
