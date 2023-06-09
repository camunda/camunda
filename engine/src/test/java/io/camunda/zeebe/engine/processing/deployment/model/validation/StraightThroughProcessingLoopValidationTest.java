/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.ExecuteCommandResponseDecoder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class StraightThroughProcessingLoopValidationTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String GENERIC_REJECTION_MESSAGE =
      "ERROR: Processes are not allowed to contain a straight-through processing loop: ";

  @Rule
  public final RecordingExporterTestWatcher recordingExporter = new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectDeploymentWithSimpleUndefinedTaskLoop() {
    // given
    final var processId = Strings.newRandomValidBpmnId();

    // when
    final var rejectedDeployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .task("task1")
                    .task("task2")
                    .connectTo("task1")
                    .done())
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .contains(String.format("Process: %s", processId))
        .contains(GENERIC_REJECTION_MESSAGE + "task1 > task2 > task1");
  }

  @Test
  public void shouldRejectDeploymentWithSimpleManualTaskLoop() {
    // given
    final var processId = Strings.newRandomValidBpmnId();

    // when
    final var rejectedDeployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .manualTask("task1")
                    .manualTask("task2")
                    .connectTo("task1")
                    .done())
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .contains(String.format("Process: %s", processId))
        .contains(GENERIC_REJECTION_MESSAGE + "task1 > task2 > task1");
  }

  @Test
  public void shouldRejectDeploymentWithComplexStraightThroughProcessingLoop() {
    // given
    final var processId = Strings.newRandomValidBpmnId();

    // when
    // Straight through loop: task1 > parallel2 > manualTask1 > exclusive2 > task1
    final var rejectedDeployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("userTask1")
                    .parallelGateway("parallel1")
                    .exclusiveGateway("exclusive1")
                    .task("task1")
                    .parallelGateway("parallel2")
                    .manualTask("manualTask1")
                    .exclusiveGateway("exclusive2")
                    .defaultFlow()
                    .userTask("userTask2")
                    .connectTo("exclusive1")
                    .moveToNode("parallel2")
                    .userTask("userTask3")
                    .endEvent()
                    .moveToNode("exclusive2")
                    .sequenceFlowId("sequenceToLoop")
                    .conditionExpression("true")
                    .connectTo("task1")
                    .moveToNode("parallel1")
                    .task("task2")
                    .task("task3")
                    .endEvent()
                    .done())
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .contains(String.format("Process: %s", processId))
        .contains(
            GENERIC_REJECTION_MESSAGE
                + "manualTask1 > exclusive2 > task1 > parallel2 > manualTask1");
  }

  @Test
  public void shouldRejectDeploymentWithStraightThroughProcessingLoopNotStartingAtFirstElement() {
    // given
    final var processId = Strings.newRandomValidBpmnId();

    // when
    final var rejectedDeployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .task("task1")
                    .task("task2")
                    .task("task3")
                    .task("task4")
                    .task("task5")
                    .task("task6")
                    .task("task7")
                    .task("task8")
                    .task("task9")
                    .connectTo("task7")
                    .done())
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .contains(String.format("Process: %s", processId))
        .contains(GENERIC_REJECTION_MESSAGE + "task7 > task8 > task9 > task7");
  }

  @Test
  public void shouldRejectDeploymentWithCollaborationContainingStraightThroughProcessingLoop() {
    // given
    final String resource = "/processes/collaboration_straight_through_processing_loop.bpmn";

    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlClasspathResource(resource).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .contains("Process: process1")
        .contains(GENERIC_REJECTION_MESSAGE + "task1 > task2 > task1")
        .contains("Process: process2")
        .contains(GENERIC_REJECTION_MESSAGE + "manualTask2 > manualTask1 > manualTask2");
  }

  @Test
  public void shouldDeployProcessWithRegularLoops() {
    // given
    final var processId = Strings.newRandomValidBpmnId();

    // when
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("task1")
                    .userTask("task2")
                    .connectTo("task1")
                    .done())
            .deploy();

    // then
    assertThat(deployment.getKey())
        .describedAs("Allow deployments of loops that aren't straight-through processed")
        .isNotNegative();
  }

  @Test
  public void shouldDeployProcessWithRegularTaskBetweenStraightThroughTasks() {
    // given
    final var processId = Strings.newRandomValidBpmnId();

    // when
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .task("task1")
                    .userTask("test")
                    .task("task2")
                    .connectTo("task1")
                    .done())
            .deploy();

    // then
    assertThat(deployment.getKey())
        .describedAs("Allow deployments of loops that aren't straight-through processed")
        .isNotNegative();
  }
}
