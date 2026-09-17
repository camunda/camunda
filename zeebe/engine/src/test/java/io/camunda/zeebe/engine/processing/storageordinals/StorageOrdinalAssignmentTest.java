/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessInstructionRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultActivateElement;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessInstructionIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessEventIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBatchIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.ProcessEventRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

/**
 * Pins the storage ordinal assignment end to end: with archiverless mode enabled the engine selects
 * the fixed provider, stamps the configured ordinal on the root process instance at creation, and
 * all follow-up records of the instance inherit that ordinal.
 */
public final class StorageOrdinalAssignmentTest {

  private static final int FIXED_ORDINAL = 1234;

  /**
   * Runs with a single command per batch so every follow-up command reaches the log before it is
   * processed. With the default batching, a follow-up command is processed in the same batch from
   * the live record object, so a processor that re-stamps the ordinal (e.g. the ad-hoc sub-process
   * ACTIVATE/COMPLETE processors) would already be visible on the logged command and hide a missing
   * assignment in the producing processor.
   */
  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .maxCommandsInBatch(1)
          .withEngineConfig(
              c -> c.setArchiverlessEnabled(true).setFixedStorageOrdinal(FIXED_ORDINAL));

  @Test
  public void shouldAssignConfiguredOrdinalToProcessInstanceRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("pi-records-process").startEvent().endEvent().done())
        .deploy();

    // when
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("pi-records-process").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .isNotEmpty()
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToVariableRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("variable-records-process").startEvent().endEvent().done())
        .deploy();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("variable-records-process")
            .withVariable("ordinalVariable", 1)
            .create();

    // then
    final var variableCreated =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("ordinalVariable")
            .getFirst();
    assertThat(variableCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToJobRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("job-records-process")
                .startEvent()
                .serviceTask("service-task", t -> t.zeebeJobType("ordinal-job"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("job-records-process").create();

    // then
    final var jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(jobCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToUserTaskRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("user-task-records-process")
                .startEvent()
                .userTask("user-task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("user-task-records-process").create();

    // then
    final var userTaskCreated =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(userTaskCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToUserTaskLifecycleAndListenerRecords() {
    // given: a user task with a completing task listener, so completion runs through the
    // intermediate-state path and creates a listener job
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("user-task-lifecycle-process")
                .startEvent()
                .userTask("user-task")
                .zeebeUserTask()
                .zeebeTaskListener(l -> l.completing().type("ordinal-task-listener"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("user-task-lifecycle-process").create();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue()
            .getUserTaskKey();

    // when
    engine.userTask().withKey(userTaskKey).complete();
    engine.job().ofInstance(processInstanceKey).withType("ordinal-task-listener").complete();

    // then: the task listener job carries the ordinal
    final var listenerJobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("ordinal-task-listener")
            .getFirst();
    assertThat(listenerJobCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);

    // and: every user task lifecycle record up to COMPLETED carries the ordinal
    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == UserTaskIntent.COMPLETED))
        .isNotEmpty()
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToSignalSubscriptionRecords() {
    // given: an instance waiting at an intermediate signal catch event
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("signal-subscription-process")
                .startEvent()
                .intermediateCatchEvent("signal-catch", c -> c.signal("ordinal-signal"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("signal-subscription-process").create();

    // then: the subscription opened for the catch event carries the ordinal
    final var subscriptionCreated =
        RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
            .withSignalName("ordinal-signal")
            .getFirst();
    assertThat(subscriptionCreated.getValue().getProcessInstanceKey())
        .isEqualTo(processInstanceKey);
    assertThat(subscriptionCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);

    // when: the signal is broadcast
    engine.signal().withSignalName("ordinal-signal").broadcast();

    // then: the DELETED event, appended from the stored subscription, inherits the ordinal
    final var subscriptionDeleted =
        RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
            .withSignalName("ordinal-signal")
            .getFirst();
    assertThat(subscriptionDeleted.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToProcessEventRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process-event-records-process")
                .startEvent()
                .intermediateCatchEvent("catch-event")
                .message(m -> m.name("ordinal-message").zeebeCorrelationKeyExpression("key"))
                .endEvent()
                .done())
        .deploy();
    engine
        .processInstance()
        .ofBpmnProcessId("process-event-records-process")
        .withVariable("key", "correlation-key-1")
        .create();

    // when
    engine.message().withName("ordinal-message").withCorrelationKey("correlation-key-1").publish();

    // then
    final var records =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getValueType() == ValueType.PROCESS_EVENT
                        && r.getIntent() == ProcessEventIntent.TRIGGERING)
            .asList();
    final var processEventTriggering = records.get(records.size() - 1);
    assertThat(((ProcessEventRecordValue) processEventTriggering.getValue()).getStorageOrdinal())
        .isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToProcessInstanceBatchRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("batch-records-process")
                .startEvent()
                .serviceTask(
                    "multi-instance-task",
                    t ->
                        t.zeebeJobType("ordinal-multi-instance")
                            .multiInstance(
                                m ->
                                    m.parallel()
                                        .zeebeInputCollectionExpression("items")
                                        .zeebeInputElement("item")))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("batch-records-process")
            .withVariable("items", List.of(1, 2, 3))
            .create();

    // then
    final var batchActivate =
        RecordingExporter.processInstanceBatchRecords(ProcessInstanceBatchIntent.ACTIVATE)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(batchActivate.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToDecisionEvaluationRecords() {
    // given
    engine
        .deployment()
        .withXmlClasspathResource("/dmn/drg-force-user.dmn")
        .withXmlResource(
            Bpmn.createExecutableProcess("decision-records-process")
                .startEvent()
                .businessRuleTask(
                    "business-rule-task",
                    t -> t.zeebeCalledDecisionId("jedi_or_sith").zeebeResultVariable("result"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("decision-records-process")
            .withVariable("lightsaberColor", "blue")
            .create();

    // then
    final var decisionEvaluated =
        RecordingExporter.decisionEvaluationRecords()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(decisionEvaluated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToIncidentRecordsOfAJobWithoutRetries() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("job-fail-incident-process")
                .startEvent()
                .serviceTask("service-task", t -> t.zeebeJobType("ordinal-failing-job"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("job-fail-incident-process").create();
    engine.jobs().withType("ordinal-failing-job").withMaxJobsToActivate(1).activate();

    // when
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withType("ordinal-failing-job")
        .withRetries(0)
        .fail();

    // then
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(incidentCreated.getValue().getErrorType()).isEqualTo(ErrorType.JOB_NO_RETRIES);
    assertThat(incidentCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToResolvedIncidentRecords() {
    // given: an incident raised for a job without retries
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("incident-resolve-process")
                .startEvent()
                .serviceTask("service-task", t -> t.zeebeJobType("ordinal-resolvable-job"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("incident-resolve-process").create();
    engine.jobs().withType("ordinal-resolvable-job").withMaxJobsToActivate(1).activate();
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withType("ordinal-resolvable-job")
        .withRetries(0)
        .fail();
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withType("ordinal-resolvable-job")
        .withRetries(1)
        .updateRetries();
    engine.incident().ofInstance(processInstanceKey).withKey(incidentCreated.getKey()).resolve();

    // then: the RESOLVED event carries the ordinal of the incident stored in state
    final var incidentResolved =
        RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(incidentResolved.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToIncidentRecordsOfAnUncaughtJobError() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("job-error-incident-process")
                .startEvent()
                .serviceTask("service-task", t -> t.zeebeJobType("ordinal-error-job"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("job-error-incident-process").create();

    // when - the thrown error has no matching catch event
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withType("ordinal-error-job")
        .withErrorCode("uncaught-error")
        .throwError();

    // then
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(incidentCreated.getValue().getErrorType())
        .isEqualTo(ErrorType.UNHANDLED_ERROR_EVENT);
    assertThat(incidentCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToIncidentRecordsOfAFailedSecretResolution() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("secret-incident-process")
                .startEvent()
                .serviceTask("service-task", t -> t.zeebeJobType("ordinal-secret-job"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("secret-incident-process").create();
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    final var resolutionRequested =
        new SecretReferenceRecord()
            .setStoreId("ordinal-store")
            .setSecretReference("ordinal-secret");
    resolutionRequested.addJobKey(jobKey);
    final var batchCreateIncidents =
        new SecretReferenceRecord()
            .setStoreId("ordinal-store")
            .setSecretReference("ordinal-secret");
    batchCreateIncidents.addJobKey(jobKey);

    // when - the job is seeded as waiting on the secret reference and the drain is processed
    engine.stop();
    engine.writeRecords(
        RecordToWrite.event()
            .secretReference(SecretReferenceIntent.RESOLUTION_REQUESTED, resolutionRequested),
        RecordToWrite.command()
            .secretReference(SecretReferenceIntent.BATCH_CREATE_INCIDENTS, batchCreateIncidents));
    // starting the engine re-exports the whole partition log, so clear the previously seen records
    RecordingExporter.reset();
    engine.start();

    // then
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withErrorType(ErrorType.SECRET_RESOLUTION_ERROR)
            .withJobKey(jobKey)
            .getFirst();
    assertThat(incidentCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToAdHocSubProcessInstructionActivatedRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("ahsp-activate-process")
                .startEvent()
                .adHocSubProcess("ad-hoc", adHocSubProcess -> adHocSubProcess.task("A"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("ahsp-activate-process").create();
    final long adHocSubProcessInstanceKey = adHocSubProcessInstanceKeyOf(processInstanceKey);

    // when - the command arrives from outside the engine, carrying no ordinal of its own
    engine
        .adHocSubProcessActivity()
        .withAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)
        .withElementIds("A")
        .activate();

    // then
    final var activated =
        RecordingExporter.adHocSubProcessInstructionRecords()
            .withIntent(AdHocSubProcessInstructionIntent.ACTIVATED)
            .getFirst();
    assertThat(activated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToAdHocSubProcessInstructionCompletedRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("ahsp-complete-process")
                .startEvent()
                .adHocSubProcess("ad-hoc", adHocSubProcess -> adHocSubProcess.task("A"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("ahsp-complete-process").create();
    final long adHocSubProcessInstanceKey = adHocSubProcessInstanceKeyOf(processInstanceKey);

    // when - the command arrives from outside the engine, carrying no ordinal of its own
    engine.writeRecords(
        RecordToWrite.command()
            .adHocSubProcessInstruction(
                AdHocSubProcessInstructionIntent.COMPLETE,
                new AdHocSubProcessInstructionRecord()
                    .setAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)));

    // then
    final var completed =
        RecordingExporter.adHocSubProcessInstructionRecords()
            .withIntent(AdHocSubProcessInstructionIntent.COMPLETED)
            .getFirst();
    assertThat(completed.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToJobDrivenAdHocSubProcessInstructionRecords() {
    // given
    final var jobType = "ahsp-job-type";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("ahsp-job-process")
                .startEvent()
                .adHocSubProcess("ad-hoc", adHocSubProcess -> adHocSubProcess.task("A"))
                .zeebeJobType(jobType)
                .endEvent()
                .done())
        .deploy();
    engine.processInstance().ofBpmnProcessId("ahsp-job-process").create();

    // when - the ad-hoc sub-process job worker activates an element
    completeAdHocSubProcessJob(jobType, 1, false, new JobResultActivateElement().setElementId("A"));

    // then - the job-emitted command itself must carry the ordinal, not only the event
    final var activateCommand =
        RecordingExporter.adHocSubProcessInstructionRecords()
            .withIntent(AdHocSubProcessInstructionIntent.ACTIVATE)
            .onlyCommands()
            .getFirst();
    assertThat(activateCommand.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
    final var activated =
        RecordingExporter.adHocSubProcessInstructionRecords()
            .withIntent(AdHocSubProcessInstructionIntent.ACTIVATED)
            .getFirst();
    assertThat(activated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);

    // when - the job created after A completed reports the completion condition as fulfilled
    completeAdHocSubProcessJob(jobType, 2, true);

    // then
    final var completeCommand =
        RecordingExporter.adHocSubProcessInstructionRecords()
            .withIntent(AdHocSubProcessInstructionIntent.COMPLETE)
            .onlyCommands()
            .getFirst();
    assertThat(completeCommand.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
    final var completed =
        RecordingExporter.adHocSubProcessInstructionRecords()
            .withIntent(AdHocSubProcessInstructionIntent.COMPLETED)
            .getFirst();
    assertThat(completed.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  private long adHocSubProcessInstanceKeyOf(final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
        .getFirst()
        .getKey();
  }

  private void completeAdHocSubProcessJob(
      final String jobType,
      final long jobCounter,
      final boolean completionConditionFulfilled,
      final JobResultActivateElement... activateElements) {
    // follow-up commands reach the log one at a time, so wait for the job before completing it
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withType(jobType)
            .skip(jobCounter - 1)
            .getFirst()
            .getKey();
    final var jobResult =
        new JobResult()
            .setActivateElements(List.of(activateElements))
            .setCompletionConditionFulfilled(completionConditionFulfilled);
    engine.job().withKey(jobKey).withResult(jobResult).complete();
  }
}
