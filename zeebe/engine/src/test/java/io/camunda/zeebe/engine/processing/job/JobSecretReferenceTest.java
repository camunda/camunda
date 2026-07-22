/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobSecretReferenceValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class JobSecretReferenceTest {

  /** Every reference resolves, so jobs with secret references stay activatable in these tests. */
  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withSecretResolver(
              references ->
                  references.stream()
                      .collect(Collectors.toMap(Function.identity(), reference -> "cached")));

  private static final String PROCESS_ID = "process";
  private static final String TASK_ID = "task";
  private static final String JOB_TYPE = "task-type";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldStoreSecretReferenceOnJobCreation() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithServiceTask(
                t ->
                    t.zeebeInputExpression(
                        "\"Bearer \" + camunda.secrets.token", "tokens.externalSystemToken")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(jobCreated.getValue().getSecretReferences())
        .extracting(
            JobSecretReferenceValue::getStoreId,
            JobSecretReferenceValue::getSecretReference,
            JobSecretReferenceValue::getPath)
        .containsExactly(tuple("", "token", "/tokens/externalSystemToken"));
  }

  @Test
  public void shouldStoreMultipleSecretReferencesForSingleInputMapping() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithServiceTask(
                t ->
                    t.zeebeInputExpression(
                        "\"Bearer \" + camunda.secrets.token + camunda.secrets.postfix",
                        "tokens.externalSystemToken")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(jobCreated.getValue().getSecretReferences())
        .extracting(
            JobSecretReferenceValue::getStoreId,
            JobSecretReferenceValue::getSecretReference,
            JobSecretReferenceValue::getPath)
        .containsExactlyInAnyOrder(
            tuple("", "token", "/tokens/externalSystemToken"),
            tuple("", "postfix", "/tokens/externalSystemToken"));
  }

  @Test
  public void shouldStoreSecretReferencesFromMultipleInputMappings() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithServiceTask(
                t ->
                    t.zeebeInputExpression("camunda.secrets.token", "auth.token")
                        .zeebeInputExpression("camunda.secrets.apiKey", "auth.key")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(jobCreated.getValue().getSecretReferences())
        .extracting(
            JobSecretReferenceValue::getStoreId,
            JobSecretReferenceValue::getSecretReference,
            JobSecretReferenceValue::getPath)
        .containsExactlyInAnyOrder(
            tuple("", "token", "/auth/token"), tuple("", "apiKey", "/auth/key"));
  }

  @Test
  public void shouldHaveNoSecretReferencesWhenTaskReferencesNone() {
    // given
    ENGINE.deployment().withXmlResource(processWithServiceTask(t -> {})).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(jobCreated.getValue().getSecretReferences()).isEmpty();
  }

  @Test
  public void shouldCarrySecretReferencesOnJobActivation() {
    // given unique ids so only this test's job is activatable on the shared engine
    final String processId = "process-activation";
    final String jobType = "task-type-activation";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    TASK_ID,
                    t ->
                        t.zeebeJobType(jobType)
                            .zeebeInputExpression("camunda.secrets.token", "auth.token"))
                .endEvent()
                .done())
        .deploy();
    ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    final var activatedJobs = ENGINE.jobs().withType(jobType).activate().getValue().getJobs();

    // then
    assertThat(activatedJobs).hasSize(1);
    assertThat(activatedJobs.getFirst().getSecretReferences())
        .extracting(
            JobSecretReferenceValue::getStoreId,
            JobSecretReferenceValue::getSecretReference,
            JobSecretReferenceValue::getPath)
        .containsExactly(tuple("", "token", "/auth/token"));
  }

  @Test
  public void shouldNotStoreSecretReferencesOnExecutionListenerJob() {
    // given the service task references a secret and has a start execution listener
    ENGINE
        .deployment()
        .withXmlResource(
            processWithServiceTask(
                t ->
                    t.zeebeInputExpression("camunda.secrets.token", "auth.token")
                        .zeebeExecutionListener(el -> el.start().type("start-listener"))))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then the execution listener job does not inherit the element's secret references
    final Record<JobRecordValue> listenerJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .getFirst();

    assertThat(listenerJob.getValue().getSecretReferences()).isEmpty();
  }

  @Test
  public void shouldNotStoreSecretReferencesOnTaskListenerJob() {
    // given the user task references a secret and has a creating task listener
    ENGINE
        .deployment()
        .withXmlResource(
            processWithUserTask(
                t ->
                    t.zeebeUserTask()
                        .zeebeInputExpression("camunda.secrets.token", "auth.token")
                        .zeebeTaskListener(l -> l.creating().type("creating-listener"))))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then the task listener job does not inherit the element's secret references
    final Record<JobRecordValue> listenerJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withJobKind(JobKind.TASK_LISTENER)
            .getFirst();

    assertThat(listenerJob.getValue().getSecretReferences()).isEmpty();
  }

  @Test
  public void shouldStoreSecretReferenceOnAdHocSubProcessJob() {
    // given a job-based ad-hoc sub-process that references a secret in its input mapping
    final String processId = "process-ahsp";
    final String jobType = "ahsp-type";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .adHocSubProcess(
                    TASK_ID,
                    ahsp -> {
                      ahsp.task("inner");
                      ahsp.zeebeInputExpression("camunda.secrets.token", "auth.token");
                    })
                .zeebeJobType(jobType)
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withJobKind(JobKind.AD_HOC_SUB_PROCESS)
            .getFirst();

    assertThat(jobCreated.getValue().getSecretReferences())
        .extracting(
            JobSecretReferenceValue::getStoreId,
            JobSecretReferenceValue::getSecretReference,
            JobSecretReferenceValue::getPath)
        .containsExactly(tuple("", "token", "/auth/token"));
  }

  private static BpmnModelInstance processWithServiceTask(
      final Consumer<io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder> modifier) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .serviceTask(
            TASK_ID,
            t -> {
              t.zeebeJobType(JOB_TYPE);
              modifier.accept(t);
            })
        .endEvent()
        .done();
  }

  private static BpmnModelInstance processWithUserTask(
      final Consumer<io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder> modifier) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .userTask(TASK_ID, modifier)
        .endEvent()
        .done();
  }
}
