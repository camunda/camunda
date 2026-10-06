/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class UserTaskSuspensionGateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectUserTaskCompleteWhileSuspended() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<UserTaskRecordValue> rejection =
        ENGINE.userTask().ofInstance(processInstanceKey).expectRejection().complete();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(UserTaskIntent.COMPLETE)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldRejectUserTaskClaimWhileSuspended() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<UserTaskRecordValue> rejection =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withAssignee("user")
            .expectRejection()
            .claim();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(UserTaskIntent.CLAIM)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldRejectUserTaskAssignWhileSuspended() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<UserTaskRecordValue> rejection =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withAssignee("user")
            .expectRejection()
            .assign();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(UserTaskIntent.ASSIGN)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldRejectUserTaskUpdateWhileSuspended() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<UserTaskRecordValue> rejection =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withAllAttributesChanged()
            .expectRejection()
            .update();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(UserTaskIntent.UPDATE)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldCancelUserTaskWhenInstanceTerminatedWhileSuspended() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldRejectTaskListenerJobCompletionWhileSuspended() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String listenerType = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .zeebeTaskListener(l -> l.completing().type(listenerType))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    final long listenerJobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(listenerType)
            .getFirst()
            .getKey();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(listenerJobKey).expectRejection().complete();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(JobIntent.COMPLETE)
        .hasRejectionType(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldTerminateSuspendedInstanceWhenCancelingListenerJobCompleted() {
    // given
    final String listenerType = Strings.newRandomValidBpmnId();
    final long processInstanceKey = suspendAndCancelInstanceWithCancelingListeners(listenerType);
    final long listenerJobKey = listenerJobKey(processInstanceKey, listenerType);

    // when
    final Record<JobRecordValue> result = ENGINE.job().withKey(listenerJobKey).complete();

    // then
    Assertions.assertThat(result).hasIntent(JobIntent.COMPLETED);
    assertThatProcessInstanceTerminated(processInstanceKey);
  }

  @Test
  public void shouldTerminateSuspendedInstanceWhenAllCancelingListenerJobsCompleted() {
    // given
    final String firstListenerType = Strings.newRandomValidBpmnId();
    final String secondListenerType = Strings.newRandomValidBpmnId();
    final long processInstanceKey =
        suspendAndCancelInstanceWithCancelingListeners(firstListenerType, secondListenerType);
    ENGINE.job().withKey(listenerJobKey(processInstanceKey, firstListenerType)).complete();

    // when
    final Record<JobRecordValue> result =
        ENGINE.job().withKey(listenerJobKey(processInstanceKey, secondListenerType)).complete();

    // then
    Assertions.assertThat(result).hasIntent(JobIntent.COMPLETED);
    assertThatProcessInstanceTerminated(processInstanceKey);
  }

  @Test
  public void shouldHandOutFailedCancelingListenerJobOfSuspendedInstanceAgain() {
    // given
    final String listenerType = Strings.newRandomValidBpmnId();
    final long processInstanceKey = suspendAndCancelInstanceWithCancelingListeners(listenerType);
    final long listenerJobKey = listenerJobKey(processInstanceKey, listenerType);
    ENGINE.jobs().withType(listenerType).activate();

    // when
    final Record<JobRecordValue> result =
        ENGINE.job().withKey(listenerJobKey).withRetries(1).fail();

    // then
    Assertions.assertThat(result).hasIntent(JobIntent.FAILED);
    assertThatJobIsHandedOut(listenerType, listenerJobKey);
    ENGINE.job().withKey(listenerJobKey).complete();
    assertThatProcessInstanceTerminated(processInstanceKey);
  }

  @Test
  public void shouldRecurCancelingListenerJobOfSuspendedInstanceAfterBackoff() {
    // given
    final String listenerType = Strings.newRandomValidBpmnId();
    final long processInstanceKey = suspendAndCancelInstanceWithCancelingListeners(listenerType);
    final long listenerJobKey = listenerJobKey(processInstanceKey, listenerType);
    ENGINE.jobs().withType(listenerType).activate();
    ENGINE.job().withKey(listenerJobKey).withRetries(1).withBackOff(Duration.ofSeconds(1)).fail();

    // when
    ENGINE.increaseTime(Duration.ofSeconds(2));

    // then
    RecordingExporter.jobRecords(JobIntent.RECURRED_AFTER_BACKOFF)
        .withRecordKey(listenerJobKey)
        .await();
    assertThatJobIsHandedOut(listenerType, listenerJobKey);
    ENGINE.job().withKey(listenerJobKey).complete();
    assertThatProcessInstanceTerminated(processInstanceKey);
  }

  @Test
  public void shouldHandOutTimedOutCancelingListenerJobOfSuspendedInstanceAgain() {
    // given
    final String listenerType = Strings.newRandomValidBpmnId();
    final long processInstanceKey = suspendAndCancelInstanceWithCancelingListeners(listenerType);
    final long listenerJobKey = listenerJobKey(processInstanceKey, listenerType);
    ENGINE.jobs().withType(listenerType).withTimeout(10L).activate();

    // when
    ENGINE.increaseTime(EngineConfiguration.DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL);

    // then
    RecordingExporter.jobRecords(JobIntent.TIMED_OUT).withRecordKey(listenerJobKey).await();
    assertThatJobIsHandedOut(listenerType, listenerJobKey);
    ENGINE.job().withKey(listenerJobKey).complete();
    assertThatProcessInstanceTerminated(processInstanceKey);
  }

  @Test
  public void shouldResolveIncidentOfCancelingListenerJobOfSuspendedInstance() {
    // given
    final String listenerType = Strings.newRandomValidBpmnId();
    final long processInstanceKey = suspendAndCancelInstanceWithCancelingListeners(listenerType);
    final long listenerJobKey = listenerJobKey(processInstanceKey, listenerType);
    ENGINE.jobs().withType(listenerType).activate();
    ENGINE.job().withKey(listenerJobKey).withRetries(0).fail();
    final long incidentKey =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    final Record<JobRecordValue> retriesUpdated =
        ENGINE.job().withKey(listenerJobKey).withRetries(1).updateRetries();
    final Record<IncidentRecordValue> resolved =
        ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentKey).resolve();

    // then
    Assertions.assertThat(retriesUpdated).hasIntent(JobIntent.RETRIES_UPDATED);
    Assertions.assertThat(resolved).hasIntent(IncidentIntent.RESOLVED);
    assertThatJobIsHandedOut(listenerType, listenerJobKey);
    ENGINE.job().withKey(listenerJobKey).complete();
    assertThatProcessInstanceTerminated(processInstanceKey);
  }

  private static long suspendAndCancelInstanceWithCancelingListeners(
      final String... listenerTypes) {
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(processWithCancelingListeners(processId, listenerTypes))
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectTerminating().cancel();
    return processInstanceKey;
  }

  private static BpmnModelInstance processWithCancelingListeners(
      final String processId, final String... listenerTypes) {
    final UserTaskBuilder userTask =
        Bpmn.createExecutableProcess(processId).startEvent().userTask("task").zeebeUserTask();
    for (final String listenerType : listenerTypes) {
      userTask.zeebeTaskListener(l -> l.canceling().type(listenerType));
    }
    return userTask.endEvent().done();
  }

  private static long listenerJobKey(final long processInstanceKey, final String listenerType) {
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(listenerType)
        .getFirst()
        .getKey();
  }

  private static void assertThatJobIsHandedOut(final String jobType, final long jobKey) {
    final Record<JobBatchRecordValue> batch = ENGINE.jobs().withType(jobType).activate();
    assertThat(batch.getValue().getJobKeys()).containsExactly(jobKey);
  }

  private static void assertThatProcessInstanceTerminated(final long processInstanceKey) {
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withRecordKey(processInstanceKey)
                .exists())
        .isTrue();
  }
}
