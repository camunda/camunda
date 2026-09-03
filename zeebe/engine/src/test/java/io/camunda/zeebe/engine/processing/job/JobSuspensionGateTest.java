/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class JobSuspensionGateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectJobCompleteWhileSuspended() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, processId);
    final long processInstanceKey = job.getValue().getProcessInstanceKey();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(job.getKey()).expectRejection().complete();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(JobIntent.COMPLETE)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldRejectJobFailWhileSuspended() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, processId);
    final long processInstanceKey = job.getValue().getProcessInstanceKey();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(job.getKey()).withRetries(0).expectRejection().fail();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(JobIntent.FAIL)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldRejectJobThrowErrorWhileSuspended() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, processId);
    final long processInstanceKey = job.getValue().getProcessInstanceKey();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(job.getKey()).withErrorCode("ERR").expectRejection().throwError();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(JobIntent.THROW_ERROR)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldRejectJobUpdateWhileSuspended() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, processId);
    final long processInstanceKey = job.getValue().getProcessInstanceKey();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(job.getKey()).expectRejection().update();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(JobIntent.UPDATE)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldRejectJobUpdateRetriesWhileSuspended() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, processId);
    final long processInstanceKey = job.getValue().getProcessInstanceKey();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(job.getKey()).withRetries(5).expectRejection().updateRetries();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(JobIntent.UPDATE_RETRIES)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldRejectJobUpdateTimeoutWhileSuspended() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, processId);
    final long processInstanceKey = job.getValue().getProcessInstanceKey();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(job.getKey()).withTimeout(60_000L).expectRejection().updateTimeout();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(JobIntent.UPDATE_TIMEOUT)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }
}
