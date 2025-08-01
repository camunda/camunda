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
import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAdHocImplementationType;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultActivateElement;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class JobBasedAdHocSubProcessTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  public static final String JOB_TYPE = "jobType";
  private static final String PROCESS_ID = "process";
  private static final String AHSP_ELEMENT_ID = "ad-hoc";
  private static final String AHSP_INNER_ELEMENT_ID =
      "ad-hoc" + ZeebeConstants.AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX;
  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  private BpmnModelInstance process(final Consumer<AdHocSubProcessBuilder> modifier) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .adHocSubProcess(AHSP_ELEMENT_ID, modifier)
        .zeebeImplementation(ZeebeAdHocImplementationType.JOB_WORKER)
        .zeebeJobType(JOB_TYPE)
        .endEvent()
        .done();
  }

  @Test
  public void shouldDeployProcess() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.task("A1").task("A2");
              adHocSubProcess.task("B");
            });

    // when
    final Record<DeploymentRecordValue> deploymentEvent =
        ENGINE.deployment().withXmlResource(process).deploy();

    // then
    assertThat(deploymentEvent).hasRecordType(RecordType.EVENT).hasIntent(DeploymentIntent.CREATED);
  }

  @Test
  public void shouldActivateAdHocSubProcess() {
    // given
    final BpmnModelInstance process = process(adHocSubProcess -> adHocSubProcess.task("A"));

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(AHSP_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSequence(
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED));

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(AHSP_ELEMENT_ID)
                .getFirst()
                .getValue())
        .hasElementId(AHSP_ELEMENT_ID)
        .hasBpmnElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
        .hasBpmnEventType(BpmnEventType.UNSPECIFIED)
        .hasFlowScopeKey(processInstanceKey);
  }

  @Test
  public void shouldCreateJobOnActivation() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.task("A1");
            });
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final var adHocSubProcess =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .withElementId(AHSP_ELEMENT_ID)
            .getFirst();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(AHSP_ELEMENT_ID)
                .getFirst()
                .getValue())
        .hasType(JOB_TYPE)
        .hasRetries(3)
        .hasElementInstanceKey(adHocSubProcess.getKey())
        .hasElementId(adHocSubProcess.getValue().getElementId())
        .hasProcessDefinitionKey(adHocSubProcess.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(adHocSubProcess.getValue().getBpmnProcessId())
        .hasProcessDefinitionVersion(adHocSubProcess.getValue().getVersion());
  }

  @Test
  public void shouldRecreateJobOnInnerInstanceCompletion() {
    // given
    final BpmnModelInstance process =
        process(adHocSubProcess -> adHocSubProcess.task("A1").task("A2"));
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var jobKey =
        ENGINE.jobs().withType(JOB_TYPE).activate().getValue().getJobKeys().getFirst();
    final var jobResult =
        new JobResult()
            .setActivateElements(List.of(new JobResultActivateElement().setElementId("A1")));

    // when
    ENGINE.job().withKey(jobKey).withResult(jobResult).complete();

    // then
    final var adHocSubProcess =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .withElementId(AHSP_ELEMENT_ID)
            .getFirst();

    Assertions.assertThat(
            RecordingExporter.jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(AHSP_ELEMENT_ID)
                .limit(3))
        .extracting(Record::getIntent, job -> job.getValue().getElementInstanceKey())
        .containsSequence(
            tuple(JobIntent.CREATED, adHocSubProcess.getKey()),
            tuple(JobIntent.COMPLETED, adHocSubProcess.getKey()),
            tuple(JobIntent.CREATED, adHocSubProcess.getKey()));
  }

  @Test
  public void shouldCancelExistingJobsBeforeRecreatingOnInnerInstanceCompletion() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
            });
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var jobKey =
        ENGINE.jobs().withType(JOB_TYPE).activate().getValue().getJobKeys().getFirst();
    final var jobResult =
        new JobResult()
            .setActivateElements(
                List.of(
                    new JobResultActivateElement().setElementId("A"),
                    new JobResultActivateElement().setElementId("B")));

    // when
    ENGINE.job().withKey(jobKey).withResult(jobResult).complete();

    // then
    final var adHocSubProcess =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .withElementId(AHSP_ELEMENT_ID)
            .getFirst();

    Assertions.assertThat(
            RecordingExporter.jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(AHSP_ELEMENT_ID)
                .limit(5))
        .extracting(Record::getIntent, job -> job.getValue().getElementInstanceKey())
        .containsSequence(
            tuple(JobIntent.CREATED, adHocSubProcess.getKey()),
            tuple(JobIntent.COMPLETED, adHocSubProcess.getKey()),
            tuple(JobIntent.CREATED, adHocSubProcess.getKey()),
            tuple(JobIntent.CANCELED, adHocSubProcess.getKey()),
            tuple(JobIntent.CREATED, adHocSubProcess.getKey()));
  }

  @Test
  public void shouldActivateSecondSetOfElements() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
              adHocSubProcess.task("C");
            });
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    var jobKey = ENGINE.jobs().withType(JOB_TYPE).activate().getValue().getJobKeys().getFirst();
    var jobResult =
        new JobResult()
            .setActivateElements(
                List.of(
                    new JobResultActivateElement().setElementId("A"),
                    new JobResultActivateElement().setElementId("B")));
    ENGINE.job().withKey(jobKey).withResult(jobResult).complete();

    // when
    jobKey = ENGINE.jobs().withType(JOB_TYPE).activate().getValue().getJobKeys().getFirst();
    jobResult =
        new JobResult()
            .setActivateElements(List.of(new JobResultActivateElement().setElementId("C")));
    ENGINE.job().withKey(jobKey).withResult(jobResult).complete();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitByCount(
                    r ->
                        r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED
                            && r.getValue().getElementId().equals(AHSP_INNER_ELEMENT_ID),
                    3))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(AHSP_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                AHSP_INNER_ELEMENT_ID,
                ProcessInstanceIntent.ELEMENT_ACTIVATED), // inner instance for A
            tuple(
                AHSP_INNER_ELEMENT_ID,
                ProcessInstanceIntent.ELEMENT_ACTIVATED), // inner instance for B
            tuple("A", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("B", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("B", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(AHSP_INNER_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(AHSP_INNER_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                AHSP_INNER_ELEMENT_ID,
                ProcessInstanceIntent.ELEMENT_ACTIVATED), // inner instance for C
            tuple("C", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("C", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(AHSP_INNER_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCancelJobOnTermination() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.task("A1");
            });
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    final var adHocSubProcess =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .withElementId(AHSP_ELEMENT_ID)
            .getFirst();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(AHSP_ELEMENT_ID)
                .getFirst()
                .getValue())
        .hasType(JOB_TYPE)
        .hasRetries(3)
        .hasElementInstanceKey(adHocSubProcess.getKey())
        .hasElementId(adHocSubProcess.getValue().getElementId())
        .hasProcessDefinitionKey(adHocSubProcess.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(adHocSubProcess.getValue().getBpmnProcessId())
        .hasProcessDefinitionVersion(adHocSubProcess.getValue().getVersion());
  }

  @Test
  public void shouldCreateIncidentOnJobFail() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.task("A1");
            });
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var jobKey =
        ENGINE.jobs().withType(JOB_TYPE).activate().getValue().getJobKeys().getFirst();
    ENGINE
        .job()
        .withKey(jobKey)
        .withRetries(0)
        .withErrorCode("errorCode")
        .withErrorMessage("jobFailed")
        .fail();

    // then
    final var adHocSubProcess =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .withElementId(AHSP_ELEMENT_ID)
            .getFirst();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.FAILED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(AHSP_ELEMENT_ID)
                .getFirst()
                .getValue())
        .hasType(JOB_TYPE)
        .hasElementInstanceKey(adHocSubProcess.getKey())
        .hasElementId(adHocSubProcess.getValue().getElementId())
        .hasProcessDefinitionKey(adHocSubProcess.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(adHocSubProcess.getValue().getBpmnProcessId())
        .hasProcessDefinitionVersion(adHocSubProcess.getValue().getVersion());

    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasElementId(AHSP_ELEMENT_ID)
        .hasElementInstanceKey(adHocSubProcess.getKey())
        .hasErrorType(ErrorType.AD_HOC_SUB_PROCESS_NO_RETRIES)
        .hasErrorMessage("jobFailed");
  }

  @Test
  public void shouldActivateElementsOnJobCompletion() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.task("A1").task("A2");
              adHocSubProcess.task("B");
              adHocSubProcess.task("C");
            });
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var jobKey =
        ENGINE.jobs().withType(JOB_TYPE).activate().getValue().getJobKeys().getFirst();
    final var jobResult =
        new JobResult()
            .setActivateElements(
                List.of(
                    new JobResultActivateElement().setElementId("A1"),
                    new JobResultActivateElement().setElementId("B")));

    // when
    ENGINE.job().withKey(jobKey).withResult(jobResult).complete();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitByCount(
                    r ->
                        r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED
                            && r.getValue().getElementId().equals(AHSP_INNER_ELEMENT_ID),
                    2))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(AHSP_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(AHSP_INNER_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(AHSP_INNER_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A1", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("B", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A1", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("B", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("A2", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                AHSP_INNER_ELEMENT_ID,
                ProcessInstanceIntent.ELEMENT_COMPLETED), // inner instance for B
            tuple("A2", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                AHSP_INNER_ELEMENT_ID,
                ProcessInstanceIntent.ELEMENT_COMPLETED)) // inner instance for A
        .doesNotContain(tuple("C", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }
}
