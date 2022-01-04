/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor.Phase;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
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
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ReplayStateRandomizedPropertyTest {

  private static final String PROCESS_COUNT = System.getProperty("processCount", "3");
  private static final String EXECUTION_PATH_COUNT =
      System.getProperty("replayExecutionCount", "1");
  @Parameter public TestDataRecord record;

  @Rule
  public TestWatcher failedTestDataPrinter =
      new FailedPropertyBasedTestDataPrinter(this::getDataRecord);

  @Rule public final EngineRule engineRule = EngineRule.singlePartition();
  private long lastProcessedPosition = -1L;
  private final ProcessExecutor processExecutor = new ProcessExecutor(engineRule);

  @Before
  public void init() {
    lastProcessedPosition = -1L;
  }

  public TestDataRecord getDataRecord() {
    return record;
  }

  /**
   * This test takes a random process and execution path in that process. A process instance is
   * started and the process is executed step by step according to the random execution path. After
   * each step, the current database state is captured and the engine is restarted. After restart
   * the database state is captured and compared to the database state before the restart. After all
   * steps are executed, a final comparison is performed.
   *
   * <p>The test passes if at any step in time the database states before and after the restart of
   * the engine are identical.
   */
  @Test
  public void shouldRestoreStateAtEachStepInExecution() {
    final var deployment = engineRule.deployment();
    record.getBpmnModels().forEach(deployment::withXmlResource);
    deployment.deploy();

    final ExecutionPath path = record.getExecutionPath();

    for (final ScheduledExecutionStep scheduledStep : path.getSteps()) {
      record.setCurrentStep(scheduledStep);
      processExecutor.applyStep(scheduledStep.getStep());

      stopAndRestartEngineAndCompareStates();
    }

    // wait for termination of the process
    final var result =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withElementType(BpmnElementType.PROCESS)
            .withBpmnProcessId(path.getProcessId())
            .getFirst();

    final var position = result.getPosition();

    Awaitility.await("await the last process record to be processed")
        .untilAsserted(
            () ->
                assertThat(engineRule.getLastProcessedPosition()).isGreaterThanOrEqualTo(position));

    stopAndRestartEngineAndCompareStates();
  }

  private void stopAndRestartEngineAndCompareStates() {
    // given
    waitForProcessingToStop();

    engineRule.pauseProcessing(1);

    final var processingState = engineRule.collectState();
    engineRule.stop();

    // when
    engineRule.start();

    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(engineRule.getStreamProcessor(1).getCurrentPhase().join())
                    .isEqualTo(Phase.PROCESSING));

    // then
    Awaitility.await("await that the replay state is equal to the processing state")
        .untilAsserted(
            () -> {
              final var replayState = engineRule.collectState();

              final var softly = new SoftAssertions();

              processingState.entrySet().stream()
                  .filter(entry -> entry.getKey() != ZbColumnFamilies.DEFAULT)
                  .forEach(
                      entry -> {
                        final var column = entry.getKey();
                        final var processingEntries = entry.getValue();
                        final var replayEntries = replayState.get(column);

                        if (processingEntries.isEmpty()) {
                          softly
                              .assertThat(replayEntries)
                              .describedAs(
                                  "The state column '%s' should be empty after replay", column)
                              .isEmpty();
                        } else {
                          softly
                              .assertThat(replayEntries)
                              .describedAs(
                                  "The state column '%s' has different entries after replay",
                                  column)
                              .containsExactlyInAnyOrderEntriesOf(processingEntries);
                        }
                      });

              softly.assertAll();
            });
  }

  private void waitForProcessingToStop() {
    Awaitility.await("await the last written record to be processed")
        .untilAsserted(
            () ->
                assertThat(engineRule.hasReachedEnd())
                    .describedAs("Processing has reached end of the log.")
                    .isTrue());
  }

  @Parameters(name = "{0}")
  public static Collection<TestDataRecord> getTestRecords() {
    // use the following code to rerun a specific test case:
    //    final var processSeed = 3499044774323385558L;
    //    final var executionPathSeed = 3627169465144620203L;
    //    return List.of(TestDataGenerator.regenerateTestRecord(processSeed, executionPathSeed));
    return TestDataGenerator.generateTestRecords(
        Integer.parseInt(PROCESS_COUNT), Integer.parseInt(EXECUTION_PATH_COUNT));
  }
}
