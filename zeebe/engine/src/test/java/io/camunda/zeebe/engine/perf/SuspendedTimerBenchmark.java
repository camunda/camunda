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
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.test.util.jmh.JMHTestCase;
import io.camunda.zeebe.test.util.junit.JMHTest;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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
 * Benchmarks resume burst latency when expired timers fire on a previously suspended PI. Setup
 * creates N parallel timer catch events, suspends the PI, and advances the clock past their due
 * dates. The benchmark measures time from RESUME through all N timers firing (TRIGGERED), which
 * includes the resume chain and the asynchronous timer burst that the COMPLETE_RESUMING processor
 * schedules via the due-date checker.
 */
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-Xmx4g", "-Xms4g", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class SuspendedTimerBenchmark {

  private static final Logger LOG =
      LoggerFactory.getLogger(SuspendedTimerBenchmark.class.getName());

  @Param({"100", "1000", "2000"})
  private int timerCount;

  private TestContext testContext;
  private TestEngine engine;
  private ControlledActorClock clock;
  private ProcessInstanceClient processInstanceClient;
  private long processInstanceKey;

  @Setup(Level.Invocation)
  public void setup() throws Throwable {
    clock = new ControlledActorClock();
    testContext = TestEngine.createTestContext(clock);
    engine = TestEngine.createSinglePartitionEngine(testContext, clock);

    final String collectionExpression =
        "=["
            + IntStream.rangeClosed(1, timerCount)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(","))
            + "]";

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .subProcess(
                "sub",
                s ->
                    s.embeddedSubProcess()
                        .startEvent()
                        .intermediateCatchEvent("timer", e -> e.timerWithDuration("PT1H"))
                        .serviceTask("task", t -> t.zeebeJobType("task").done())
                        .endEvent()
                        .subProcessDone()
                        .multiInstance()
                        .parallel()
                        .zeebeInputCollectionExpression(collectionExpression)
                        .multiInstanceDone())
            .endEvent()
            .done();

    engine.createDeploymentClient().withXmlResource(process).deploy();
    processInstanceClient = engine.createProcessInstanceClient();

    LOG.info("Creating PI with {} timer catch events...", timerCount);
    processInstanceKey = processInstanceClient.ofBpmnProcessId("process").create();

    RecordingExporter.timerRecords()
        .withIntent(TimerIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(timerCount)
        .count();

    processInstanceClient.withInstanceKey(processInstanceKey).suspend();

    clock.addTime(Duration.ofHours(2));

    // Drain rejected TRIGGER commands written by the due-date checker while PI is suspended
    RecordingExporter.timerRecords()
        .withIntent(TimerIntent.TRIGGER)
        .withProcessInstanceKey(processInstanceKey)
        .onlyCommandRejections()
        .limit(timerCount)
        .count();

    LOG.info("PI {} suspended with {} expired timers.", processInstanceKey, timerCount);
    RecordingExporter.reset();
    RecordingExporter.setMaximumWaitTime(Duration.ofSeconds(120).toMillis());
  }

  @TearDown(Level.Invocation)
  public void tearDown() {
    testContext.autoCloseableRule().after();
    testContext.temporaryFolder().delete();
  }

  @Benchmark
  public long measureResumeBurstWithExpiredTimers() {
    processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1).resume();

    // Timers fire asynchronously after RESUMED via a scheduled due-date check (~100ms);
    // wait for all of them so the measurement includes the full burst
    RecordingExporter.timerRecords()
        .withIntent(TimerIntent.TRIGGERED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(timerCount)
        .count();

    return processInstanceKey;
  }

  @JMHTest("measureResumeBurstWithExpiredTimers")
  void shouldMeasureTimerResumeBurst(final JMHTestCase testCase) {
    testCase.run();
  }
}
