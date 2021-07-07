/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import static io.camunda.zeebe.engine.processing.deployment.model.validation.ExpectedValidationResult.expect;

import io.camunda.zeebe.engine.util.JobWorkerTaskBuilder;
import io.camunda.zeebe.engine.util.JobWorkerTaskBuilderProvider;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractJobWorkerTaskBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

public class JobWorkerTaskValidationTest {

  @ParameterizedTest
  @ArgumentsSource(JobWorkerTaskBuilderProvider.class)
  @DisplayName("task with invalid job type expression")
  void invalidJobTypeExpression(final JobWorkerTaskBuilder taskBuilder) {

    final var process =
        processWithTask(taskBuilder, task -> task.zeebeJobTypeExpression("invalid!"));

    ProcessValidationUtil.validateProcess(
        process, expect(ZeebeTaskDefinition.class, "failed to parse expression 'invalid!'"));
  }

  @ParameterizedTest
  @ArgumentsSource(JobWorkerTaskBuilderProvider.class)
  @DisplayName("task with invalid job retries expression")
  void invalidJobRetriesExpression(final JobWorkerTaskBuilder taskBuilder) {

    final var process =
        processWithTask(
            taskBuilder, task -> task.zeebeJobType("task").zeebeJobRetriesExpression("invalid!"));

    ProcessValidationUtil.validateProcess(
        process, expect(ZeebeTaskDefinition.class, "failed to parse expression 'invalid!'"));
  }

  private BpmnModelInstance processWithTask(
      final JobWorkerTaskBuilder taskBuilder,
      final Consumer<AbstractJobWorkerTaskBuilder<?, ?>> taskModifier) {

    final var processBuilder = Bpmn.createExecutableProcess("process").startEvent();
    final var jobWorkerTaskBuilder = taskBuilder.build(processBuilder);
    taskModifier.accept(jobWorkerTaskBuilder);
    return jobWorkerTaskBuilder.endEvent().done();
  }
}
