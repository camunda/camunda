/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.multiinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.CallActivityBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.JsonUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class MultiInstanceCallActivityTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID_PARENT = "wf-parent";
  private static final String PROCESS_ID_CHILD = "wf-child";
  private static final String CALL_ACTIVITY_ID = "call";

  private static final String INPUT_COLLECTION_VARIABLE = "items";
  private static final List<Integer> INPUT_COLLECTION = List.of(10, 20, 30);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();
  private String jobType;

  private static BpmnModelInstance parentProcessWithCallActivity(
      final Consumer<CallActivityBuilder> callActivityBuilder) {
    final CallActivityBuilder process =
        Bpmn.createExecutableProcess(PROCESS_ID_PARENT)
            .startEvent()
            .callActivity(CALL_ACTIVITY_ID, c -> c.zeebeProcessId(PROCESS_ID_CHILD));

    callActivityBuilder.accept(process);

    return process.endEvent().done();
  }

  @Before
  public void init() {
    jobType = helper.getJobType();

    final var childProcess =
        Bpmn.createExecutableProcess(PROCESS_ID_CHILD)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();

    final var parentProcess =
        parentProcessWithCallActivity(
            callActivity ->
                callActivity.multiInstance(
                    b -> b.zeebeInputCollectionExpression(INPUT_COLLECTION_VARIABLE)));

    ENGINE
        .deployment()
        .withXmlResource("wf-parent.bpmn", parentProcess)
        .withXmlResource("wf-child.bpmn", childProcess)
        .deploy();
  }

  @Test
  public void shouldCreateChildInstanceForEachElement() {
    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID_PARENT)
            .withVariable(INPUT_COLLECTION_VARIABLE, INPUT_COLLECTION)
            .create();

    final List<Long> callActivityInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.CALL_ACTIVITY)
            .limit(INPUT_COLLECTION.size())
            .map(Record::getKey)
            .collect(Collectors.toList());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withParentProcessInstanceKey(processInstanceKey)
                .withBpmnProcessId(PROCESS_ID_CHILD)
                .filterRootScope()
                .limit(INPUT_COLLECTION.size()))
        .extracting(r -> r.getValue().getParentElementInstanceKey())
        .containsExactly(
            callActivityInstanceKey.get(0),
            callActivityInstanceKey.get(1),
            callActivityInstanceKey.get(2));
  }

  @Test
  public void shouldCompleteBodyWhenAllChildInstancesAreCompleted() {
    // given
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID_PARENT)
            .withVariable(INPUT_COLLECTION_VARIABLE, INPUT_COLLECTION)
            .create();

    // when
    awaitJobsCreated(INPUT_COLLECTION.size());

    ENGINE
        .jobs()
        .withType(jobType)
        .activate()
        .getValue()
        .getJobKeys()
        .forEach(jobKey -> ENGINE.job().withKey(jobKey).complete());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withParentProcessInstanceKey(processInstanceKey)
                .filterRootScope()
                .limit(INPUT_COLLECTION.size())
                .count())
        .describedAs("Expected child process instances to be completed")
        .isEqualTo(INPUT_COLLECTION.size());

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType())
        .containsSequence(
            BpmnElementType.CALL_ACTIVITY,
            BpmnElementType.CALL_ACTIVITY,
            BpmnElementType.CALL_ACTIVITY,
            BpmnElementType.MULTI_INSTANCE_BODY,
            BpmnElementType.END_EVENT,
            BpmnElementType.PROCESS);
  }

  @Test
  public void shouldCancelChildInstancesOnTermination() {
    // given
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID_PARENT)
            .withVariable(INPUT_COLLECTION_VARIABLE, INPUT_COLLECTION)
            .create();

    awaitJobsCreated(INPUT_COLLECTION.size());

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withParentProcessInstanceKey(processInstanceKey)
                .filterRootScope()
                .limit(INPUT_COLLECTION.size())
                .count())
        .describedAs("Expected child process instances to be terminated")
        .isEqualTo(INPUT_COLLECTION.size());

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> r.getValue().getBpmnElementType())
        .containsExactly(
            BpmnElementType.CALL_ACTIVITY,
            BpmnElementType.CALL_ACTIVITY,
            BpmnElementType.CALL_ACTIVITY,
            BpmnElementType.MULTI_INSTANCE_BODY,
            BpmnElementType.PROCESS);
  }

  @Test
  public void shouldCollectOutputFromChildInstance() {
    // given
    final BpmnModelInstance parentProcess =
        parentProcessWithCallActivity(
            callActivity ->
                callActivity
                    .zeebeOutputExpression("x", "result")
                    .multiInstance(
                        b ->
                            b.zeebeInputCollectionExpression(INPUT_COLLECTION_VARIABLE)
                                .zeebeOutputElementExpression("result")
                                .zeebeOutputCollection("results")));

    ENGINE.deployment().withXmlResource("wf-parent.bpmn", parentProcess).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID_PARENT)
            .withVariable(INPUT_COLLECTION_VARIABLE, INPUT_COLLECTION)
            .create();

    // when
    awaitJobsCreated(INPUT_COLLECTION.size());

    final var jobCounter = new AtomicInteger();

    ENGINE
        .jobs()
        .withType(jobType)
        .activate()
        .getValue()
        .getJobKeys()
        .forEach(
            jobKey ->
                ENGINE
                    .job()
                    .withKey(jobKey)
                    .withVariable("x", jobCounter.incrementAndGet())
                    .complete());

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
        .await();

    // then
    final var expectedOutputCollection =
        JsonUtil.toJson(
            IntStream.rangeClosed(1, INPUT_COLLECTION.size()).boxed().collect(Collectors.toList()));

    assertThat(
            RecordingExporter.records()
                .betweenProcessInstance(processInstanceKey)
                .variableRecords()
                .withName("results")
                .withScopeKey(processInstanceKey))
        .extracting(r -> r.getValue().getValue())
        .containsExactly(expectedOutputCollection);
  }

  private void awaitJobsCreated(final int size) {
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).limit(size).count())
        .describedAs("Expected %d jobs to be created", size)
        .isEqualTo(size);
  }
}
