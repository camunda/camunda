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
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public final class IdLengthTest {

  @ParameterizedTest
  @MethodSource("idLengthTestCases")
  void idLengthExceeded(
      final Class<? extends BpmnModelElementInstance> expectedElementClass,
      final ProcessBuilder processBuilder) {

    final var id = "a".repeat(ProcessValidationUtil.MAX_ID_FIELD_LENGTH + 1);

    // when
    final var process = processBuilder.build(id);

    // then
    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            expectedElementClass,
            "IDs must not be longer than the configured max-id-length of %s characters"
                .formatted(ProcessValidationUtil.MAX_ID_FIELD_LENGTH)));
  }

  @ParameterizedTest
  @MethodSource("idLengthTestCases")
  void idLengthNotExceeded(
      final Class<? extends BpmnModelElementInstance> expectedElementClass,
      final ProcessBuilder processBuilder) {

    final var id = "a".repeat(ProcessValidationUtil.MAX_ID_FIELD_LENGTH);

    // when
    final var process = processBuilder.build(id);

    // then
    ProcessValidationUtil.validateProcess(process);
  }

  private static Stream<Arguments> idLengthTestCases() {
    return Stream.of(
        Arguments.of(
            Process.class,
            (ProcessBuilder)
                id ->
                    Bpmn.createExecutableProcess(id)
                        .startEvent()
                        .serviceTask("task", t -> t.zeebeJobType("test"))
                        .endEvent()
                        .done()),
        Arguments.of(
            ServiceTask.class,
            (ProcessBuilder)
                id ->
                    Bpmn.createExecutableProcess("process")
                        .startEvent()
                        .serviceTask(id, t -> t.zeebeJobType("test"))
                        .endEvent()
                        .done()),
        Arguments.of(
            StartEvent.class,
            (ProcessBuilder)
                id ->
                    Bpmn.createExecutableProcess("process")
                        .startEvent(id)
                        .serviceTask("task", t -> t.zeebeJobType("test"))
                        .endEvent()
                        .done()));
  }

  @FunctionalInterface
  private interface ProcessBuilder {
    BpmnModelInstance build(String id);
  }
}
