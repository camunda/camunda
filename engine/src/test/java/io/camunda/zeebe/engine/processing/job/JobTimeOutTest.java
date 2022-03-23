/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.protocol.record.intent.JobIntent.TIME_OUT;
import static io.camunda.zeebe.test.util.record.RecordingExporter.jobBatchRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class JobTimeOutTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";
  private static String jobType;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Before
  public void setup() {
    jobType = Strings.newRandomValidBpmnId();
  }

  @Test
  public void shouldTimeOutJob() {
    // given
    final long jobKey = ENGINE.createJob(jobType, PROCESS_ID).getKey();
    final long timeout = 10L;

    ENGINE.jobs().withType(jobType).withTimeout(timeout).activate();
    ENGINE.increaseTime(JobTimeoutTrigger.TIME_OUT_POLLING_INTERVAL);

    // when expired
    jobRecords(TIME_OUT).withType(jobType).getFirst();
    ENGINE.jobs().withType(jobType).activate();

    // then activated again
    final List<Record<JobRecordValue>> jobEvents =
        jobRecords().withType(jobType).limit(3).collect(Collectors.toList());

    assertThat(jobEvents).extracting(Record::getKey).contains(jobKey);
    assertThat(jobEvents)
        .extracting(Record::getIntent)
        .containsExactly(JobIntent.CREATED, JobIntent.TIME_OUT, JobIntent.TIMED_OUT);
  }

  @Test
  public void shouldTimeOutAfterReprocessing() {
    // given
    final long jobKey = ENGINE.createJob(jobType, PROCESS_ID).getKey();
    final long timeout = 10L;

    ENGINE.jobs().withType(jobType).withTimeout(timeout).activate();
    ENGINE.increaseTime(JobTimeoutTrigger.TIME_OUT_POLLING_INTERVAL);
    jobRecords(TIME_OUT).withRecordKey(jobKey).getFirst();

    final long jobKey2 = ENGINE.createJob(jobType, PROCESS_ID).getKey();
    ENGINE.jobs().withType(jobType).activate();
    ENGINE.job().withKey(jobKey).complete();

    // when
    ENGINE.reprocess();
    ENGINE.jobs().withType(jobType).activate();

    // then
    ENGINE.increaseTime(JobTimeoutTrigger.TIME_OUT_POLLING_INTERVAL);
    jobRecords(TIME_OUT).withRecordKey(jobKey2).getFirst();
  }

  @Test
  public void shouldExpireMultipleActivatedJobsAtOnce() {
    // given
    final long instanceKey1 = createInstance();
    final long instanceKey2 = createInstance();

    final long jobKey1 =
        jobRecords(JobIntent.CREATED)
            .withType(jobType)
            .filter(r -> r.getValue().getProcessInstanceKey() == instanceKey1)
            .getFirst()
            .getKey();
    final long jobKey2 =
        jobRecords(JobIntent.CREATED)
            .withType(jobType)
            .filter(r -> r.getValue().getProcessInstanceKey() == instanceKey2)
            .getFirst()
            .getKey();
    final long timeout = 10L;

    ENGINE.jobs().withType(jobType).withTimeout(timeout).activate();

    // when
    jobBatchRecords(JobBatchIntent.ACTIVATED).withType(jobType).getFirst();

    ENGINE.increaseTime(JobTimeoutTrigger.TIME_OUT_POLLING_INTERVAL);
    jobRecords(JobIntent.TIMED_OUT).withProcessInstanceKey(instanceKey1).getFirst();
    ENGINE.jobs().withType(jobType).activate();

    // then
    final var jobActivations =
        jobBatchRecords(JobBatchIntent.ACTIVATED)
            .withType(jobType)
            .limit(2)
            .collect(Collectors.toList());

    final var jobKeys =
        jobActivations.stream()
            .flatMap(
                jobBatchRecordValueRecord ->
                    jobBatchRecordValueRecord.getValue().getJobKeys().stream())
            .collect(Collectors.toList());

    assertThat(jobKeys).hasSize(4).containsExactlyInAnyOrder(jobKey1, jobKey2, jobKey1, jobKey2);

    final List<Record<JobRecordValue>> expiredEvents =
        jobRecords(JobIntent.TIMED_OUT)
            .filter(
                r -> {
                  final long processInstanceKey = r.getValue().getProcessInstanceKey();
                  return processInstanceKey == instanceKey1 || processInstanceKey == instanceKey2;
                })
            .limit(2)
            .collect(Collectors.toList());

    assertThat(expiredEvents)
        .extracting(Record::getKey)
        .containsExactlyInAnyOrder(jobKey1, jobKey2);
  }

  // regression test for https://github.com/camunda/zeebe/issues/5420
  @Test
  public void shouldHaveNoSourceRecordPositionOnTimeOut() {
    final long processInstanceKey = createInstance();

    jobRecords(JobIntent.CREATED)
        .withType(jobType)
        .filter(r -> r.getValue().getProcessInstanceKey() == processInstanceKey)
        .getFirst()
        .getKey();
    final long timeout = 10L;
    ENGINE.jobs().withType(jobType).withTimeout(timeout).activate();

    // when
    jobBatchRecords(JobBatchIntent.ACTIVATED).withType(jobType).getFirst();
    ENGINE.increaseTime(JobTimeoutTrigger.TIME_OUT_POLLING_INTERVAL);
    final Record<JobRecordValue> timedOutRecord =
        jobRecords(TIME_OUT).withProcessInstanceKey(processInstanceKey).getFirst();

    // then
    assertThat(timedOutRecord.getSourceRecordPosition()).isLessThan(0);
  }

  private long createInstance() {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent("start")
                .serviceTask("task", b -> b.zeebeJobType(jobType).done())
                .endEvent("end")
                .done())
        .deploy();
    return ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
  }
}
