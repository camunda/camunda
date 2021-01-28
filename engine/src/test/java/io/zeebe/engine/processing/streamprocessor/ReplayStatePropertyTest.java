/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.engine.util.random.ExecutionPath;
import io.zeebe.engine.util.random.IntermediateMessageCatchEventBlockBuilder;
import io.zeebe.engine.util.random.RandomWorkflowBuilder;
import io.zeebe.engine.util.random.steps.AbstractExecutionStep;
import io.zeebe.engine.util.random.steps.ActivateAndCompleteJob;
import io.zeebe.engine.util.random.steps.ActivateAndFailJob;
import io.zeebe.engine.util.random.steps.PickConditionCase;
import io.zeebe.engine.util.random.steps.PickDefaultCase;
import io.zeebe.engine.util.random.steps.PublishMessage;
import io.zeebe.engine.util.random.steps.StartProcess;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ReplayStatePropertyTest {

  @Rule public final EngineRule engineRule = EngineRule.singlePartition();

  @Parameter(0)
  public TestDataRecord record;

  @Test
  public void shouldDeployWorkflow() {
    final BpmnModelInstance model = record.getBpmnModel();
    engineRule.deployment().withXmlResource(model).deploy();
  }

  @Test
  public void shouldExecuteWorkflowToEnd() {
    final BpmnModelInstance model = record.getBpmnModel();
    engineRule.deployment().withXmlResource(model).deploy();

    final ExecutionPath path = record.getExecutionPath();

    path.getSteps().forEach(this::applyStep);

    RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_TERMINATED)
        .withElementType(BpmnElementType.END_EVENT)
        .await();
  }

  public void shouldRestoreStateAtEachStepInExecution() {
    final BpmnModelInstance model = record.getBpmnModel();
    engineRule.deployment().withXmlResource(model).deploy();

    final ExecutionPath path = record.getExecutionPath();

    for (AbstractExecutionStep step : path.getSteps()) {

      applyStep(step);

      // Pseudo code

      // wait until halt state

      // capture state

      // stop engine and start replay

      // compare states
    }
  }

  private void applyStep(AbstractExecutionStep step) {

    if (step instanceof StartProcess) {
      final StartProcess startProcess = (StartProcess) step;
      createWorkflowInstance(startProcess);
    } else if (step instanceof PublishMessage) {
      final PublishMessage publishMessage = (PublishMessage) step;
      publicMessage(publishMessage);
    } else if (step instanceof ActivateAndCompleteJob) {
      final ActivateAndCompleteJob activateAndCompleteJob = (ActivateAndCompleteJob) step;
      activateAndCompleteJob(activateAndCompleteJob);
    } else if (step instanceof ActivateAndFailJob) {
      final ActivateAndFailJob activateAndFailJob = (ActivateAndFailJob) step;
      activateAndFailJob(activateAndFailJob);
    } else if ((step instanceof PickDefaultCase) || (step instanceof PickConditionCase)) {
      /**
       * Nothing to do here, as the choice is made by the engine. The default case is for debugging
       * purposes only The condition case is implemented by starting the workflow with the right
       * variables
       */
    } else {
      Assertions.fail("Not yet implemented: " + step);
    }
  }

  private void activateAndCompleteJob(final ActivateAndCompleteJob activateAndCompleteJob) {
    engineRule
        .jobs()
        .withType(activateAndCompleteJob.getJobType())
        .activate()
        .getValue()
        .getJobKeys()
        .forEach(jobKey -> engineRule.job().withKey(jobKey).complete());
  }

  private void activateAndFailJob(final ActivateAndFailJob activateAndFailJob) {
    engineRule
        .jobs()
        .withType(activateAndFailJob.getJobType())
        .activate()
        .getValue()
        .getJobKeys()
        .forEach(jobKey -> engineRule.job().withKey(jobKey).fail());
  }

  private void publicMessage(final PublishMessage publishMessage) {
    engineRule
        .message()
        .withName(publishMessage.getMessageName())
        .withCorrelationKey(IntermediateMessageCatchEventBlockBuilder.CORRELATION_KEY_VALUE)
        .publish();
  }

  private void createWorkflowInstance(final StartProcess startProcess) {
    engineRule
        .workflowInstance()
        .ofBpmnProcessId(startProcess.getProcessId())
        .withVariables(startProcess.getVariables())
        .create();
  }

  @Parameters(name = "{0}")
  public static Collection<TestDataRecord> getTestRecord() {
    final List<TestDataRecord> records = new ArrayList<>();

    final Random random = new Random();

    for (int i = 0; i < 10; i++) {
      final long workflowSeed = random.nextLong();

      final String id = "process" + i;

      final RandomWorkflowBuilder builder =
          new RandomWorkflowBuilder(
              workflowSeed, Optional.empty(), Optional.empty(), Optional.empty());

      final BpmnModelInstance bpmnModelInstance = builder.buildWorkflow();

      for (int p = 0; p < 1; p++) {
        long pathSeed = random.nextLong();

        ExecutionPath path = builder.findRandomExecutionPath(pathSeed);

        records.add(new TestDataRecord(workflowSeed, pathSeed, bpmnModelInstance, path));
      }
    }

    return records;
  }

  private static final class TestDataRecord {
    private final long workFlowSeed;
    private final long executionPathSeed;

    private final BpmnModelInstance bpmnModel;
    private final ExecutionPath executionPath;

    private TestDataRecord(
        final long workFlowSeed,
        final long executionPathSeed,
        final BpmnModelInstance bpmnModel,
        final ExecutionPath executionPath) {
      this.workFlowSeed = workFlowSeed;
      this.executionPathSeed = executionPathSeed;
      this.bpmnModel = bpmnModel;
      this.executionPath = executionPath;
    }

    public long getWorkFlowSeed() {
      return workFlowSeed;
    }

    public long getExecutionPathSeed() {
      return executionPathSeed;
    }

    public BpmnModelInstance getBpmnModel() {
      return bpmnModel;
    }

    public ExecutionPath getExecutionPath() {
      return executionPath;
    }

    @Override
    public String toString() {
      return "TestDataRecord{"
          + "workFlowSeed="
          + workFlowSeed
          + ", executionPathSeed="
          + executionPathSeed
          + '}';
    }
  }
}
