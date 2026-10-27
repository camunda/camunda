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
 * Benchmarks the time to suspend a process instance with many active elements. Uses a parallel
 * multi-instance service task to create PIs with N active jobs. The suspend operation BFS-walks all
 * element instances and writes Job.SUSPENDED for each activatable job.
 */
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-Xmx4g", "-Xms4g", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class SuspendJobsBenchmark {

  private static final Logger LOG = LoggerFactory.getLogger(SuspendJobsBenchmark.class.getName());

  // 4000 is the largest count that still suspends: SUSPEND writes Job.SUSPENDED for every
  // activatable job in one command, and beyond ~4000 that batch exceeds the 4MB record-size limit
  // (see SuspensionBatchLimitTest, where 5000 is rejected).
  @Param({"100", "1000", "2000", "4000"})
  private int activeElementCount;

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
                        .zeebeInputCollectionExpression(
                            TestEngine.collectionExpression(activeElementCount))
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
    LOG.info("Creating PI with {} active elements...", activeElementCount);
    // 2000 elements' CREATED and SUSPENDED exports can each exceed the 5s default under load, and
    // engine.reset() in the previous invocation's teardown restores that default - so the ceiling
    // must be raised before the CREATED await below, not only before the timed suspend(). It only
    // bounds how long a wait takes to fail; it doesn't bias the timed call.
    RecordingExporter.setMaximumWaitTime(Duration.ofSeconds(30).toMillis());
    processInstanceKey = processInstanceClient.ofBpmnProcessId("process").create();

    RecordingExporter.jobRecords()
        .withIntent(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(activeElementCount)
        .count();

    LOG.info("PI {} ready with {} jobs.", processInstanceKey, activeElementCount);
  }

  @TearDown(Level.Invocation)
  public void resetAfterInvocation() {
    // engine.reset() only clears the log/exporter, not RocksDB state - cancel this invocation's
    // suspended PI (and its N jobs) first so state doesn't accumulate across the whole trial
    processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1).cancel();
    engine.reset();
  }

  @Benchmark
  public long measureSuspendTime() {
    processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1).suspend();
    return processInstanceKey;
  }

  @JMHTest("measureSuspendTime")
  void shouldMeasureSuspendLatencyWith100ActiveElements(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("activeElementCount", "100")).run();
  }

  @JMHTest("measureSuspendTime")
  void shouldMeasureSuspendLatencyWith1000ActiveElements(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("activeElementCount", "1000")).run();
  }

  @JMHTest("measureSuspendTime")
  void shouldMeasureSuspendLatencyWith2000ActiveElements(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("activeElementCount", "2000")).run();
  }

  @JMHTest("measureSuspendTime")
  void shouldMeasureSuspendLatencyWith4000ActiveElements(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("activeElementCount", "4000")).run();
  }
}
