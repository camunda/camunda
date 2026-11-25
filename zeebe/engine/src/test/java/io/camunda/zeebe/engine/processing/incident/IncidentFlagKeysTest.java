/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class IncidentFlagKeysTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldSetFlagKeysOnFullPathForFirstIncident() {
    // given
    final var instance = startCallActivityWithTwoTasks();

    // when
    final var incident = failJob(instance.jobType(), instance.job1Key());

    // then
    assertThat(incident.getValue().getIncidentFlagKeys())
        .hasSize(4)
        .containsExactlyElementsOf(flattenedPath(incident));
  }

  @Test
  public void shouldSetOnlyNewKeysForSecondIncident() {
    // given
    final var instance = startCallActivityWithTwoTasks();
    failJob(instance.jobType(), instance.job1Key());

    // when
    final var secondIncident = failJob(instance.jobType(), instance.job2Key());

    // then
    assertThat(secondIncident.getValue().getIncidentFlagKeys())
        .containsExactly(instance.task2Key());
  }

  @Test
  public void shouldNotClearParentsWhileAnotherIncidentIsActive() {
    // given
    final var instance = startCallActivityWithTwoTasks();
    final var firstIncident = failJob(instance.jobType(), instance.job1Key());
    final var secondIncident = failJob(instance.jobType(), instance.job2Key());

    // when
    final var firstResolved =
        resolveJobIncident(instance.jobType(), instance.job1Key(), firstIncident);
    final var secondResolved =
        resolveJobIncident(instance.jobType(), instance.job2Key(), secondIncident);

    // then
    assertThat(firstResolved.getValue().getIncidentFlagKeys()).containsExactly(instance.task1Key());
    assertThat(secondResolved.getValue().getIncidentFlagKeys())
        .containsExactlyElementsOf(flattenedPath(secondResolved));
  }

  @Test
  public void shouldClearFlagKeysWhenProcessInstanceIsCanceled() {
    // given
    final var instance = startCallActivityWithTwoTasks();
    failJob(instance.jobType(), instance.job1Key());
    failJob(instance.jobType(), instance.job2Key());

    // when
    ENGINE.processInstance().withInstanceKey(instance.rootKey()).cancel();

    // then
    final var resolvedIncidents =
        RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
            .withBpmnProcessId(instance.childProcessId())
            .limit(2)
            .toList();
    assertThat(resolvedIncidents).hasSize(2);
    final var first = resolvedIncidents.get(0);
    final var second = resolvedIncidents.get(1);
    assertThat(first.getValue().getIncidentFlagKeys())
        .containsExactly(first.getValue().getElementInstanceKey());
    assertThat(second.getValue().getIncidentFlagKeys())
        .containsExactlyElementsOf(flattenedPath(second));
  }

  @Test
  public void shouldIncludeEmbeddedSubprocessInFlagKeys() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var jobType = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess("sub")
                .embeddedSubProcess()
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .subProcessDone()
                .endEvent()
                .done())
        .deploy();
    ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).getFirst().getKey();
    ENGINE.jobs().withType(jobType).activate();

    // when
    final var incident = failJob(jobType, jobKey);

    // then
    assertThat(incident.getValue().getIncidentFlagKeys())
        .hasSize(3)
        .containsExactlyElementsOf(flattenedPath(incident));
  }

  @Test
  public void shouldSetFlagKeysForProcessIncidentWithoutJob() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType(Strings.newRandomValidBpmnId())
                            .zeebeInputExpression("assert(foo, foo != null)", "foo"))
                .done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(incident.getValue().getIncidentFlagKeys())
        .isNotEmpty()
        .containsExactlyElementsOf(flattenedPath(incident));
  }

  private static List<Long> flattenedPath(final Record<IncidentRecordValue> incident) {
    return incident.getValue().getElementInstancePath().stream().flatMap(List::stream).toList();
  }

  private static Record<IncidentRecordValue> failJob(final String jobType, final long jobKey) {
    ENGINE.job().withType(jobType).withKey(jobKey).withRetries(0).fail();
    return RecordingExporter.incidentRecords(IncidentIntent.CREATED).withJobKey(jobKey).getFirst();
  }

  private static Record<IncidentRecordValue> resolveJobIncident(
      final String jobType, final long jobKey, final Record<IncidentRecordValue> incident) {
    ENGINE.job().withType(jobType).withKey(jobKey).withRetries(1).updateRetries();
    ENGINE
        .incident()
        .ofInstance(incident.getValue().getProcessInstanceKey())
        .withKey(incident.getKey())
        .resolve();
    return RecordingExporter.incidentRecords(IncidentIntent.RESOLVED).withJobKey(jobKey).getFirst();
  }

  /**
   * Root process with a call activity that calls a child process with two parallel service tasks.
   * Jobs are identified by their element id, not by order of arrival.
   */
  private static CallActivityInstance startCallActivityWithTwoTasks() {
    final var parentProcessId = Strings.newRandomValidBpmnId();
    final var childProcessId = Strings.newRandomValidBpmnId();
    final var jobType = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            "parent.bpmn",
            Bpmn.createExecutableProcess(parentProcessId)
                .startEvent()
                .callActivity("call", c -> c.zeebeProcessId(childProcessId))
                .endEvent()
                .done())
        .withXmlResource(
            "child.bpmn",
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .parallelGateway("fork")
                .serviceTask("task1", t -> t.zeebeJobType(jobType))
                .endEvent()
                .moveToNode("fork")
                .serviceTask("task2", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done())
        .deploy();

    final long rootKey = ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();

    final long task1Key = elementInstanceKey(childProcessId, "task1");
    final long task2Key = elementInstanceKey(childProcessId, "task2");

    final List<Record<JobRecordValue>> jobs =
        RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).limit(2).toList();
    final long job1Key = jobKeyOfElement(jobs, "task1");
    final long job2Key = jobKeyOfElement(jobs, "task2");
    ENGINE.jobs().withType(jobType).withMaxJobsToActivate(2).activate();

    return new CallActivityInstance(
        rootKey, childProcessId, jobType, task1Key, task2Key, job1Key, job2Key);
  }

  private static long elementInstanceKey(final String processId, final String elementId) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withBpmnProcessId(processId)
        .withElementId(elementId)
        .getFirst()
        .getKey();
  }

  private static long jobKeyOfElement(
      final List<Record<JobRecordValue>> jobs, final String elementId) {
    return jobs.stream()
        .filter(job -> elementId.equals(job.getValue().getElementId()))
        .findFirst()
        .orElseThrow()
        .getKey();
  }

  private record CallActivityInstance(
      long rootKey,
      String childProcessId,
      String jobType,
      long task1Key,
      long task2Key,
      long job1Key,
      long job2Key) {}
}
