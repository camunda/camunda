/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.perf;

import io.camunda.zeebe.engine.perf.TestEngine.TestContext;
import io.camunda.zeebe.engine.util.client.ProcessInstanceClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.BusinessRuleTaskBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.clock.DefaultActorClock;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.rules.TemporaryFolder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Warmup(iterations = 25, time = 1, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10_000, time = 1, timeUnit = TimeUnit.MILLISECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-Xmx4g", "-Xms4g"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class EngineParallelDmnTaskPerformanceTest {
  public static final Logger LOG =
      LoggerFactory.getLogger(EngineParallelDmnTaskPerformanceTest.class.getName());
  public static final String DECISION_ID = "jedi_or_sith";
  public static final String END_EVENT = "end";
  private static final String DMN_RESOURCE = "/dmn/drg-force-user.dmn";
  private static final String PROCESS_ID = "process";
  private static final String TASK_ID = "task";
  private static final String RESULT_VARIABLE = "result";
  private long count;
  private ProcessInstanceClient processInstanceClient;
  private TestContext testContext;
  private TestEngine singlePartitionEngine;

  @Setup
  public void setup() throws Throwable {
    testContext = createTestContext();

    singlePartitionEngine = TestEngine.createSinglePartitionEngine(testContext);

    setupState(singlePartitionEngine);
  }

  /** Will build up a state for the large state performance test */
  private void setupState(final TestEngine singlePartitionEngine) {
    AbstractFlowNodeBuilder<?, ?> processBuilder =
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent().parallelGateway();

    final int numberOfDmnTasks = 10;
    for (int i = 0; i < numberOfDmnTasks; i++) {
      processBuilder = processBuilder.businessRuleTask(TASK_ID + i, createBusinessRuleTask());

      if (i < numberOfDmnTasks - 1) {
        processBuilder = processBuilder.moveToLastGateway();
      }
    }

    final BpmnModelInstance model = processBuilder.endEvent(END_EVENT).done();

    singlePartitionEngine
        .createDeploymentClient()
        .withXmlClasspathResource(DMN_RESOURCE)
        .withXmlResource(model)
        .deploy();

    processInstanceClient = singlePartitionEngine.createProcessInstanceClient();
  }

  private static Consumer<BusinessRuleTaskBuilder> createBusinessRuleTask() {
    return t -> t.zeebeCalledDecisionId(DECISION_ID).zeebeResultVariable(RESULT_VARIABLE);
  }

  private TestContext createTestContext() throws IOException {
    final var autoCloseableRule = new AutoCloseableRule();
    final var temporaryFolder = new TemporaryFolder();
    temporaryFolder.create();

    // scheduler
    final var builder =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(1)
            .setIoBoundActorThreadCount(1)
            .setActorClock(new DefaultActorClock());

    final var actorScheduler = builder.build();
    autoCloseableRule.manage(actorScheduler);
    actorScheduler.start();
    return new TestContext(actorScheduler, temporaryFolder, autoCloseableRule);
  }

  @TearDown
  public void tearDown() {
    LOG.info("Started {} process instances", count);
    testContext.autoCloseableRule().after();
  }

  @Benchmark
  public Record<?> measureProcessExecutionTime() {
    final long piKey =
        processInstanceClient
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("lightsaberColor", "blue")
            .create();

    final Record<ProcessInstanceRecordValue> record =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(piKey)
            .withElementType(BpmnElementType.END_EVENT)
            .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();

    count++;
    singlePartitionEngine.reset();
    return record;
  }
}
