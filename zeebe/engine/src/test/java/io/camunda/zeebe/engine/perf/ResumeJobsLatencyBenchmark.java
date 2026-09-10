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
 * Benchmarks the time to resume a process instance with many jobs suspended. This is the reverse of
 * {@link SuspendJobsBenchmark}: unlike suspend, which walks the whole element tree and appends
 * {@code Job.SUSPENDED} for every activatable job in a single command, resume drains one job per
 * {@code RESUME_JOBS} command cycle ({@code ProcessInstanceResumeJobsProcessor}), each its own
 * follow-up command and log append. Latency is therefore expected to scale with N sequential
 * round-trips through the stream processor, not with a single O(N) tree walk.
 */
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-Xmx4g", "-Xms4g", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class ResumeJobsLatencyBenchmark {

  private static final Logger LOG =
      LoggerFactory.getLogger(ResumeJobsLatencyBenchmark.class.getName());

  // capped at 4000: setup suspends a PI with jobCount activatable jobs, and SUSPEND writes
  // Job.SUSPENDED for all of them in one command - beyond ~4000 that batch exceeds the 4MB
  // record-size limit and SUSPEND is rejected (see SuspensionBatchLimitTest, 5000 rejected).
  @Param({"100", "1000", "2000", "4000"})
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
  public void setupSuspendedProcessInstance() {
    LOG.info("Creating PI with {} jobs, then suspending...", jobCount);
    // 2000 jobs' SUSPENDED export can exceed the 5s default under load, same as
    // SuspendJobsBenchmark; suspend() itself (not just the timed resume() below) needs the
    // raised ceiling, so it must be set before calling it, not only after
    RecordingExporter.setMaximumWaitTime(Duration.ofSeconds(60).toMillis());
    processInstanceKey = processInstanceClient.ofBpmnProcessId("process").create();

    RecordingExporter.jobRecords()
        .withIntent(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(jobCount)
        .count();

    processInstanceClient.withInstanceKey(processInstanceKey).suspend();

    LOG.info("PI {} suspended with {} jobs.", processInstanceKey, jobCount);
    RecordingExporter.reset();
    // resume drains one job per RESUME_JOBS cycle instead of one batch write, so completion time
    // scales with jobCount sequential round-trips; this ceiling only bounds how long the timed
    // resume() call waits to fail, it doesn't bias a successful result
    RecordingExporter.setMaximumWaitTime(Duration.ofSeconds(60).toMillis());
  }

  @TearDown(Level.Invocation)
  public void resetAfterInvocation() {
    // engine.reset() only clears the log/exporter, not RocksDB state - cancel this invocation's
    // resumed PI (and its N now-activatable jobs) first so state doesn't accumulate across the
    // whole trial
    processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1).cancel();
    engine.reset();
  }

  @Benchmark
  public long measureResumeTime() {
    // onPartition(1) avoids resume()'s fallback partition lookup, which reads back an already
    // exported process instance record for this key - a record that no longer exists once
    // RecordingExporter.reset() has run in setup, above
    processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1).resume();
    return processInstanceKey;
  }

  @JMHTest("measureResumeTime")
  void shouldMeasureResumeLatencyWith100Jobs(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("jobCount", "100")).run();
  }

  @JMHTest("measureResumeTime")
  void shouldMeasureResumeLatencyWith1000Jobs(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("jobCount", "1000")).run();
  }

  @JMHTest("measureResumeTime")
  void shouldMeasureResumeLatencyWith2000Jobs(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("jobCount", "2000")).run();
  }

  @JMHTest("measureResumeTime")
  void shouldMeasureResumeLatencyWith4000Jobs(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("jobCount", "4000")).run();
  }
}
