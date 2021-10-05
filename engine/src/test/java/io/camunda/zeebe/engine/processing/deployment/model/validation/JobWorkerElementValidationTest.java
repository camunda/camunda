/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import static io.camunda.zeebe.engine.processing.deployment.model.validation.ExpectedValidationResult.expect;

import io.camunda.zeebe.engine.util.JobWorkerElementBuilder;
import io.camunda.zeebe.engine.util.JobWorkerElementBuilderProvider;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ZeebeJobWorkerElementBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

public class JobWorkerElementValidationTest {

  @ParameterizedTest
  @ArgumentsSource(JobWorkerElementBuilderProvider.class)
  @DisplayName("element with invalid job type expression")
  void invalidJobTypeExpression(final JobWorkerElementBuilder elementBuilder) {

    final var process =
        processWithElement(elementBuilder, element -> element.zeebeJobTypeExpression("invalid!"));

    ProcessValidationUtil.validateProcess(
        process, expect(ZeebeTaskDefinition.class, "failed to parse expression 'invalid!'"));
  }

  @ParameterizedTest
  @ArgumentsSource(JobWorkerElementBuilderProvider.class)
  @DisplayName("element with invalid job retries expression")
  void invalidJobRetriesExpression(final JobWorkerElementBuilder elementBuilder) {

    final var process =
        processWithElement(
            elementBuilder,
            element -> element.zeebeJobType("task").zeebeJobRetriesExpression("invalid!"));

    ProcessValidationUtil.validateProcess(
        process, expect(ZeebeTaskDefinition.class, "failed to parse expression 'invalid!'"));
  }

  private BpmnModelInstance processWithElement(
      final JobWorkerElementBuilder elementBuilder,
      final Consumer<ZeebeJobWorkerElementBuilder<?>> taskModifier) {

    final var processBuilder = Bpmn.createExecutableProcess("process").startEvent();
    final var jobWorkerElementBuilder = elementBuilder.build(processBuilder, taskModifier);
    return jobWorkerElementBuilder.done();
  }
}
