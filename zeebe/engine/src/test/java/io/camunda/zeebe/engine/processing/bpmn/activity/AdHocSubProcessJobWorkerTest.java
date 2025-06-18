/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AdHocSubProcessBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAdHocImplementationType;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultAdHocSubProcess;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class AdHocSubProcessJobWorkerTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String AD_HOC_SUB_PROCESS_ELEMENT_ID = "ad-hoc";
  private static final String JOB_TYPE = "routing-agent";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  private BpmnModelInstance process(final Consumer<AdHocSubProcessBuilder> modifier) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .adHocSubProcess(
            AD_HOC_SUB_PROCESS_ELEMENT_ID,
            adHocSubProcess -> {
              adHocSubProcess
                  .zeebeImplementation(ZeebeAdHocImplementationType.JOB_WORKER)
                  .zeebeJobType(JOB_TYPE);
              modifier.accept(adHocSubProcess);
            })
        .endEvent()
        .done();
  }

  @Test
  public void shouldDeployProcess() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess
                  .zeebeImplementation(ZeebeAdHocImplementationType.JOB_WORKER)
                  .zeebeJobType(JOB_TYPE)
                  .zeebeJobRetries("5")
                  .zeebeTaskHeader("header", "jobWorkerConfig")
                  .zeebeInputExpression("1", "jobWorkerVariable");

              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
              adHocSubProcess.task("C");
            });

    // when
    final Record<DeploymentRecordValue> deploymentEvent =
        ENGINE.deployment().withXmlResource(process).deploy();

    // then
    assertThat(deploymentEvent).hasRecordType(RecordType.EVENT).hasIntent(DeploymentIntent.CREATED);
  }

  @Test
  public void shouldCreateJobOnActivation() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
              adHocSubProcess.task("C");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED));

    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(jobCreated.getValue())
        .hasJobKind(JobKind.AD_HOC_SUB_PROCESS)
        .hasElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
        .hasType(JOB_TYPE);
  }

  @Test
  public void shouldActivateElementsWithJobCompletion() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
              adHocSubProcess.task("C");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    completeAdHocSubProcessJob(
        processInstanceKey,
        0,
        adHocSubProcess -> {
          adHocSubProcess.activateElements().add().setElementId("A");
          adHocSubProcess.activateElements().add().setElementId("C");
        });

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit("C", ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("A", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("C", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldCreateJobOnElementCompletion() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
              adHocSubProcess.task("C");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    completeAdHocSubProcessJob(
        processInstanceKey,
        0,
        adHocSubProcess -> adHocSubProcess.activateElements().add().setElementId("A"));

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit("A", ProcessInstanceIntent.ELEMENT_COMPLETED))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED));

    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .skip(1)
            .getFirst();
    assertThat(jobCreated.getValue())
        .hasJobKind(JobKind.AD_HOC_SUB_PROCESS)
        .hasElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
        .hasType(JOB_TYPE);
  }

  @Test
  public void shouldCreateAndCompleteJobs() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
              adHocSubProcess.task("C");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    completeAdHocSubProcessJob(
        processInstanceKey,
        0,
        adHocSubProcess -> adHocSubProcess.activateElements().add().setElementId("A"));

    completeAdHocSubProcessJob(
        processInstanceKey,
        1,
        adHocSubProcess -> adHocSubProcess.activateElements().add().setElementId("B"));

    completeAdHocSubProcessJob(
        processInstanceKey,
        2,
        adHocSubProcess -> adHocSubProcess.activateElements().add().setElementId("C"));

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit("C", ProcessInstanceIntent.ELEMENT_COMPLETED))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("B", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("B", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("C", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("C", ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCancelJobOnCreatingNewJob() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
              adHocSubProcess.task("C");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    completeAdHocSubProcessJob(
        processInstanceKey,
        0,
        adHocSubProcess -> {
          adHocSubProcess.activateElements().add().setElementId("A");
          adHocSubProcess.activateElements().add().setElementId("B");
        });

    // then
    Assertions.assertThat(
            RecordingExporter.jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withType(JOB_TYPE)
                .limit(5))
        .extracting(Record::getIntent)
        .containsSequence(
            JobIntent.CREATED, // on ad-hoc sub-process: ACTIVATED
            JobIntent.COMPLETED, // job worker: activate elements [A,B]
            JobIntent.CREATED, // on element A: COMPLETED
            JobIntent.CANCELED, // <<-- expected cancellation
            JobIntent.CREATED // on element B: COMPLETED
            );
  }

  @Test
  public void shouldActivateElementsWithVariables() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
              adHocSubProcess.task("C");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    completeAdHocSubProcessJob(
        processInstanceKey,
        0,
        adHocSubProcess -> {
          adHocSubProcess
              .activateElements()
              .add()
              .setElementId("A")
              .setVariables(MsgPackUtil.asMsgPack(Map.of("a", 1)));

          adHocSubProcess
              .activateElements()
              .add()
              .setElementId("B")
              .setVariables(MsgPackUtil.asMsgPack(Map.of("b", 2)));
        });

    // then
    final var elementA =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst()
            .getValue();

    final var elementB =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("B")
            .getFirst()
            .getValue();

    Assertions.assertThat(
            RecordingExporter.variableRecords().withProcessInstanceKey(processInstanceKey).limit(3))
        .extracting(Record::getValue)
        .extracting(
            VariableRecordValue::getName,
            VariableRecordValue::getValue,
            VariableRecordValue::getScopeKey)
        .contains(
            tuple("a", "1", elementA.getFlowScopeKey()),
            tuple("b", "2", elementB.getFlowScopeKey()));

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS_INNER_INSTANCE)
                .limit(2))
        .extracting(Record::getKey, r -> r.getValue().getBpmnElementType())
        .contains(
            tuple(elementA.getFlowScopeKey(), BpmnElementType.AD_HOC_SUB_PROCESS_INNER_INSTANCE),
            tuple(elementB.getFlowScopeKey(), BpmnElementType.AD_HOC_SUB_PROCESS_INNER_INSTANCE));
  }

  @Test
  public void shouldCompleteAdHocSubProcess() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
              adHocSubProcess.task("C");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    completeAdHocSubProcessJob(
        processInstanceKey,
        0,
        adHocSubProcess -> adHocSubProcess.setCompletionConditionFulfilled(true));

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCompleteAdHocSubProcessAndCancelRemainingInstances() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.task("A");
              adHocSubProcess.serviceTask("B", t -> t.zeebeJobType("B"));
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    completeAdHocSubProcessJob(
        processInstanceKey,
        0,
        adHocSubProcess -> {
          adHocSubProcess.activateElements().add().setElementId("A");
          adHocSubProcess.activateElements().add().setElementId("B");
        });

    completeAdHocSubProcessJob(
        processInstanceKey,
        1,
        adHocSubProcess ->
            adHocSubProcess
                .setCompletionConditionFulfilled(true)
                .setCancelRemainingInstances(true));

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("B", ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  private static void completeAdHocSubProcessJob(
      final long processInstanceKey,
      final int skip,
      final Consumer<JobResultAdHocSubProcess> adHocSubProcessProperties) {
    final JobResultAdHocSubProcess adHocSubProcess = new JobResultAdHocSubProcess();
    adHocSubProcessProperties.accept(adHocSubProcess);
    final var jobResult = new JobResult().setAdHocSubProcess(adHocSubProcess);

    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withType(JOB_TYPE)
            .withProcessInstanceKey(processInstanceKey)
            .skip(skip)
            .getFirst()
            .getKey();

    ENGINE.job().withKey(jobKey).withType(JOB_TYPE).withResult(jobResult).complete();
  }
}
