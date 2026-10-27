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
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
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
 * Benchmarks the time to suspend a process instance with many active elements that are message
 * subscriptions rather than jobs, contrasting with {@link SuspendJobsBenchmark}. Suspend runs two
 * independent BFS walks over the element tree: {@code ProcessInstanceSuspensionJobBehavior}
 * (suspends activatable jobs) and {@code ProcessInstanceSuspensionMessageSubscriptionBehavior}
 * (closes {@code OPENED} message subscriptions). A wide tree of parallel receive tasks exercises
 * only the second walk, isolating the per-element cost of closing a subscription from the
 * per-element cost of suspending a job. Uses the same shallow, wide (root -&gt; N parallel
 * receive-task instances) shape as {@link SuspendJobsBenchmark} so the two are directly comparable.
 */
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-Xmx4g", "-Xms4g", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class SuspendWideNonJobTreeBenchmark {

  private static final Logger LOG =
      LoggerFactory.getLogger(SuspendWideNonJobTreeBenchmark.class.getName());

  @Param({"100", "500", "1000", "2000"})
  private int subscriptionCount;

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
            .receiveTask("receive")
            .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
            .multiInstance()
            .parallel()
            .zeebeInputCollectionExpression(TestEngine.collectionExpression(subscriptionCount))
            .multiInstanceDone()
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
    LOG.info("Creating PI with {} open message subscriptions...", subscriptionCount);
    // 2000 elements' CREATED and SUSPENDED exports can each exceed the 5s default under load, and
    // engine.reset() in the previous invocation's teardown restores that default - so the ceiling
    // must be raised before the CREATED await below, not only before the timed suspend(). It only
    // bounds how long a wait takes to fail; it doesn't bias the timed call.
    RecordingExporter.setMaximumWaitTime(Duration.ofSeconds(30).toMillis());
    processInstanceKey =
        processInstanceClient.ofBpmnProcessId("process").withVariable("key", "k").create();

    RecordingExporter.processMessageSubscriptionRecords()
        .withIntent(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(subscriptionCount)
        .count();

    LOG.info("PI {} ready with {} subscriptions.", processInstanceKey, subscriptionCount);
  }

  @TearDown(Level.Invocation)
  public void resetAfterInvocation() {
    // each suspend()'s close buffers a REOPEN once its async ack lands - wait for all of them so
    // that trailing work can't race the next invocation's own subscription-CREATED wait.
    TestEngine.awaitReopenBuffered(processInstanceKey, subscriptionCount);
    // engine.reset() only clears the log/exporter, not RocksDB state - cancel this invocation's
    // suspended PI (and its N subscriptions) first so state doesn't accumulate across the whole
    // trial
    processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1).cancel();
    // cancel() returns after the root's own termination, but each subscription's close is a
    // separate async round trip - wait for all N DELETED acks too before resetting.
    RecordingExporter.processMessageSubscriptionRecords()
        .withIntent(ProcessMessageSubscriptionIntent.DELETED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(subscriptionCount)
        .count();
    engine.reset();
  }

  @Benchmark
  public long measureSuspendTime() {
    // onPartition(1) keeps the timed section to the engine's subscription walk only, and keeps this
    // comparable with SuspendJobsBenchmark: without it the client scans RecordingExporter to
    // discover the partition first, adding harness work to the measurement.
    processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1).suspend();
    return processInstanceKey;
  }

  @JMHTest("measureSuspendTime")
  void shouldMeasureSuspendLatencyWith100Subscriptions(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("subscriptionCount", "100")).run();
  }

  @JMHTest("measureSuspendTime")
  void shouldMeasureSuspendLatencyWith500Subscriptions(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("subscriptionCount", "500")).run();
  }

  @JMHTest("measureSuspendTime")
  void shouldMeasureSuspendLatencyWith1000Subscriptions(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("subscriptionCount", "1000")).run();
  }

  @JMHTest("measureSuspendTime")
  void shouldMeasureSuspendLatencyWith2000Subscriptions(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("subscriptionCount", "2000")).run();
  }
}
