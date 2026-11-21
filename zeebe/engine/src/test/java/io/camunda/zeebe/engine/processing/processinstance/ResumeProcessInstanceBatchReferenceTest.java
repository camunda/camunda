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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * A batch resume item completes only on a {@code RESUMED} that carries the batch reference. {@code
 * RESUMED} is often written by a later command than the batch's {@code RESUME}, so it must take the
 * reference stored when the resume started.
 */
public final class ResumeProcessInstanceBatchReferenceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCarryBatchReferenceWhenResumeFinishes() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String jobType = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .withBatchOperationReference(1L)
        .resume();
    ENGINE.job().ofInstance(processInstanceKey).withType(jobType).complete();

    // then
    assertResumedOnceWithBatchReference(processInstanceKey, 1L);
  }

  @Test
  public void shouldCarryBatchReferenceWhenJobCompletionEndsInstanceWhileResuming() {
    // given - the process waits for its end listener, so the resume chain is rejected and the
    // instance stays RESUMING until the worker completes the listener job
    final var instance = suspendWhileWaitingForEndListener();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(instance.processInstanceKey())
        .withBatchOperationReference(2L)
        .expectResuming()
        .resume();
    awaitResumeChainRejected(instance.processInstanceKey());
    ENGINE.job().withKey(instance.listenerJobKey()).complete();

    // then
    assertResumedOnceWithBatchReference(instance.processInstanceKey(), 2L);
  }

  @Test
  public void shouldKeepFirstBatchReferenceWhenResumeRestarts() {
    // given
    final var instance = suspendWhileWaitingForEndListener();
    ENGINE
        .processInstance()
        .withInstanceKey(instance.processInstanceKey())
        .withBatchOperationReference(3L)
        .expectResuming()
        .resume();
    awaitResumeChainRejected(instance.processInstanceKey());

    // when - a second batch resumes the instance while it is still RESUMING
    ENGINE
        .processInstance()
        .withInstanceKey(instance.processInstanceKey())
        .withBatchOperationReference(4L)
        .expectResuming()
        .resume();
    ENGINE.job().withKey(instance.listenerJobKey()).complete();

    // then
    assertResumedOnceWithBatchReference(instance.processInstanceKey(), 3L);
  }

  private static ListenerInstance suspendWhileWaitingForEndListener() {
    final String processId = Strings.newRandomValidBpmnId();
    final String listenerType = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .zeebeEndExecutionListener(listenerType)
                .startEvent()
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final long listenerJobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(listenerType)
            .getFirst()
            .getKey();
    // an activated job is not suspended, so the worker can still complete it
    ENGINE.jobs().withType(listenerType).activate();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    return new ListenerInstance(processInstanceKey, listenerJobKey);
  }

  private static void awaitResumeChainRejected(final long processInstanceKey) {
    RecordingExporter.processInstanceRecords()
        .onlyCommandRejections()
        .withIntent(ProcessInstanceIntent.RESUME_JOBS)
        .withProcessInstanceKey(processInstanceKey)
        .await();
  }

  private static void assertResumedOnceWithBatchReference(
      final long processInstanceKey, final long batchOperationReference) {
    final List<Record<ProcessInstanceRecordValue>> resumed =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .limitToProcessInstanceCompleted()
            .withIntent(ProcessInstanceIntent.RESUMED)
            .toList();
    assertThat(resumed)
        .singleElement()
        .extracting(Record::getBatchOperationReference)
        .isEqualTo(batchOperationReference);

    // the stored request is removed again
    assertThat(
            RecordingExporter.asyncRequestRecords()
                .withIntent(AsyncRequestIntent.PROCESSED)
                .filter(r -> r.getValue().getScopeKey() == processInstanceKey)
                .getFirst()
                .getValue()
                .getBatchOperationReference())
        .isEqualTo(batchOperationReference);
  }

  private record ListenerInstance(long processInstanceKey, long listenerJobKey) {}
}
