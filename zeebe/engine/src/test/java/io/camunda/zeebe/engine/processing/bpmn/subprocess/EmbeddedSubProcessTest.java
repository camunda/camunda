/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.subprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.EmbeddedSubProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.EndEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class EmbeddedSubProcessTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process-with-sub-process";

  private static final BpmnModelInstance NO_TASK_SUB_PROCESS =
      processWithSubProcess(subProcess -> subProcess.startEvent().endEvent());

  private static final BpmnModelInstance ONE_TASK_SUB_PROCESS =
      processWithSubProcess(
          subProcess ->
              subProcess.startEvent().serviceTask("task", b -> b.zeebeJobType("task")).endEvent());

  private static final BpmnModelInstance PARALLEL_TASKS_SUB_PROCESS =
      processWithSubProcess(
          subProcess ->
              subProcess
                  .startEvent()
                  .parallelGateway("fork")
                  .serviceTask("task-1", b -> b.zeebeJobType("task-1"))
                  .sequenceFlowId("join-1")
                  .parallelGateway("join")
                  .moveToNode("fork")
                  .serviceTask("task-2", b -> b.zeebeJobType("task-2"))
                  .sequenceFlowId("join-2")
                  .connectTo("join")
                  .endEvent());

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static BpmnModelInstance processWithSubProcess(
      final Consumer<EmbeddedSubProcessBuilder> subProcessBuilder) {
    return processWithSubProcessBuilder(subProcessBuilder).done();
  }

  private static EndEventBuilder processWithSubProcessBuilder(
      final Consumer<EmbeddedSubProcessBuilder> subProcessBuilder) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .subProcess(
            "sub-process", subProcess -> subProcessBuilder.accept(subProcess.embeddedSubProcess()))
        .endEvent();
  }

  @Test
  public void shouldActivateSubProcess() {
    // given
    ENGINE.deployment().withXmlResource(NO_TASK_SUB_PROCESS).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.SEQUENCE_FLOW, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED));

    final var subProcessActivating =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SUB_PROCESS)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .getFirst();

    Assertions.assertThat(subProcessActivating.getValue())
        .hasFlowScopeKey(processInstanceKey)
        .hasElementId("sub-process");
  }

  @Test
  public void shouldContainTreePathOnActivatingSubProcess() {
    // given
    ENGINE.deployment().withXmlResource(NO_TASK_SUB_PROCESS).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.SEQUENCE_FLOW, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED));

    final var subProcessActivating =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SUB_PROCESS)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .getFirst();

    final Record<ProcessInstanceRecordValue> startEvent =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.START_EVENT)
            .withFlowScopeKey(subProcessActivating.getKey())
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .getFirst();

    assertTreePathIsPopulated(subProcessActivating, processInstanceKey);
    assertTreePathIsPopulated(startEvent, processInstanceKey, subProcessActivating.getKey());
  }

  @Test
  public void shouldActivateSubProcessWithCustomTenant() {
    // given
    final String tenantId = "foo";
    ENGINE.deployment().withXmlResource(NO_TASK_SUB_PROCESS).withTenantId(tenantId).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(tenantId).create();

    // then
    final var subProcessActivating =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SUB_PROCESS)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withTenantId(tenantId)
            .getFirst();

    Assertions.assertThat(subProcessActivating.getValue())
        .hasFlowScopeKey(processInstanceKey)
        .hasElementId("sub-process")
        .hasTenantId(tenantId);
  }

  @Test
  public void shouldTerminateSubProcessWithNonInterruptingBoundaryEvent() {
    // given
    final var model =
        processWithSubProcess(
            subProcess -> {
              subProcess
                  .startEvent()
                  .serviceTask("task-1", b -> b.zeebeJobType("task-1"))
                  .endEvent()
                  .subProcessDone()
                  .boundaryEvent(
                      "boundary",
                      b -> b.cancelActivity(false).timerWithDuration("PT15S").endEvent());
            });

    ENGINE.deployment().withXmlResource(model).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .await();
    ENGINE.increaseTime(Duration.ofMinutes(1));
    RecordingExporter.processInstanceRecords()
        .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withElementType(BpmnElementType.BOUNDARY_EVENT)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldTerminateSubProcessWithNonInterruptingEventSubProcess() {
    // given
    final var model =
        processWithSubProcess(
            subProcess -> {
              subProcess
                  .eventSubProcess()
                  .startEvent()
                  .interrupting(false)
                  .timerWithDuration("PT15S")
                  .endEvent();

              subProcess
                  .startEvent()
                  .serviceTask("task-1", b -> b.zeebeJobType("task-1"))
                  .endEvent();
            });

    ENGINE.deployment().withXmlResource(model).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .await();
    ENGINE.increaseTime(Duration.ofMinutes(1));
    RecordingExporter.processInstanceRecords()
        .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldCompleteSubProcess() {
    // given
    ENGINE.deployment().withXmlResource(NO_TASK_SUB_PROCESS).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SEQUENCE_FLOW, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ACTIVATE_ELEMENT));

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .limitToProcessInstanceCompleted())
        .extracting(Record::getIntent)
        .contains(ProcessInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldCreateJobForInnerTask() {
    // given
    ENGINE.deployment().withXmlResource(ONE_TASK_SUB_PROCESS).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final var serviceTaskActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(JobIntent.CREATED)
            .getFirst();

    Assertions.assertThat(jobCreated.getValue())
        .hasElementId("task")
        .hasElementInstanceKey(serviceTaskActivated.getKey())
        .hasBpmnProcessId(serviceTaskActivated.getValue().getBpmnProcessId())
        .hasProcessDefinitionVersion(serviceTaskActivated.getValue().getVersion())
        .hasProcessDefinitionKey(serviceTaskActivated.getValue().getProcessDefinitionKey());
  }

  @Test
  public void shouldTerminateSubProcess() {
    // given
    ENGINE.deployment().withXmlResource(ONE_TASK_SUB_PROCESS).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldInterruptSubProcess() {
    // given
    final var process =
        processWithSubProcess(
            subProcess ->
                subProcess
                    .startEvent()
                    .serviceTask("task", t -> t.zeebeJobType("task"))
                    .endEvent()
                    .subProcessDone()
                    .boundaryEvent(
                        "cancel",
                        b -> b.message(m -> m.name("cancel").zeebeCorrelationKeyExpression("key")))
                    .endEvent());

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "key-1").create();

    RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .await();

    // when
    ENGINE.message().withName("cancel").withCorrelationKey("key-1").publish();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED));

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .limitToProcessInstanceCompleted())
        .extracting(Record::getIntent)
        .contains(ProcessInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldCompleteNestedSubProcess() {
    // given
    final Consumer<SubProcessBuilder> nestedSubProcess =
        subProcess -> subProcess.embeddedSubProcess().startEvent().endEvent();

    final BpmnModelInstance process =
        processWithSubProcess(
            subProcess ->
                subProcess
                    .startEvent()
                    .subProcess("nestedSubProcess", nestedSubProcess)
                    .endEvent());

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCompleteSubProcessWithParallelFlow() {
    // given
    final var process =
        processWithSubProcess(
            subProcess ->
                subProcess
                    .startEvent()
                    .parallelGateway("fork")
                    .serviceTask("task-1", b -> b.zeebeJobType("task-1"))
                    .endEvent()
                    .moveToLastGateway()
                    .serviceTask("task-2", b -> b.zeebeJobType("task-2"))
                    .endEvent());

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("task-1").complete();

    RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.END_EVENT)
        .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .await();

    ENGINE.job().ofInstance(processInstanceKey).withType("task-2").complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.PARALLEL_GATEWAY, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldTerminateSubProcessWithParallelFlow() {
    // given
    ENGINE.deployment().withXmlResource(PARALLEL_TASKS_SUB_PROCESS).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(2)
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .onlyEvents()
                .limitToProcessInstanceTerminated())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldTerminateSubProcessWithPendingParallelGateway() {
    // given
    ENGINE.deployment().withXmlResource(PARALLEL_TASKS_SUB_PROCESS).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.job().ofInstance(processInstanceKey).withType("task-1").complete();

    // await that one sequence flow on the joining parallel gateway is taken
    RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("join-1")
        .withIntent(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .onlyEvents()
                .limitToProcessInstanceTerminated())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldNotOverrideVariablesOnCompleteSubProcess() {
    // given a process instance waiting for a job inside a sub process
    ENGINE
        .deployment()
        .withXmlResource(
            processWithSubProcessBuilder(
                    subprocess ->
                        subprocess
                            .startEvent()
                            .serviceTask("task", b -> b.zeebeJobType("task"))
                            .endEvent())
                .moveToActivity("sub-process")
                .boundaryEvent(
                    "msg-boundary",
                    boundary ->
                        boundary
                            .cancelActivity(false)
                            .message(msg -> msg.name("foo").zeebeCorrelationKeyExpression("bar")))
                .endEvent("msg-end")
                .done())
        .deploy();
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("bar", "bar").create();
    final var job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // and a message with variables is correlated to the non-interrupting boundary event
    ENGINE.message().withName("foo").withCorrelationKey("bar").withVariables("{\"x\":1}").publish();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("msg-end")
        .await();

    // and we update the variables
    ENGINE.variables().ofScope(processInstanceKey).withDocument("{\"x\":2}").update();
    RecordingExporter.variableRecords(VariableIntent.UPDATED)
        .withProcessInstanceKey(processInstanceKey)
        .withName("x")
        .withValue("2")
        .await();

    // when we complete the job
    ENGINE.job().withKey(job.getKey()).complete();

    // then the variable is overridden
    assertThat(
            RecordingExporter.records()
                .betweenProcessInstance(processInstanceKey)
                .variableRecords()
                .withName("x")
                .withScopeKey(processInstanceKey))
        .extracting(var -> tuple(var.getIntent(), var.getValue().getValue()))
        .containsExactly(tuple(VariableIntent.CREATED, "1"), tuple(VariableIntent.UPDATED, "2"));
  }

  private static void assertTreePathIsPopulated(
      final Record<ProcessInstanceRecordValue> elementInstanceRecord,
      final long... parentElementInstanceKeys) {
    final ProcessInstanceRecordValue elementInstanceValue = elementInstanceRecord.getValue();
    assertThat(elementInstanceValue.getCallingElementPath()).isEmpty();
    assertThat(elementInstanceValue.getElementInstancePath()).hasSize(1);

    final List<Long> expectedElementInstancePath = new ArrayList<>();
    Arrays.stream(parentElementInstanceKeys).boxed().forEach(expectedElementInstancePath::add);
    expectedElementInstancePath.add(elementInstanceRecord.getKey());

    final List<Long> elementInstances = elementInstanceValue.getElementInstancePath().getFirst();
    assertThat(elementInstances).containsExactlyElementsOf(expectedElementInstancePath);
    assertThat(elementInstanceValue.getProcessDefinitionPath())
        .containsExactly(elementInstanceValue.getProcessDefinitionKey());
  }
}
