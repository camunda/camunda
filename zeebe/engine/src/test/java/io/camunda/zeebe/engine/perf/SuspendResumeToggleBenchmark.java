/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.perf;

import io.camunda.zeebe.engine.perf.TestEngine.TestContext;
import io.camunda.zeebe.engine.util.client.ProcessInstanceClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
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
 * Benchmarks a full suspend/resume cycle interleaved with job-activation attempts: suspend, try to
 * activate (expect none), resume, then activate for real (expect all N). Measures this as a
 * sequential round trip rather than with a multi-threaded {@code @Threads} variant, since the
 * stream processor is a single actor and serializes command processing per partition regardless.
 */
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-Xmx4g", "-Xms4g", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class SuspendResumeToggleBenchmark {

  private static final Logger LOG =
      LoggerFactory.getLogger(SuspendResumeToggleBenchmark.class.getName());

  // capped at 4000: each cycle suspends a PI with jobCount activatable jobs, and SUSPEND writes
  // Job.SUSPENDED for all of them in one command - beyond ~4000 that batch exceeds the 4MB
  // record-size limit and SUSPEND is rejected (see SuspensionBatchLimitTest, 5000 rejected).
  @Param({"10", "1000", "4000"})
  private int jobCount;

  private TestContext testContext;
  private TestEngine engine;
  private ProcessInstanceClient processInstanceClient;
  private long processInstanceKey;

  @Setup
  public void setup() throws Throwable {
    testContext = TestEngine.createTestContext();
    engine = TestEngine.createSinglePartitionEngine(testContext);

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
    // engine.reset() in the previous invocation's teardown restores the 5s default wait - raise the
    // ceiling before the CREATED await below, not only before the timed toggle. It only bounds how
    // long a wait takes to fail; it doesn't bias the timed call.
    RecordingExporter.setMaximumWaitTime(Duration.ofSeconds(30).toMillis());
    processInstanceKey = processInstanceClient.ofBpmnProcessId("process").create();

    RecordingExporter.jobRecords()
        .withIntent(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(jobCount)
        .count();

    LOG.info("PI {} ready with {} jobs.", processInstanceKey, jobCount);
  }

  @TearDown(Level.Invocation)
  public void resetAfterInvocation() {
    // engine.reset() only clears the log/exporter, not RocksDB state - cancel this invocation's
    // resumed PI (and its N activated jobs) first so state doesn't accumulate across the whole
    // trial
    processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1).cancel();
    engine.reset();
  }

  @Benchmark
  public long measureToggleWithJobActivation() {
    // onPartition(1) keeps the timed section to engine round trips only: without it the client
    // scans RecordingExporter to discover the partition before each command, adding harness work.
    processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1).suspend();

    // the default 10s lease is shorter than this trial (5s warmup + 10s measurement);
    // engine.reset()
    // doesn't clear engine state, so a job activated here and left uncompleted would time out and
    // become activatable again mid-trial, letting a later invocation's "whileSuspended" activation
    // pick up a stale job from an earlier invocation and fail its "no jobs" assertion
    final var whileSuspended =
        engine
            .createJobActivationClient()
            .withType("task")
            .withMaxJobsToActivate(jobCount)
            .withTimeout(Duration.ofMinutes(10).toMillis())
            .activate();
    if (!whileSuspended.getValue().getJobKeys().isEmpty()) {
      throw new IllegalStateException(
          "Expected no jobs to be activatable while suspended, but got %d"
              .formatted(whileSuspended.getValue().getJobKeys().size()));
    }

    processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1).resume();

    final var afterResume =
        engine
            .createJobActivationClient()
            .withType("task")
            .withMaxJobsToActivate(jobCount)
            .withTimeout(Duration.ofMinutes(10).toMillis())
            .activate();
    if (afterResume.getValue().getJobKeys().size() != jobCount) {
      throw new IllegalStateException(
          "Expected all %d jobs to be activatable after resume, but got %d"
              .formatted(jobCount, afterResume.getValue().getJobKeys().size()));
    }

    return processInstanceKey;
  }

  @JMHTest("measureToggleWithJobActivation")
  void shouldMeasureToggleLatencyWith10Jobs(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("jobCount", "10")).run();
  }

  @JMHTest("measureToggleWithJobActivation")
  void shouldMeasureToggleLatencyWith1000Jobs(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("jobCount", "1000")).run();
  }

  @JMHTest("measureToggleWithJobActivation")
  void shouldMeasureToggleLatencyWith4000Jobs(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("jobCount", "4000")).run();
  }
}
