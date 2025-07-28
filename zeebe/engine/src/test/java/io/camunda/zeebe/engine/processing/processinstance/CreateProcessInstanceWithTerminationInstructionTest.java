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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.assertj.core.groups.Tuple;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class CreateProcessInstanceWithTerminationInstructionTest {

  @ClassRule
  public static final EngineRule ENGINE = EngineRule.singlePartition().maxCommandsInBatch(1);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldTerminateProcessInstanceWhenElementIsCompleted() {
    // given
    final String processId = "process";
    final String elementBeforeTermination = "element";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .manualTask(elementBeforeTermination)
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withRuntimeTerminateInstruction(elementBeforeTermination)
            .create();

    // then
    final var result =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withProcessInstanceKey(processInstanceKey)
            .limit(processId, ProcessInstanceIntent.ELEMENT_TERMINATED)
            .filter(
                record ->
                    record.getValue().getElementId().equals(processId)
                        || record.getValue().getElementId().equals(elementBeforeTermination));

    assertThat(result)
        .extracting(Record::getIntent, record -> record.getValue().getElementId())
        .containsSequence(
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_COMPLETED, elementBeforeTermination),
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_TERMINATING, processId));

    final var processInstanceRecord =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
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
  public void shouldTerminateWhenInterruptingBoundaryEventActivated() {
    // given
    final String processId = "process";
    final String elementBeforeTermination = "element";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(elementBeforeTermination, t -> t.zeebeJobType("jobType"))
                .boundaryEvent("boundary")
                .cancelActivity(true)
                .message(
                    messageBuilder ->
                        messageBuilder.name("myMessage").zeebeCorrelationKey("=\"myOtherKey\""))
                .endEvent()
                .moveToActivity(elementBeforeTermination)
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withRuntimeTerminateInstruction(elementBeforeTermination)
            .create();

    // when
    ENGINE.message().withName("myMessage").withCorrelationKey("myOtherKey").publish();

    // then
    final var result =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withProcessInstanceKey(processInstanceKey)
            .limit(processId, ProcessInstanceIntent.ELEMENT_TERMINATED)
            .filter(
                record ->
                    record.getValue().getElementId().equals(processId)
                        || record.getValue().getElementId().equals(elementBeforeTermination));

    assertThat(result)
        .extracting(Record::getIntent, record -> record.getValue().getElementId())
        .containsSequence(
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_TERMINATING, elementBeforeTermination),
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_TERMINATED, elementBeforeTermination),
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_TERMINATING, processId),
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_TERMINATED, processId));
  }

  @Test
  public void shouldNotTerminateWhenNonInterruptingBoundaryEventIsActivated() {
    // given
    final String processId = "process";
    final String elementBeforeTermination = "element";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(elementBeforeTermination, t -> t.zeebeJobType("jobType"))
                .boundaryEvent("boundary")
                .cancelActivity(false)
                .message(
                    messageBuilder ->
                        messageBuilder.name("myMessage").zeebeCorrelationKey("=\"myKey\""))
                .endEvent()
                .moveToActivity(elementBeforeTermination)
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withRuntimeTerminateInstruction(elementBeforeTermination)
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
            .withProcessInstanceKey(processInstanceKey)
            .limit("boundary", ProcessInstanceIntent.ELEMENT_COMPLETED)
            .filter(record -> record.getValue().getElementId().equals(processId));

    assertThat(result)
        .extracting(Record::getIntent, record -> record.getValue().getElementId())
        .doesNotContain(Tuple.tuple(ProcessInstanceIntent.ELEMENT_TERMINATING, processId))
        .doesNotContain(Tuple.tuple(ProcessInstanceIntent.ELEMENT_TERMINATED, processId));
  }

  @Test
  public void shouldTerminateWhenElementIsInNestedEmbeddedSubprocess() {
    // given
    final String processId = "process";
    final String elementBeforeTermination = "element";
    final String boundaryEventNestedSubProcessEnd = "boundaryNestedSubProcessEnd";
    final String boundaryEventId = "boundary";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess("firstSubProcess")
                .embeddedSubProcess()
                .startEvent()
                .subProcess("nestedSubProcess")
                .embeddedSubProcess()
                .startEvent()
                .serviceTask(elementBeforeTermination, t -> t.zeebeJobType("jobType"))
                .subProcessDone()
                .boundaryEvent("boundary")
                .cancelActivity(true)
                .message(
                    messageBuilder ->
                        messageBuilder
                            .name("myMessage")
                            .zeebeCorrelationKey("=\"myCorrelationKey\""))
                .endEvent(boundaryEventNestedSubProcessEnd)
                .moveToActivity("nestedSubProcess")
                .endEvent("nestedSubProcessEnd")
                .subProcessDone()
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withRuntimeTerminateInstruction(elementBeforeTermination)
            .create();

    // when
    ENGINE.message().withName("myMessage").withCorrelationKey("myCorrelationKey").publish();

    // then
    final var result =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withProcessInstanceKey(processInstanceKey)
            .limit(processId, ProcessInstanceIntent.ELEMENT_TERMINATED)
            .filter(
                record ->
                    record.getValue().getElementId().equals(processId)
                        || record.getValue().getElementId().equals(elementBeforeTermination)
                        || record.getValue().getElementId().equals(boundaryEventNestedSubProcessEnd)
                        || record.getValue().getElementId().equals(boundaryEventId));

    assertThat(result)
        .describedAs(
            "Expect boundary event to be the last activated element before process termination")
        .extracting(Record::getIntent, record -> record.getValue().getElementId())
        .containsSequence(
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_TERMINATING, elementBeforeTermination),
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_TERMINATED, elementBeforeTermination),
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_ACTIVATING, boundaryEventId),
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_ACTIVATED, boundaryEventId),
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_TERMINATING, processId),
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_TERMINATING, boundaryEventId),
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_TERMINATED, boundaryEventId),
            Tuple.tuple(ProcessInstanceIntent.ELEMENT_TERMINATED, processId))
        .doesNotContain(
            Tuple.tuple(
                ProcessInstanceIntent.ELEMENT_ACTIVATING, boundaryEventNestedSubProcessEnd));
  }
}
