/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationActivateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.assertj.core.groups.Tuple;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class CreateProcessInstanceWithSuspensionInstructionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldSuspendProcessInstanceWhenElementIsCompleted() {
    // given
    final String processId = "process";
    final String elementToSuspend = "element";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .manualTask(elementToSuspend)
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withRuntimeSuspendInstruction(elementToSuspend)
            .create();

    // then
    final var result =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withProcessInstanceKey(processInstanceKey)
            .limit(processId, ProcessInstanceIntent.ELEMENT_SUSPENDED)
            .filter(
                record ->
                    record.getValue().getElementId().equals(processId)
                        || record.getValue().getElementId().equals(elementToSuspend));

    assertThat(result)
        .extracting(Record::getIntent, record -> record.getValue().getElementId())
        .containsSequence(
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_COMPLETED, elementToSuspend),
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_SUSPENDED, processId));

    final var processInstanceRecord =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_SUSPENDED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    ENGINE.writeRecords(
        RecordToWrite.command()
            .key(processInstanceKey)
            .processInstance(
                ProcessInstanceIntent.COMPLETE_ELEMENT, processInstanceRecord.getValue()));

    final var rejectedCommandRecord =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.COMPLETE_ELEMENT)
            .withProcessInstanceKey(processInstanceKey)
            .onlyCommandRejections()
            .getFirst();

    assertThat(rejectedCommandRecord).isNotNull();
  }

  @Test
  public void shouldSuspendProcessInstanceWhenMultipleInstructionsAndElementIsCompleted() {
    // given
    final String processId = "process";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .manualTask("manualTask1")
                .manualTask("manualTask2")
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withRuntimeSuspendInstruction("manualTask2")
            .withRuntimeSuspendInstruction("manualTask1")
            .create();

    // then
    final var result =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withProcessInstanceKey(processInstanceKey)
            .limit(processId, ProcessInstanceIntent.ELEMENT_SUSPENDED)
            .filter(
                record ->
                    record.getValue().getElementId().equals(processId)
                        || record.getValue().getElementId().equals("manualTask1"));

    assertThat(result)
        .extracting(Record::getIntent, record -> record.getValue().getElementId())
        .containsSequence(
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_COMPLETED, "manualTask1"),
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_SUSPENDED, processId));
  }

  @Test
  public void shouldCancelJobWhenProcessInstanceIsSuspended() {
    // given
    final String processId = "process";
    final String elementToSuspend = "element";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway()
                .serviceTask("task", t -> t.zeebeJobType("jobType"))
                .endEvent()
                .moveToLastGateway()
                .manualTask(elementToSuspend)
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withRuntimeSuspendInstruction(elementToSuspend)
            .create();

    // then
    final var result =
        RecordingExporter.jobRecords(JobIntent.CANCELED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(result.getValue().getElementId()).isEqualTo("task");
  }

  @Test
  public void shouldUnsubscribeFromEventsWhenProcessIsSuspended() {
    // given
    final String processId = "process";
    final String elementToSuspend = "element";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway()
                .intermediateCatchEvent(
                    "message",
                    e -> e.message(m -> m.name("my_message").zeebeCorrelationKey("=\"my_key\"")))
                .endEvent()
                .moveToLastGateway()
                .manualTask(elementToSuspend)
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withRuntimeSuspendInstruction(elementToSuspend)
            .create();

    // then
    final var result =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.DELETED)
            .withProcessInstanceKey(processInstanceKey)
            .withMessageName("my_message")
            .getFirst();

    assertThat(result.getValue().getElementId()).isEqualTo("message");
  }

  @Test
  public void shouldCancelUserTasksWhenProcessInstanceIsSuspended() {
    // given
    final String processId = "process";
    final String elementToSuspend = "element";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway()
                .userTask("userTask", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .moveToLastGateway()
                .manualTask(elementToSuspend)
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withRuntimeSuspendInstruction(elementToSuspend)
            .create();

    // then
    final var result =
        RecordingExporter.userTaskRecords(UserTaskIntent.CANCELED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(result.getValue().getElementId()).isEqualTo("userTask");
  }

  @Test
  public void shouldResolveIncidentsWhenProcessInstanceIsSuspended() {
    // given
    final String processId = "process";
    final String elementToSuspend = "element";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway()
                // invalid timer expression will trigger an incident
                .intermediateCatchEvent(
                    "timer", e -> e.timerWithDurationExpression("not.a.valid.expr"))
                .endEvent()
                .moveToLastGateway()
                .manualTask(elementToSuspend)
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withRuntimeSuspendInstruction(elementToSuspend)
            .create();

    // then
    final var result =
        RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(result.getValue().getElementId()).isEqualTo("timer");
  }

  @Test
  public void shouldBePossibleToCancelSuspendedProcessInstance() {
    final String processId = "process";
    final String elementToSuspend = "element";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway()
                .task("task1")
                .task("task2")
                .task("task3")
                .task("task4")
                .endEvent()
                .moveToLastGateway()
                .manualTask(elementToSuspend)
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withRuntimeSuspendInstruction(elementToSuspend)
            .create();

    // then
    final var result =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withProcessInstanceKey(processInstanceKey)
            .limit(processId, ProcessInstanceIntent.ELEMENT_SUSPENDED)
            .filter(record -> record.getValue().getElementId().equals(processId));

    assertThat(result)
        .extracting(Record::getIntent, record -> record.getValue().getElementId())
        .contains(Tuple.tuple(ProcessInstanceIntent.ELEMENT_SUSPENDED, processId));

    final var processInstanceRecord =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_SUSPENDED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    ENGINE.writeRecords(
        RecordToWrite.command()
            .key(processInstanceKey)
            .processInstance(ProcessInstanceIntent.CANCEL, processInstanceRecord.getValue()));

    final var terminatedEvent =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withProcessInstanceKey(processInstanceKey)
            .limit(processId, ProcessInstanceIntent.ELEMENT_TERMINATED)
            .getFirst();

    assertThat(terminatedEvent).isNotNull();
  }

  @Test
  public void shouldNotAllowToMigrateSuspendedProcessInstance() {
    // given
    final String processId = "process";
    final String elementToSuspend = "element";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .manualTask(elementToSuspend)
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withRuntimeSuspendInstruction(elementToSuspend)
            .create();

    final long updatedProcessDefinitionKey =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .manualTask(elementToSuspend)
                    .manualTask("anotherManualTask")
                    .endEvent()
                    .done())
            .deploy()
            .getKey();

    // when
    final var migrationRecord =
        new ProcessInstanceMigrationRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setTargetProcessDefinitionKey(updatedProcessDefinitionKey);

    ENGINE.writeRecords(RecordToWrite.command().key(processInstanceKey).migration(migrationRecord));

    // then
    final var rejectedCommandRecord =
        RecordingExporter.processInstanceMigrationRecords(ProcessInstanceMigrationIntent.MIGRATE)
            .withProcessInstanceKey(processInstanceKey)
            .onlyCommandRejections()
            .getFirst();

    assertThat(rejectedCommandRecord).isNotNull();
  }

  @Test
  public void shouldNotAllowToModifySuspendedProcessInstance() {
    // given
    final String processId = "process";
    final String elementToSuspend = "element";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .manualTask(elementToSuspend)
                .manualTask("anotherManualTask")
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withRuntimeSuspendInstruction(elementToSuspend)
            .create();

    // when
    final var modification =
        new ProcessInstanceModificationRecord()
            .setProcessInstanceKey(processInstanceKey)
            .addActivateInstruction(
                new ProcessInstanceModificationActivateInstruction()
                    .setElementId("anotherManualTask"));
    ENGINE.writeRecords(RecordToWrite.command().key(processInstanceKey).modification(modification));

    // then
    final var rejectedCommandRecord =
        RecordingExporter.processInstanceModificationRecords(
                ProcessInstanceModificationIntent.MODIFY)
            .withProcessInstanceKey(processInstanceKey)
            .onlyCommandRejections()
            .getFirst();

    assertThat(rejectedCommandRecord).isNotNull();
  }

  @Test
  public void shouldSuspendWhenInterruptingBoundaryEventActivated() {
    // given
    final String processId = "process";
    final String elementToSuspend = "element";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(elementToSuspend, t -> t.zeebeJobType("jobType"))
                .boundaryEvent("boundary")
                .cancelActivity(true)
                .message(
                    messageBuilder ->
                        messageBuilder.name("myMessage").zeebeCorrelationKey("=\"myKey\""))
                .endEvent()
                .moveToActivity(elementToSuspend)
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withRuntimeSuspendInstruction(elementToSuspend)
            .create();

    // when
    ENGINE
        .message()
        .withName("myMessage")
        .withCorrelationKey("myKey")
        .withTimeToLive(1000)
        .publish();

    // then
    final var result =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .onlyEvents()
            .limit(elementToSuspend, ProcessInstanceIntent.ELEMENT_TERMINATED)
            .filter(
                record ->
                    record.getValue().getElementId().equals(processId)
                        || record.getValue().getElementId().equals(elementToSuspend));

    assertThat(result)
        .extracting(Record::getIntent, record -> record.getValue().getElementId())
        .containsSequence(
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_TERMINATING, elementToSuspend),
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_SUSPENDED, processId),
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_TERMINATED, elementToSuspend));
  }

  @Test
  public void shouldNotSuspendWhenNonInterruptingBoundaryEventIsActivated() {
    // given
    final String processId = "process";
    final String elementToSuspend = "element";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(elementToSuspend, t -> t.zeebeJobType("jobType"))
                .boundaryEvent("boundary")
                .cancelActivity(false)
                .message(
                    messageBuilder ->
                        messageBuilder.name("myMessage").zeebeCorrelationKey("=\"myKey\""))
                .endEvent()
                .moveToActivity(elementToSuspend)
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withRuntimeSuspendInstruction(elementToSuspend)
            .create();

    // when
    ENGINE
        .message()
        .withName("myMessage")
        .withCorrelationKey("myKey")
        .withTimeToLive(1000)
        .publish();

    // then
    final var result =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .limit("boundary", ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(processId, ProcessInstanceIntent.ELEMENT_SUSPENDED)
            .filter(record -> record.getValue().getElementId().equals(processId));

    assertThat(result)
        .extracting(Record::getIntent, record -> record.getValue().getElementId())
        .doesNotContain(Tuple.tuple(ProcessInstanceIntent.ELEMENT_SUSPENDED, processId));
  }
}
