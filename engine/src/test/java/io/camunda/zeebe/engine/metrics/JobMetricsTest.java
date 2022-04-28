/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.metrics;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class JobMetricsTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String TASK_ID = "task";
  private static final String JOB_TYPE = "job";

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @BeforeClass
  public static void deployProcess() {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(TASK_ID, t -> t.zeebeJobTypeExpression("jobType"))
                .endEvent()
                .done())
        .deploy();
  }

  @Before
  public void resetMetrics() {
    JobMetrics.clear();
  }

  @Test
  public void allCountsStartAtNull() {
    assertThat(jobMetric("created", JOB_TYPE)).isNull();
    assertThat(jobMetric("activated", JOB_TYPE)).isNull();
    assertThat(jobMetric("timed out", JOB_TYPE)).isNull();
    assertThat(jobMetric("completed", JOB_TYPE)).isNull();
    assertThat(jobMetric("failed", JOB_TYPE)).isNull();
    assertThat(jobMetric("canceled", JOB_TYPE)).isNull();
    assertThat(jobMetric("error thrown", JOB_TYPE)).isNull();
  }

  @Test
  public void shouldCountCreated() {
    // when
    createProcessInstanceWithJob(JOB_TYPE);

    // then
    assertThat(jobMetric("created", JOB_TYPE)).isNotNull().isEqualTo(1);
  }

  @Test
  public void shouldCountActivated() {
    // given

    // the job type must be unique, because other tests may also have created jobs that can be
    // activated. We can't depend on the unique process instance when activating a batch of jobs.
    final String jobType = JOB_TYPE + "_activated";
    createProcessInstanceWithJob(jobType);

    // when
    ENGINE.jobs().withType(jobType).activate();

    // then
    assertThat(jobMetric("activated", jobType)).isNotNull().isEqualTo(1);
  }

  @Test
  public void shouldCountTimedOut() {
    // given
    final long processInstanceKey = createProcessInstanceWithJob(JOB_TYPE);

    final var timeout = Duration.ofMinutes(10);
    ENGINE.jobs().withType(JOB_TYPE).withTimeout(timeout.toMillis()).activate();

    // when
    ENGINE.getClock().addTime(timeout);
    RecordingExporter.jobRecords(JobIntent.TIMED_OUT)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // then
    assertThat(jobMetric("timed out", JOB_TYPE)).isNotNull().isEqualTo(1);
  }

  @Test
  public void shouldCountCompleted() {
    // given
    final long processInstanceKey = createProcessInstanceWithJob(JOB_TYPE);

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(JOB_TYPE).complete();

    // then
    assertThat(jobMetric("completed", JOB_TYPE)).isNotNull().isEqualTo(1);
  }

  @Test
  public void shouldCountFailed() {
    // given
    final long processInstanceKey = createProcessInstanceWithJob(JOB_TYPE);

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(JOB_TYPE).fail();

    // then
    assertThat(jobMetric("failed", JOB_TYPE)).isNotNull().isEqualTo(1);
  }

  @Test
  public void shouldCountCanceled() {
    // given
    final long processInstanceKey = createProcessInstanceWithJob(JOB_TYPE);

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();
    RecordingExporter.jobRecords(JobIntent.CANCELED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // then
    assertThat(jobMetric("canceled", JOB_TYPE)).isNotNull().isEqualTo(1);
  }

  @Test
  public void shouldCountErrorThrown() {
    // given
    final long processInstanceKey = createProcessInstanceWithJob(JOB_TYPE);

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(JOB_TYPE).throwError();

    // then
    assertThat(jobMetric("error thrown", JOB_TYPE)).isNotNull().isEqualTo(1);
  }

  /**
   * Creates a process instance with a job, and waits until the job is created
   *
   * @param jobType the job type for the service task
   * @return the key of the created process instance
   */
  private static long createProcessInstanceWithJob(final String jobType) {
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("jobType", jobType)
            .create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    return processInstanceKey;
  }

  private static Double jobMetric(final String action, final String type) {
    return MetricsTestHelper.readMetricValue(
        "zeebe_job_events_total",
        entry("action", action),
        entry("partition", "1"),
        entry("type", type));
  }
}
