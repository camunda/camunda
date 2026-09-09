/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.perf;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.perf.TestEngine.TestContext;
import io.camunda.zeebe.engine.util.client.ProcessInstanceClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.test.util.jmh.JMHTestCase;
import io.camunda.zeebe.test.util.junit.JMHTest;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Benchmarks job-timeout handling for jobs that were activated (handed to a worker) before their
 * process instance was suspended. Setup creates N parallel jobs, activates all of them (so none
 * remain activatable, and suspend leaves them untouched), suspends the PI, and the benchmark
 * advances the clock past the job timeout, measuring the time until all N jobs are parked ({@code
 * Job.SUSPENDED}).
 */
// SingleShotTime with fixed iterations: the @Setup (re-create, activate and suspend up to
// 100k jobs) dwarfs the timed burst, and a time-based window would repeat it many times per
// iteration. One measured op per iteration is enough.
@Warmup(iterations = 3, batchSize = 1)
@Measurement(iterations = 5, batchSize = 1)
@Fork(
    value = 1,
    jvmArgs = {"-Xmx4g", "-Xms4g", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class SuspendedJobTimeoutBenchmark {

  private static final Logger LOG =
      LoggerFactory.getLogger(SuspendedJobTimeoutBenchmark.class.getName());

  private static final Duration JOB_TIMEOUT = Duration.ofSeconds(30);
  // one activate() call returns its whole batch in a single record, subject to the 4MB limit;
  // activate in chunks so the setup can hand out 100k jobs without exceeding it
  private static final int ACTIVATE_CHUNK_SIZE = 1000;

  @Param({"1000", "10000", "100000"})
  private int jobCount;

  private TestContext testContext;
  private TestEngine engine;
  private ControlledActorClock clock;
  private ProcessInstanceClient processInstanceClient;
  private long processInstanceKey;

  @Setup
  public void setup() throws Throwable {
    clock = new ControlledActorClock();
    testContext = TestEngine.createTestContext(clock);
    engine = TestEngine.createSinglePartitionEngine(testContext, clock);

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("task")
                        .multiInstance()
                        .parallel()
                        .zeebeInputCollectionExpression(TestEngine.collectionExpression(jobCount))
                        .multiInstanceDone()
                        .done())
            .endEvent()
            .done();

    engine.createDeploymentClient().withXmlResource(process).deploy();
    processInstanceClient = engine.createProcessInstanceClient();
  }

  @TearDown
  public void tearDown() {
    testContext.close();
  }

  @Setup(Level.Invocation)
  public void setupProcessInstance() {
    LOG.info("Creating PI with {} jobs...", jobCount);
    // engine.reset() in the previous invocation's teardown restores the 5s default wait, and
    // creating/activating up to 100k jobs below far exceeds it - raise the ceiling before them, not
    // only before the timed burst. It only bounds how long a wait takes to fail; it doesn't bias
    // the timed call.
    RecordingExporter.setMaximumWaitTime(Duration.ofMinutes(5).toMillis());
    processInstanceKey = processInstanceClient.ofBpmnProcessId("process").create();

    RecordingExporter.jobRecords()
        .withIntent(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(jobCount)
        .count();

    LOG.info("Activating all {} jobs (handing them out to a worker)...", jobCount);
    // the controlled clock doesn't advance during setup, so already-activated jobs keep their lease
    // and stay non-activatable - each chunk therefore hands out the next distinct batch of jobs
    int activated = 0;
    while (activated < jobCount) {
      final int inBatch =
          engine
              .createJobActivationClient()
              .withType("task")
              .withMaxJobsToActivate(Math.min(ACTIVATE_CHUNK_SIZE, jobCount - activated))
              .withTimeout(JOB_TIMEOUT.toMillis())
              .activate()
              .getValue()
              .getJobKeys()
              .size();
      if (inBatch == 0) {
        throw new IllegalStateException(
            "Expected to activate %d jobs but stalled after %d".formatted(jobCount, activated));
      }
      activated += inBatch;
    }

    processInstanceClient.withInstanceKey(processInstanceKey).suspend();

    LOG.info("PI {} suspended with {} activated jobs.", processInstanceKey, jobCount);
    RecordingExporter.reset();
    RecordingExporter.setMaximumWaitTime(Duration.ofMinutes(5).toMillis());
  }

  @TearDown(Level.Invocation)
  public void resetAfterInvocation() {
    // engine.reset() only clears the log/exporter, not RocksDB state - cancel this invocation's
    // suspended PI (and its N parked jobs) first so state doesn't accumulate across the whole
    // trial
    processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1).cancel();
    engine.reset();
  }

  @Benchmark
  public long measureSuspendedJobTimeoutBurst() {
    clock.addTime(JOB_TIMEOUT.plus(EngineConfiguration.DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL));

    RecordingExporter.jobRecords()
        .withIntent(JobIntent.SUSPENDED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(jobCount)
        .count();

    return processInstanceKey;
  }

  @JMHTest("measureSuspendedJobTimeoutBurst")
  void shouldMeasureSuspendedJobTimeoutBurstWith1kJobs(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("jobCount", "1000")).run();
  }

  @JMHTest("measureSuspendedJobTimeoutBurst")
  void shouldMeasureSuspendedJobTimeoutBurstWith10kJobs(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("jobCount", "10000")).run();
  }

  @JMHTest("measureSuspendedJobTimeoutBurst")
  void shouldMeasureSuspendedJobTimeoutBurstWith100kJobs(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("jobCount", "100000")).run();
  }
}
