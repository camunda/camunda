/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstance;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public final class WorkerTypeLengthTest {

  @ParameterizedTest
  @MethodSource("workerTypeLengthTestCases")
  void workerTypeLengthExceeded(
      final Class<? extends BpmnModelElementInstance> expectedElementClass,
      final ProcessBuilder processBuilder) {

    final var id = "a".repeat(ProcessValidationUtil.MAX_WORKER_TYPE_LENGTH + 1);

    // when
    final var process = processBuilder.build(id);

    // then
    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            expectedElementClass,
            "Worker types must not be longer than the configured max-worker-type-length of %s characters"
                .formatted(ProcessValidationUtil.MAX_WORKER_TYPE_LENGTH)));
  }

  @ParameterizedTest
  @MethodSource("workerTypeLengthTestCases")
  void workerTypeLengthNotExceeded(
      final Class<? extends BpmnModelElementInstance> expectedElementClass,
      final ProcessBuilder processBuilder) {

    final var id = "a".repeat(ProcessValidationUtil.MAX_WORKER_TYPE_LENGTH);

    // when
    final var process = processBuilder.build(id);

    // then
    ProcessValidationUtil.validateProcess(process);
  }

  private static Stream<Arguments> workerTypeLengthTestCases() {
    return Stream.of(
        Arguments.of(
            ZeebeTaskDefinition.class,
            (ProcessBuilder)
                id ->
                    Bpmn.createExecutableProcess("process")
                        .startEvent()
                        .serviceTask("task", t -> t.zeebeJobType(id))
                        .endEvent()
                        .done()),
        Arguments.of(
            ZeebeTaskDefinition.class,
            (ProcessBuilder)
                id ->
                    Bpmn.createExecutableProcess("process")
                        .startEvent()
                        .scriptTask("task", t -> t.zeebeJobType(id))
                        .endEvent()
                        .done()),
        Arguments.of(
            ZeebeTaskDefinition.class,
            (ProcessBuilder)
                id ->
                    Bpmn.createExecutableProcess("process")
                        .startEvent("startEvent")
                        .sendTask("task", t -> t.zeebeJobType(id))
                        .endEvent()
                        .done()));
  }

  @FunctionalInterface
  private interface ProcessBuilder {
    BpmnModelInstance build(String id);
  }
}
