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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
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
 * Benchmarks job activation throughput with and without suspended jobs in state. Suspended jobs are
 * removed from the activatable-by-priority index, so JobBatchActivateProcessor should not be
 * affected by their presence. This is a negative benchmark confirming the index design works.
 */
@Warmup(iterations = 50, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 25, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-Xmx4g", "-Xms4g", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class JobActivationWithSuspendedJobsBenchmark {

  private static final Logger LOG =
      LoggerFactory.getLogger(JobActivationWithSuspendedJobsBenchmark.class.getName());

  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask("task", t -> t.zeebeJobType("task").done())
          .endEvent()
          .done();

  @Param({"0", "10000", "100000"})
  private int suspendedJobCount;

  private TestContext testContext;
  private TestEngine engine;
  private ProcessInstanceClient processInstanceClient;
  private long piKey;
  private Record<JobBatchRecordValue> lastActivation;

  @Setup
  public void setup() throws Throwable {
    testContext = TestEngine.createTestContext();
    engine = TestEngine.createSinglePartitionEngine(testContext);

    engine.createDeploymentClient().withXmlResource(PROCESS).deploy();
    processInstanceClient = engine.createProcessInstanceClient();

    LOG.info("Creating {} PIs to suspend (one job each)...", suspendedJobCount);
    for (int i = 0; i < suspendedJobCount; i++) {
      final long suspendedPiKey = processInstanceClient.ofBpmnProcessId("process").create();

      RecordingExporter.jobRecords()
          .withIntent(JobIntent.CREATED)
          .withProcessInstanceKey(suspendedPiKey)
          .getFirst();

      processInstanceClient.withInstanceKey(suspendedPiKey).suspend();

      RecordingExporter.reset();
      if (i > 0 && i % 10000 == 0) {
        LOG.info("\t{} PIs suspended.", i);
        engine.reset();
      }
    }

    LOG.info("Setup complete. {} suspended jobs in state.", suspendedJobCount);
    engine.reset();
  }

  @TearDown
  public void tearDown() {
    testContext.close();
  }

  @Setup(Level.Invocation)
  public void setupProcessInstance() {
    piKey = processInstanceClient.ofBpmnProcessId("process").create();

    RecordingExporter.jobRecords()
        .withIntent(JobIntent.CREATED)
        .withType("task")
        .withProcessInstanceKey(piKey)
        .getFirst();
  }

  @TearDown(Level.Invocation)
  public void resetAfterInvocation() {
    // an empty batch (e.g. a regression in the suspended-job index) would otherwise read as
    // improved throughput instead of failing; check here rather than in the timed benchmark
    // method so validation doesn't add to the measured section
    final var activatedJobs = lastActivation.getValue().getJobs();
    if (activatedJobs.size() != 1) {
      throw new IllegalStateException(
          "Expected exactly 1 job to be activated, but got %d".formatted(activatedJobs.size()));
    }
    // the count alone can't catch the regression this benchmark targets: if a preloaded suspended
    // job leaked back into the activatable index, activation could still return exactly one job -
    // the wrong one - while this PI's job stays queued. Assert the job belongs to this invocation.
    final long activatedPiKey = activatedJobs.getFirst().getProcessInstanceKey();
    if (activatedPiKey != piKey) {
      throw new IllegalStateException(
          "Expected the activated job to belong to PI %d, but it belonged to %d"
              .formatted(piKey, activatedPiKey));
    }

    // engine.reset() only clears the log/exporter, not RocksDB state - cancel this invocation's
    // created PI (and its activated job) first so state doesn't accumulate across the whole trial
    processInstanceClient.withInstanceKey(piKey).onPartition(1).cancel();
    engine.reset();
  }

  @Benchmark
  public Record<JobBatchRecordValue> measureJobActivationThroughput() {
    // Timeout must outlast the whole trial (warmup + measurement); otherwise an activated job
    // times out mid-run, becomes activatable again, and gets mixed into later invocations.
    lastActivation =
        engine
            .createJobActivationClient()
            .withType("task")
            .withMaxJobsToActivate(1)
            .withTimeout(Duration.ofMinutes(10).toMillis())
            .activate();
    return lastActivation;
  }

  @JMHTest("measureJobActivationThroughput")
  void shouldMeasureJobActivationWith0SuspendedJobs(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("suspendedJobCount", "0")).run();
  }

  @JMHTest("measureJobActivationThroughput")
  void shouldMeasureJobActivationWith10kSuspendedJobs(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("suspendedJobCount", "10000")).run();
  }

  @JMHTest("measureJobActivationThroughput")
  void shouldMeasureJobActivationWith100kSuspendedJobs(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("suspendedJobCount", "100000")).run();
  }
}
