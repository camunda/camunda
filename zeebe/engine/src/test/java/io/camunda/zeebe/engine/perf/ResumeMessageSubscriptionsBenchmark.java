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
 * Benchmarks the time to resume a process instance with many suspended message subscriptions. This
 * is the reverse of {@link SuspendMessageSubscriptionsBenchmark}: suspending closes each
 * subscription and, once its close is acknowledged, buffers a {@code REOPEN} command for it. Resume
 * drains that buffer one command per cycle ({@code BufferedCommandDrainProcessor}), and each {@code
 * REOPEN} re-subscribes from the resume manifest and sends the open command to the message
 * partition. Latency is therefore expected to scale with N sequential round-trips. {@link
 * ResumeBufferDrainBenchmark} drains the same buffer, but with injected element commands instead of
 * {@code REOPEN}.
 *
 * <p>The timed section ends with {@code RESUMED} and does not wait for all open acknowledgements.
 * On this single-partition engine the message partition is the same partition, so opens and
 * acknowledgements already processed in between drain cycles are part of the measured time; the
 * ones still outstanding at {@code RESUMED} are not.
 */
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-Xmx4g", "-Xms4g", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class ResumeMessageSubscriptionsBenchmark {

  private static final Logger LOG =
      LoggerFactory.getLogger(ResumeMessageSubscriptionsBenchmark.class.getName());

  private static final long MAX_WAIT_TIME_MS = Duration.ofSeconds(60).toMillis();

  @Param({"100", "500", "1000", "2000", "5000", "10000"})
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
  public void setupSuspendedProcessInstance() {
    LOG.info("Creating PI with {} message subscriptions, then suspending...", subscriptionCount);
    // engine.reset() in the previous invocation's teardown restores the 5s default wait, which the
    // CREATED, SUSPENDED and REOPEN awaits below can exceed at 10000 subscriptions. It only bounds
    // how long a wait takes to fail; it doesn't bias the timed call.
    RecordingExporter.setMaximumWaitTime(MAX_WAIT_TIME_MS);
    processInstanceKey =
        processInstanceClient.ofBpmnProcessId("process").withVariable("key", "k").create();

    RecordingExporter.processMessageSubscriptionRecords()
        .withIntent(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(subscriptionCount)
        .count();

    processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1).suspend();
    // SUSPENDED can be written before the close acks arrive; only an acked close buffers the
    // REOPEN that resume drains, so wait for all of them to time a full drain.
    TestEngine.awaitReopenBuffered(processInstanceKey, subscriptionCount);

    LOG.info("PI {} suspended with {} subscriptions.", processInstanceKey, subscriptionCount);
    // the timed call waits for RESUMED by scanning the exporter from the start - drop the setup
    // records so that scan doesn't grow with subscriptionCount. reset() also restores the 5s
    // default wait, so raise it again for the timed call.
    RecordingExporter.reset();
    RecordingExporter.setMaximumWaitTime(MAX_WAIT_TIME_MS);
  }

  @TearDown(Level.Invocation)
  public void resetAfterInvocation() {
    // each drained REOPEN re-opens its subscription with an async round trip to the message
    // partition - wait for all of them, so a late open can't race the cancel below or the next
    // invocation's own subscription-CREATED wait.
    RecordingExporter.processMessageSubscriptionRecords()
        .withIntent(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(subscriptionCount)
        .count();
    // engine.reset() only clears the log/exporter, not RocksDB state - cancel this invocation's PI
    // (and its N subscriptions) first so state doesn't accumulate across the whole trial
    processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1).cancel();
    RecordingExporter.processMessageSubscriptionRecords()
        .withIntent(ProcessMessageSubscriptionIntent.DELETED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(subscriptionCount)
        .count();
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
  void shouldMeasureResumeLatencyWith100Subscriptions(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("subscriptionCount", "100")).run();
  }

  @JMHTest("measureResumeTime")
  void shouldMeasureResumeLatencyWith1000Subscriptions(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("subscriptionCount", "1000")).run();
  }
}
