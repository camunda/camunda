/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assume.assumeThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.micrometer.core.instrument.Counter;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestWatcher;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JobProcessingMetricsTest {

  private static final String PROCESS_ID = "process";
  private static final String TASK_ID = "task";
  private static final String JOB_TYPE = "job";

  @Parameter public JobMetricsTestScenario scenario;

  private final TestWatcher watcher = new RecordingExporterTestWatcher();
  private final EngineRule engine = EngineRule.singlePartition();

  @Rule public final RuleChain ruleChain = RuleChain.outerRule(engine).around(watcher);

  @Parameters(name = "{index}: {0}")
  public static Collection<Object[]> parameters() {
    return Arrays.asList(
        new Object[][] {
          {
            JobMetricsTestScenario.of(
                JobKind.BPMN_ELEMENT,
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .serviceTask(TASK_ID, t -> t.zeebeJobTypeExpression("jobType"))
                    .endEvent()
                    .done())
          },
          {
            JobMetricsTestScenario.of(
                JobKind.EXECUTION_LISTENER,
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .manualTask(TASK_ID)
                    .zeebeExecutionListener(el -> el.start().typeExpression("jobType"))
                    .endEvent()
                    .done())
          }
        });
  }

  @Test
  public void allCountsStartAtNull() {
    assertThat(findJobCounter("created", JOB_TYPE, scenario.jobKind)).isEmpty();
    assertThat(findJobCounter("activated", JOB_TYPE, scenario.jobKind)).isEmpty();
    assertThat(findJobCounter("timed out", JOB_TYPE, scenario.jobKind)).isEmpty();
    assertThat(findJobCounter("completed", JOB_TYPE, scenario.jobKind)).isEmpty();
    assertThat(findJobCounter("failed", JOB_TYPE, scenario.jobKind)).isEmpty();
    assertThat(findJobCounter("canceled", JOB_TYPE, scenario.jobKind)).isEmpty();
    assertThat(findJobCounter("error thrown", JOB_TYPE, scenario.jobKind)).isEmpty();
  }

  @Test
  public void shouldCountCreated() {
    // when
    engine.deployment().withXmlResource(scenario.process).deploy();
    createProcessInstanceWithJob(JOB_TYPE);

    // then
    assertThat(jobMetric("created", JOB_TYPE, scenario.jobKind)).isOne();
  }

  @Test
  public void shouldCountActivated() {
    // given
    engine.deployment().withXmlResource(scenario.process).deploy();

    // the job type must be unique, because other tests may also have created jobs that can be
    // activated. We can't depend on the unique process instance when activating a batch of jobs.
    final String jobType = String.format("%s_%s_activated", JOB_TYPE, scenario.jobKind);
    createProcessInstanceWithJob(jobType);

    // when
    engine.jobs().withType(jobType).activate();

    // then
    assertThat(jobMetric("activated", jobType, scenario.jobKind)).isOne();
  }

  @Test
  public void shouldCountTimedOut() {
    // given
    engine.deployment().withXmlResource(scenario.process).deploy();
    final long processInstanceKey = createProcessInstanceWithJob(JOB_TYPE);

    final var timeout = Duration.ofMinutes(10);
    final var jobRecord =
        engine
            .jobs()
            .withType(JOB_TYPE)
            .withTimeout(timeout.toMillis())
            .activate()
            .getValue()
            .getJobs()
            .getFirst();

    // when
    // We need to add 1 ms as the deadline needs to be < the current time. Without the extra 1 ms
    // it could be that the JobTimeoutChecker is triggered at the exact same time as the job
    // deadline resulting in the Job activation not being expired yet.
    engine
        .getClock()
        .addTime(
            Duration.ofMillis(
                jobRecord.getDeadline() - engine.getClock().getCurrentTimeInMillis() + 1));
    RecordingExporter.jobRecords(JobIntent.TIMED_OUT)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // then
    assertThat(jobMetric("timed out", JOB_TYPE, scenario.jobKind)).isOne();
  }

  @Test
  public void shouldCountCompleted() {
    // given
    engine.deployment().withXmlResource(scenario.process).deploy();
    final long processInstanceKey = createProcessInstanceWithJob(JOB_TYPE);

    // when
    engine.job().ofInstance(processInstanceKey).withType(JOB_TYPE).complete();

    // then
    assertThat(jobMetric("completed", JOB_TYPE, scenario.jobKind)).isOne();
  }

  @Test
  public void shouldCountFailed() {
    // given
    engine.deployment().withXmlResource(scenario.process).deploy();
    final long processInstanceKey = createProcessInstanceWithJob(JOB_TYPE);

    // when
    engine.job().ofInstance(processInstanceKey).withType(JOB_TYPE).fail();

    // then
    assertThat(jobMetric("failed", JOB_TYPE, scenario.jobKind)).isOne();
  }

  @Test
  public void shouldCountCanceled() {
    // given
    engine.deployment().withXmlResource(scenario.process).deploy();
    final long processInstanceKey = createProcessInstanceWithJob(JOB_TYPE);

    // when
    engine.processInstance().withInstanceKey(processInstanceKey).cancel();
    RecordingExporter.jobRecords(JobIntent.CANCELED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // then
    assertThat(jobMetric("canceled", JOB_TYPE, scenario.jobKind)).isOne();
  }

  @Test
  public void shouldCountErrorThrown() {
    // Skip test for `JobKind.EXECUTION_LISTENER` as error throwing functionality
    // is not supported for this job kind.
    assumeThat(scenario.jobKind, is(not(equalTo(JobKind.EXECUTION_LISTENER))));

    // given
    engine.deployment().withXmlResource(scenario.process).deploy();
    final long processInstanceKey = createProcessInstanceWithJob(JOB_TYPE);

    // when
    engine.job().ofInstance(processInstanceKey).withType(JOB_TYPE).throwError();

    // then
    assertThat(jobMetric("error thrown", JOB_TYPE, scenario.jobKind)).isOne();
  }

  /**
   * Creates a process instance with a job, and waits until the job is created
   *
   * @param jobType the job type for the service task
   * @return the key of the created process instance
   */
  private long createProcessInstanceWithJob(final String jobType) {
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("jobType", jobType)
            .create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    return processInstanceKey;
  }

  private Optional<Counter> findJobCounter(
      final String action, final String type, final JobKind kind) {
    return Optional.ofNullable(
        engine
            .getMeterRegistry()
            .find("zeebe.job.events.total")
            .tag("action", action)
            .tag("partition", "1")
            .tag("type", type)
            .tag("job_kind", kind.name())
            .counter());
  }

  private double jobMetric(final String action, final String type, final JobKind kind) {
    return engine
        .getMeterRegistry()
        .get("zeebe.job.events.total")
        .tag("action", action)
        .tag("partition", "1")
        .tag("type", type)
        .tag("job_kind", kind.name())
        .counter()
        .count();
  }

  record JobMetricsTestScenario(JobKind jobKind, BpmnModelInstance process) {
    @Override
    public String toString() {
      return jobKind.name();
    }

    private static JobMetricsTestScenario of(
        final JobKind jobKind, final BpmnModelInstance process) {
      return new JobMetricsTestScenario(jobKind, process);
    }
  }
}
