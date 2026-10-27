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
import io.camunda.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.test.util.jmh.JMHTestCase;
import io.camunda.zeebe.test.util.junit.JMHTest;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
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
 * Benchmarks the cost of the suspend tree-walk itself, isolated from per-element suspend cost. The
 * walk ({@code ElementInstanceState#getChildren}) is BFS and visits every element instance,
 * including subprocess wrappers with nothing to suspend, one RocksDB prefix-scan per level. A wide
 * tree ({@link SuspendJobsBenchmark}, {@link SuspendWideNonJobTreeBenchmark}) needs only one such
 * call to reach all N leaves; N levels of nesting need N sequential ones. The innermost subprocess
 * holds a receive task so the chain stays active long enough to suspend.
 */
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-Xmx4g", "-Xms4g", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class SuspendTreeWalkDepthBenchmark {

  private static final Logger LOG =
      LoggerFactory.getLogger(SuspendTreeWalkDepthBenchmark.class.getName());

  @Param({"10", "50", "100", "200"})
  private int nestingDepth;

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
            .subProcess("nested-" + nestingDepth, nestedSubProcess(nestingDepth))
            .endEvent()
            .done();

    engine.createDeploymentClient().withXmlResource(process).deploy();
    processInstanceClient = engine.createProcessInstanceClient();
  }

  private static Consumer<SubProcessBuilder> nestedSubProcess(final int remainingDepth) {
    if (remainingDepth <= 1) {
      return subProcess ->
          subProcess
              .embeddedSubProcess()
              .startEvent()
              .receiveTask("receive")
              .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
              .endEvent();
    }
    return subProcess ->
        subProcess
            .embeddedSubProcess()
            .startEvent()
            .subProcess("nested-" + (remainingDepth - 1), nestedSubProcess(remainingDepth - 1))
            .endEvent();
  }

  @TearDown
  public void tearDown() {
    testContext.close();
  }

  @Setup(Level.Invocation)
  public void setupProcessInstance() {
    LOG.info("Creating PI with {} levels of nested subprocesses...", nestingDepth);
    processInstanceKey =
        processInstanceClient.ofBpmnProcessId("process").withVariable("key", "k").create();

    RecordingExporter.processMessageSubscriptionRecords()
        .withIntent(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();

    LOG.info("PI {} ready at nesting depth {}.", processInstanceKey, nestingDepth);
    RecordingExporter.setMaximumWaitTime(Duration.ofSeconds(30).toMillis());
  }

  @TearDown(Level.Invocation)
  public void resetAfterInvocation() {
    // suspend()'s subscription close is async and later buffers a REOPEN once its ack lands -
    // wait for that here so it can't bleed into the next invocation's own CREATED wait.
    TestEngine.awaitReopenBuffered(processInstanceKey, 1);
    // engine.reset() only clears the log/exporter, not RocksDB state - cancel this invocation's
    // suspended PI (and its nested subprocess/subscription) first so state doesn't accumulate
    // across the whole trial
    processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1).cancel();
    // cancel() returns after the root's own termination, but its subscription close is a separate
    // async round trip - wait for the DELETED ack too, so it can't bleed into the next invocation.
    RecordingExporter.processMessageSubscriptionRecords()
        .withIntent(ProcessMessageSubscriptionIntent.DELETED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
    engine.reset();
  }

  @Benchmark
  public long measureSuspendTime() {
    // onPartition(1) keeps the timed section to the engine's tree walk only: without it the client
    // scans RecordingExporter to discover the partition first, adding harness work that especially
    // distorts the shallow-depth measurements.
    processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1).suspend();
    return processInstanceKey;
  }

  @JMHTest("measureSuspendTime")
  void shouldMeasureSuspendLatencyAtDepth10(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("nestingDepth", "10")).run();
  }

  @JMHTest("measureSuspendTime")
  void shouldMeasureSuspendLatencyAtDepth50(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("nestingDepth", "50")).run();
  }

  @JMHTest("measureSuspendTime")
  void shouldMeasureSuspendLatencyAtDepth100(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("nestingDepth", "100")).run();
  }

  @JMHTest("measureSuspendTime")
  void shouldMeasureSuspendLatencyAtDepth200(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("nestingDepth", "200")).run();
  }
}
