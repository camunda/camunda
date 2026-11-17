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
 * Benchmarks how long a large command buffer takes to drain on resume. Resume drains one buffered
 * command per {@code DRAIN} cycle ({@code BufferedCommandDrainProcessor}) - each its own follow-up
 * command, log append and RocksDB seek - so latency is expected to scale with the buffered count as
 * sequential round-trips, not as a single batch operation. This contrasts with {@link
 * SuspendedBufferDrainBenchmark}, which clears the same kind of buffer in one synchronous
 * scan-and-delete on cancel.
 *
 * <p>The process forks into two parallel timer catch events. Setup suspends the instance, then
 * injects {@code bufferedCommandCount} {@code COMPLETE_ELEMENT} commands for the first timer
 * directly into the buffer (mirroring {@code
 * ResumeProcessInstanceDrainTest#bufferCompleteCommands}) - the only way to populate the buffer,
 * since real completions are rejected while suspended rather than buffered. The second timer never
 * fires and keeps the instance alive through the whole drain, so {@code RESUMED} - written once the
 * buffer is empty - is a clean signal that the drain finished and does not race the instance
 * completing out from under it.
 */
// SingleShotTime with fixed iterations: the @Setup that rebuilds the whole buffer (up to 100k
// commands) dwarfs the timed drain, and a time-based window would repeat it many times per
// iteration. One measured op per iteration is enough.
@Warmup(iterations = 3, batchSize = 1)
@Measurement(iterations = 5, batchSize = 1)
@Fork(
    value = 1,
    jvmArgs = {"-Xmx4g", "-Xms4g", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class ResumeBufferDrainBenchmark {

  private static final Logger LOG =
      LoggerFactory.getLogger(ResumeBufferDrainBenchmark.class.getName());

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
            .parallelGateway("fork")
            .intermediateCatchEvent("wait", e -> e.timerWithDuration("PT1H"))
            .endEvent()
            .moveToNode("fork")
            .intermediateCatchEvent("keepAlive", e -> e.timerWithDuration("PT1H"))
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
  public void setupBufferedCommands() {
    LOG.info("Creating PI with {} buffered commands...", bufferedCommandCount);
    // engine.reset() in the previous invocation's teardown restores the 5s default wait, and the
    // await for up to 100k commands to reach BUFFERED below far exceeds it - raise the ceiling
    // before that await, not only before the timed resume(). It only bounds how long a wait takes
    // to fail; it doesn't bias the timed call.
    RecordingExporter.setMaximumWaitTime(Duration.ofMinutes(5).toMillis());
    processInstanceKey = processInstanceClient.ofBpmnProcessId("process").create();

    final var waitElement =
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
                        .processInstance(
                            ProcessInstanceIntent.COMPLETE_ELEMENT, waitElement.getValue())
                        .key(waitElement.getKey()))
            .toArray(RecordToWrite[]::new));

    // writeRecordsInChunks only waits for the log append, not for these commands to be processed;
    // wait for them to actually reach BUFFERED so the benchmark measures resume()'s drain alone.
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
    // the "keepAlive" branch is still waiting on its timer after resume - cancel the instance so
    // its
    // RocksDB state doesn't accumulate across the whole trial (engine.reset() clears only the
    // log/exporter).
    processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1).cancel();
    engine.reset();
  }

  @Benchmark
  public long measureBufferDrainOnResume() {
    // the "keepAlive" timer keeps the instance active through the drain, so RESUMED is written once
    // the buffer is empty - onPartition(1) skips resume()'s fallback partition lookup, which would
    // read back an already-exported record no longer available after RecordingExporter.reset().
    processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1).resume();
    return processInstanceKey;
  }

  @JMHTest("measureBufferDrainOnResume")
  void shouldMeasureBufferDrainOnResumeWith1kBufferedCommands(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("bufferedCommandCount", "1000")).run();
  }

  @JMHTest("measureBufferDrainOnResume")
  void shouldMeasureBufferDrainOnResumeWith10kBufferedCommands(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("bufferedCommandCount", "10000")).run();
  }

  @JMHTest("measureBufferDrainOnResume")
  void shouldMeasureBufferDrainOnResumeWith100kBufferedCommands(final JMHTestCase testCase) {
    testCase.withOptions(opts -> opts.param("bufferedCommandCount", "100000")).run();
  }
}
