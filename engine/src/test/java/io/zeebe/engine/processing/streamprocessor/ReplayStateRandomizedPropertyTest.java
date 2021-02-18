/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.processing.streamprocessor.StreamProcessor.Phase;
import io.zeebe.engine.state.ZbColumnFamilies;
import io.zeebe.engine.util.EngineRule;
import io.zeebe.engine.util.WorkflowExecutor;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.test.util.bpmn.random.AbstractExecutionStep;
import io.zeebe.test.util.bpmn.random.ExecutionPath;
import io.zeebe.test.util.bpmn.random.TestDataGenerator;
import io.zeebe.test.util.bpmn.random.TestDataGenerator.TestDataRecord;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.Collection;
import java.util.stream.Collectors;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public class ReplayStateRandomizedPropertyTest {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ReplayStateRandomizedPropertyTest.class);

  private static final int WORKFLOW_COUNT = 5;
  private static final int EXECUTION_PATH_COUNT = 5;

  @Rule
  public final EngineRule engineRule =
      EngineRule.singlePartition()
          .withOnProcessedCallback(record -> lastProcessedPosition = record.getPosition())
          .withOnSkippedCallback(record -> lastProcessedPosition = record.getPosition());

  @Rule public TestWatcher failedTestDataPrinter = new FailedTestDataPrinter();

  @Parameter public TestDataRecord record;

  private final WorkflowExecutor workflowExecutor = new WorkflowExecutor(engineRule);

  private long lastProcessedPosition = -1L;

  @Before
  public void init() {
    lastProcessedPosition = -1L;
  }

  /**
   * This test takes a random workflow and execution path in that workflow. A process instance is
   * started and the workflow is executed step by step according to the random execution path. After
   * each step, the current database state is captured and the engine is restarted. After restart
   * the database state is captured and compared to the database state before the restart. After all
   * steps are executed, a final comparison is performed.
   *
   * <p>The test passes if at any step in time the database states before and after the restart of
   * the engine are identical.
   */
  @Test
  public void shouldRestoreStateAtEachStepInExecution() {
    final BpmnModelInstance model = record.getBpmnModel();
    engineRule.deployment().withXmlResource(model).deploy();

    final ExecutionPath path = record.getExecutionPath();

    for (final AbstractExecutionStep step : path.getSteps()) {

      workflowExecutor.applyStep(step);

      stopAndRestartEngineAndCompareStates();
    }

    // wait for termination of the process
    final var result =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withElementType(BpmnElementType.PROCESS)
            .withBpmnProcessId(path.getProcessId())
            .getFirst();

    final var position = result.getPosition();

    Awaitility.await("await the last workflow record to be processed")
        .untilAsserted(() -> assertThat(lastProcessedPosition).isEqualTo(position));

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
                    .describedAs("The state column '%s' should be empty after replay", column)
                    .isEmpty();
              } else {
                softly
                    .assertThat(replayEntries)
                    .describedAs("The state column '%s' has different entries after replay", column)
                    .containsExactlyInAnyOrderEntriesOf(processingEntries);
              }
            });

    softly.assertAll();
  }

  private void waitForProcessingToStop() {
    Awaitility.await("await the last written record to be processed")
        .untilAsserted(
            () ->
                assertThat(lastProcessedPosition).isEqualTo(engineRule.getLastWrittenPosition(1)));
  }

  @Parameters(name = "{0}")
  public static Collection<TestDataRecord> getTestRecords() {
    // use the following code to rerun a specific test case:
    //    final var workflowSeed = 8354930473519021610L;
    //    final var executionPathSeed = 4913527714508155594L;
    //    return List.of(TestDataGenerator.regenerateTestRecord(workflowSeed, executionPathSeed));
    return TestDataGenerator.generateTestRecords(WORKFLOW_COUNT, EXECUTION_PATH_COUNT);
  }

  private final class FailedTestDataPrinter extends TestWatcher {

    @Override
    protected void failed(final Throwable e, final Description description) {
      LOGGER.info("Data of failed test case: {}", record);
      LOGGER.info(
          "Workflow of failed test case:{}{}",
          System.lineSeparator(),
          Bpmn.convertToString(record.getBpmnModel()));
      LOGGER.info(
          "Execution path of failed test case:{}{}",
          System.lineSeparator(),
          record.getExecutionPath().getSteps().stream()
              .map(AbstractExecutionStep::toString)
              .collect(Collectors.joining(System.lineSeparator())));
    }
  }
}
