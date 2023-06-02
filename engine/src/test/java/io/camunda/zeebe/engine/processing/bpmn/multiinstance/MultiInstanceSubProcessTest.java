/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.multiinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.client.PublishMessageClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.EventSubProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class MultiInstanceSubProcessTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  public static final String TASK_ELEMENT_ID = "task";
  private static final String PROCESS_ID = "process";
  private static final String SUB_PROCESS_ELEMENT_ID = "sub-process";
  private static final String JOB_TYPE = "test";
  private static final String INPUT_COLLECTION = "items";
  private static final String INPUT_ELEMENT = "item";

  private static final BpmnModelInstance EMPTY_SUB_PROCESS =
      process(b -> b.sequenceFlowId("sub-process-to-end"));

  private static final BpmnModelInstance SERVICE_TASK_SUB_PROCESS =
      process(b -> b.serviceTask(TASK_ELEMENT_ID, t -> t.zeebeJobType(JOB_TYPE)));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static BpmnModelInstance process(final Consumer<StartEventBuilder> subProcessBuilder) {
    final StartEventBuilder process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                SUB_PROCESS_ELEMENT_ID,
                s ->
                    s.multiInstance(
                        b ->
                            b.zeebeInputCollectionExpression(INPUT_COLLECTION)
                                .zeebeInputElement(INPUT_ELEMENT)))
            .embeddedSubProcess()
            .startEvent("sub-process-start");

    subProcessBuilder.accept(process);

    return process.endEvent("sub-process-end").done();
  }

  @Test
  public void shouldActivateStartEventForEachElement() {
    // given
    ENGINE.deployment().withXmlResource(EMPTY_SUB_PROCESS).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, Arrays.asList(10, 20, 30))
            .create();

    final List<Long> subProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(SUB_PROCESS_ELEMENT_ID)
            .skip(1)
            .limit(3)
            .map(Record::getKey)
            .collect(Collectors.toList());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementId("sub-process-start")
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .extracting(r -> r.getValue().getFlowScopeKey())
        .containsExactly(
            subProcessInstanceKey.get(0),
            subProcessInstanceKey.get(1),
            subProcessInstanceKey.get(2));
  }

  @Test
  public void shouldActivateAllElementsOfSubProcess() {
    // given
    ENGINE.deployment().withXmlResource(EMPTY_SUB_PROCESS).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, Arrays.asList(10, 20, 30))
            .create();

    // then
    final long subProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId(SUB_PROCESS_ELEMENT_ID)
            .skip(1)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .onlyEvents()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withFlowScopeKey(subProcessInstanceKey))
        .extracting(r -> tuple(r.getValue().getElementId(), r.getIntent()))
        .containsExactly(
            tuple("sub-process-start", ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple("sub-process-start", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("sub-process-start", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("sub-process-start", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("sub-process-to-end", ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("sub-process-end", ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple("sub-process-end", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("sub-process-end", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("sub-process-end", ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCancelSubProcessOnTermination() {
    // given
    ENGINE.deployment().withXmlResource(SERVICE_TASK_SUB_PROCESS).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, Arrays.asList(10, 20, 30))
            .create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(3)
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> r.getValue().getBpmnElementType())
        .containsExactly(
            BpmnElementType.SERVICE_TASK,
            BpmnElementType.SUB_PROCESS,
            BpmnElementType.SERVICE_TASK,
            BpmnElementType.SUB_PROCESS,
            BpmnElementType.SERVICE_TASK,
            BpmnElementType.SUB_PROCESS,
            BpmnElementType.MULTI_INSTANCE_BODY,
            BpmnElementType.PROCESS);
  }

  @Test
  public void shouldCreateJobForEachSubProcess() {
    // given
    ENGINE.deployment().withXmlResource(SERVICE_TASK_SUB_PROCESS).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, Arrays.asList(10, 20, 30))
            .create();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .hasSize(3);

    // and
    final JobBatchRecordValue jobActivation =
        ENGINE.jobs().withType(JOB_TYPE).activate().getValue();

    jobActivation.getJobKeys().forEach(jobKey -> ENGINE.job().withKey(jobKey).complete());

    // then
    assertThat(jobActivation.getJobs())
        .extracting(j -> j.getVariables().get(INPUT_ELEMENT))
        .containsExactly(10, 20, 30);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .filterRootScope()
                .limitToProcessInstanceCompleted())
        .extracting(Record::getIntent)
        .containsExactly(ProcessInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldCreateMessageSubscriptionForEachSubProcess() {
    // given
    final BpmnModelInstance process =
        process(
            b ->
                b.intermediateCatchEvent()
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression(INPUT_ELEMENT)));

    ENGINE.deployment().withXmlResource(process).deploy();

    final List<String> inputCollection = Arrays.asList("a", "b", "c");
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, inputCollection)
            .create();

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .hasSize(3)
        .extracting(r -> r.getValue().getCorrelationKey())
        .containsExactly("a", "b", "c");

    // and
    inputCollection.forEach(
        element -> ENGINE.message().withName("message").withCorrelationKey(element).publish());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .filterRootScope()
                .limitToProcessInstanceCompleted())
        .extracting(Record::getIntent)
        .containsExactly(ProcessInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldCreateTimerForEachSubProcess() {
    // given
    final BpmnModelInstance process =
        process(b -> b.intermediateCatchEvent("timer").timerWithDuration("PT1S"));

    ENGINE.deployment().withXmlResource(process).deploy();

    final List<String> inputCollection = Arrays.asList("a", "b", "c");
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, inputCollection)
            .create();

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .hasSize(3)
        .extracting(r -> r.getValue().getTargetElementId())
        .containsOnly("timer");

    // and
    ENGINE.getClock().addTime(Duration.ofSeconds(1));

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .filterRootScope()
                .limitToProcessInstanceCompleted())
        .extracting(Record::getIntent)
        .containsExactly(ProcessInstanceIntent.ELEMENT_COMPLETED);
  }

  /** Regression test for bug: https://github.com/camunda/zeebe/issues/11578 */
  @Test
  public void shouldCorrelateMessagesToEventSubProcessForEachSubProcess() {
    // given
    final Consumer<EventSubProcessBuilder> eventSubprocess =
        s ->
            s.startEvent()
                .message(m -> m.name("msg").zeebeCorrelationKeyExpression(INPUT_ELEMENT + ".id"))
                .endEvent();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "subprocess",
                    s ->
                        s.multiInstance(
                                m ->
                                    m.parallel()
                                        .zeebeInputCollectionExpression(INPUT_COLLECTION)
                                        .zeebeInputElement(INPUT_ELEMENT))
                            .embeddedSubProcess()
                            .eventSubProcess("msg-subprocess", eventSubprocess)
                            .startEvent()
                            .userTask("task")
                            .endEvent())
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(
                INPUT_COLLECTION, Arrays.asList(Map.of("id", 1), Map.of("id", 2), Map.of("id", 3)))
            .create();

    // when
    Stream.of("1", "2", "3")
        .map(correlationKey -> ENGINE.message().withName("msg").withCorrelationKey(correlationKey))
        .forEach(PublishMessageClient::publish);

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .describedAs("Expect that each message is correlated")
        .hasSize(3);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .limitToProcessInstanceCompleted())
        .describedAs("Expect that each sub process is interrupted so process could complete")
        .hasSize(1);
  }
}
