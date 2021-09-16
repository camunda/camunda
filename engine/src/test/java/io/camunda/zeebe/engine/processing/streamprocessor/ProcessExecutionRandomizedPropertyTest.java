/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.ProcessExecutor;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPath;
import io.camunda.zeebe.test.util.bpmn.random.ScheduledExecutionStep;
import io.camunda.zeebe.test.util.bpmn.random.TestDataGenerator;
import io.camunda.zeebe.test.util.bpmn.random.TestDataGenerator.TestDataRecord;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProcessExecutionRandomizedPropertyTest {

  /*
   * Some notes on scaling of these tests:
   * With 10 processes and 100 paths there is a theoretical maximum of 1000 records.
   * However, in tests the number of actual records was around 300, which can execute in about 1 m.
   *
   * Having a high number of random execution paths has only a small effect as there are rarely 100
   * different execution paths for any given process. Having a high number of paths gives us a good
   * chance to exhaust all possible paths within a given process.
   *
   * This is only true if the complexity of the processes stays constant.
   * Increasing the maximum number of blocks, depth or branches could increase the number of
   * possible paths exponentially
   */
  private static final String PROCESS_COUNT = System.getProperty("processCount", "3");
  private static final String EXECUTION_PATH_COUNT = System.getProperty("executionCount", "30");
  @Rule public final EngineRule engineRule = EngineRule.singlePartition();
  @Parameter public TestDataRecord record;

  @Rule
  public TestWatcher failedTestDataPrinter =
      new FailedPropertyBasedTestDataPrinter(this::getDataRecord);

  private final ProcessExecutor processExecutor = new ProcessExecutor(engineRule);

  public TestDataRecord getDataRecord() {
    return record;
  }

  /**
   * This test takes a random process and execution path in that process. A process instance is
   * started and the process is executed according to the random execution path. The test passes if
   * it reaches the end of the process.
   */
  @Test
  public void shouldExecuteProcessToEnd() {
    final var deployment = engineRule.deployment();
    record.getBpmnModels().forEach(deployment::withXmlResource);
    deployment.deploy();

    final ExecutionPath path = record.getExecutionPath();

    path.getSteps().stream()
        .peek(scheduledExecutionStep -> record.setCurrentStep(scheduledExecutionStep))
        .map(ScheduledExecutionStep::getStep)
        .forEach(processExecutor::applyStep);

    // wait for the completion of the process
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withElementType(BpmnElementType.PROCESS)
        .withBpmnProcessId(path.getProcessId())
        .await();
  }

  @Parameters(name = "{0}")
  public static Collection<TestDataGenerator.TestDataRecord> getTestRecords() {
    return TestDataGenerator.generateTestRecords(
        Integer.parseInt(PROCESS_COUNT), Integer.parseInt(EXECUTION_PATH_COUNT));
  }
}
