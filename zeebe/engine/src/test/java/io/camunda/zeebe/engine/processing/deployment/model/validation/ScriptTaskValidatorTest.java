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
import io.camunda.zeebe.model.bpmn.builder.ScriptTaskBuilder;
import io.camunda.zeebe.model.bpmn.instance.ScriptTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeScript;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

public class ScriptTaskValidatorTest {

  @Test
  void emptyExpression() {
    // when
    final var process = process(task -> task.zeebeExpression("").zeebeResultVariable("result"));

    // then
    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            ZeebeScript.class, "Attribute 'expression' must be present and not empty"));
  }

  @Test
  void emptyResultVariable() {
    // when
    final var process = process(task -> task.zeebeExpression("expression").zeebeResultVariable(""));

    // then
    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            ZeebeScript.class, "Attribute 'resultVariable' must be present and not empty"));
  }

  @Test
  void noExpressionAndTaskDefinition() {
    // when
    final var process = process(task -> {});

    // then
    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            ScriptTask.class,
            "Must have either one 'zeebe:script' or one 'zeebe:taskDefinition' extension element"));
  }

  @Test
  void bothExpressionAndTaskDefinitionExtension() {
    // when
    final BpmnModelInstance process =
        process(
            task ->
                task.zeebeExpression("true").zeebeResultVariable("result").zeebeJobType("jobType"));

    // then
    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            ScriptTask.class,
            "Must have either one 'zeebe:script' or one 'zeebe:taskDefinition' extension element"));
  }

  private BpmnModelInstance process(final Consumer<ScriptTaskBuilder> taskBuilder) {
    return Bpmn.createExecutableProcess("process")
        .startEvent()
        .scriptTask("task", taskBuilder)
        .done();
  }
}
