/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.perf;

import io.camunda.zeebe.engine.perf.TestEngine.TestContext;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.engine.util.client.ProcessInstanceClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BufferedCommandRecordValue;
import io.camunda.zeebe.test.util.jmh.JMHTestCase;
import io.camunda.zeebe.test.util.junit.JMHTest;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
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
 * Benchmarks cancel() latency for a suspended process instance with N commands buffered.
 * Cancellation always proceeds and clears the buffered-command column family synchronously in one
 * scan-and-delete ({@code DbSuspensionState#clearBufferedCommands}) while terminating the root.
 *
 * <p>The active tree is a single timer catch event, so cancel()'s walk-and-terminate cost stays
 * constant as {@code bufferedCommandCount} scales - only the buffer clear itself grows. All N
 * buffered commands target that one element, since the clear doesn't apply buffer contents.
 */
// SingleShotTime with fixed iterations: the @Setup that rebuilds the whole buffer (up to 100k
// commands) dwarfs the timed clear, and a time-based window would repeat it many times per
// iteration. One measured op per iteration is enough.
@Warmup(iterations = 3, batchSize = 1)
@Measurement(iterations = 5, batchSize = 1)
@Fork(
    value = 1,
    jvmArgs = {"-Xmx4g", "-Xms4g", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class SuspendedBufferDrainBenchmark {

  private static final Logger LOG =
      LoggerFactory.getLogger(SuspendedBufferDrainBenchmark.class.getName());

  private static final int WRITE_CHUNK_SIZE = 1000;

  @Param({"1000", "10000", "100000"})
  private int bufferedCommandCount;

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
            .intermediateCatchEvent("wait", e -> e.timerWithDuration("PT1H"))
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
    LOG.info("Creating PI with 1 active element, {} buffered commands...", bufferedCommandCount);
    // engine.reset() in the previous invocation's teardown restores the 5s default wait, and the
    // await for up to 100k commands to reach BUFFERED below far exceeds it - raise the ceiling
    // before that await, not only before the timed cancel(). It only bounds how long a wait takes
    // to fail; it doesn't bias the timed call.
    RecordingExporter.setMaximumWaitTime(Duration.ofMinutes(5).toMillis());
    processInstanceKey = processInstanceClient.ofBpmnProcessId("process").create();

    final var child =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("wait")
            .getFirst();

    processInstanceClient.withInstanceKey(processInstanceKey).suspend();

    engine.writeRecordsInChunks(
        WRITE_CHUNK_SIZE,
        IntStream.range(0, bufferedCommandCount)
            .mapToObj(
                i ->
                    RecordToWrite.command()
                        .processInstance(ProcessInstanceIntent.COMPLETE_ELEMENT, child.getValue())
                        .key(child.getKey()))
            .toArray(RecordToWrite[]::new));

    // writeRecords only waits for the log append, not for these commands to be processed; wait
    // for them to actually reach BUFFERED so the benchmark measures cancel()'s buffer-clear alone
    RecordingExporter.records()
        .withValueType(ValueType.BUFFERED_COMMAND)
        .withIntent(BufferedCommandIntent.BUFFERED)
        .filter(
            r ->
                ((BufferedCommandRecordValue) r.getValue()).getProcessInstanceKey()
                    == processInstanceKey)
        .limit(bufferedCommandCount)
        .count();

    LOG.info(
        "PI {} suspended with {} commands buffered.", processInstanceKey, bufferedCommandCount);
    RecordingExporter.reset();
    RecordingExporter.setMaximumWaitTime(Duration.ofMinutes(5).toMillis());
  }

  @TearDown(Level.Invocation)
  public void resetAfterInvocation() {
    engine.reset();
  }

  @Benchmark
  public long measureBufferDrainOnCancel() {
    processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1).cancel();
    return processInstanceKey;
  }

  @JMHTest("measureBufferDrainOnCancel")
  void shouldMeasureBufferDrainOnCancelWith1kBufferedCommands(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("bufferedCommandCount", "1000")).run();
  }

  @JMHTest("measureBufferDrainOnCancel")
  void shouldMeasureBufferDrainOnCancelWith10kBufferedCommands(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("bufferedCommandCount", "10000")).run();
  }

  @JMHTest("measureBufferDrainOnCancel")
  void shouldMeasureBufferDrainOnCancelWith100kBufferedCommands(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("bufferedCommandCount", "100000")).run();
  }
}
