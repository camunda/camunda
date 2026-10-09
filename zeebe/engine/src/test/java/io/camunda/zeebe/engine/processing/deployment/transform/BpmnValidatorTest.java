/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.InstantSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class BpmnValidatorTest {

  private static final String DUPLICATE_MESSAGE_ERROR =
      "Multiple message event definitions with the same name 'message' are not allowed.";

  private BpmnValidator validator;

  @BeforeEach
  void setUp() {
    final var expressionLanguage =
        ExpressionLanguageFactory.createExpressionLanguage(
            new ZeebeFeelEngineClock(InstantSource.system()));
    validator =
        new BpmnValidator(
            expressionLanguage,
            new ExpressionProcessor(
                expressionLanguage,
                scopeKey -> name -> null,
                EngineConfiguration.DEFAULT_EXPRESSION_EVALUATION_TIMEOUT),
            EngineConfiguration.DEFAULT_VALIDATORS_RESULTS_OUTPUT_MAX_SIZE);
  }

  @Test
  void shouldReportErrorsOfEachValidationSeparately() {
    // given
    final var invalidModel = withDuplicateMessageBoundaryEvents("firstTask");
    final var otherInvalidModel = withDuplicateMessageBoundaryEvents("secondTask");

    // when
    final var firstResult = validator.validate(invalidModel);
    final var secondResult = validator.validate(otherInvalidModel);

    // then
    assertThat(firstResult).contains("firstTask").contains(DUPLICATE_MESSAGE_ERROR);
    assertThat(secondResult)
        .contains("secondTask")
        .contains(DUPLICATE_MESSAGE_ERROR)
        .doesNotContain("firstTask");
  }

  @Test
  void shouldNotReportErrorsOfPreviousValidationForValidModel() {
    // given
    validator.validate(withDuplicateMessageBoundaryEvents("task"));
    final var validModel =
        Bpmn.createExecutableProcess("valid")
            .startEvent()
            .serviceTask("task", b -> b.zeebeJobType("type"))
            .endEvent()
            .done();

    // when
    final var result = validator.validate(validModel);

    // then
    assertThat(result).isNull();
  }

  private static BpmnModelInstance withDuplicateMessageBoundaryEvents(final String taskId) {
    return Bpmn.createExecutableProcess("process")
        .startEvent()
        .serviceTask(taskId, b -> b.zeebeJobType("type"))
        .boundaryEvent("msg1")
        .message(m -> m.name("message").zeebeCorrelationKeyExpression("id"))
        .endEvent()
        .moveToActivity(taskId)
        .boundaryEvent("msg2")
        .message(m -> m.name("message").zeebeCorrelationKeyExpression("orderId"))
        .endEvent()
        .done();
  }
}
