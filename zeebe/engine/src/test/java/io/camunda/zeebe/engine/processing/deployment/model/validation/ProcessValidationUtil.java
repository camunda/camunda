/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.deployment.transform.BpmnValidatorConfig;
import io.camunda.zeebe.engine.processing.expression.ScopedEvaluationContext;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.traversal.ModelWalker;
import io.camunda.zeebe.model.bpmn.validation.ValidationVisitor;
import io.camunda.zeebe.model.bpmn.validation.zeebe.ZeebeDesignTimeValidators;
import java.time.InstantSource;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.camunda.bpm.model.xml.validation.ValidationResults;

public class ProcessValidationUtil {

  public static final int MAX_ID_FIELD_LENGTH = 1024;
  public static final int MAX_NAME_FIELD_LENGTH = 1024;

  /**
   * Validate the provided {@link BpmnModelInstance}, asserting that is NOT a valid process
   *
   * @param process the element to validate
   * @param expectation the expected validation errors
   */
  public static void validateProcess(
      final BpmnModelInstance process, final ExpectedValidationResult expectation) {

    Bpmn.validateModel(process);

    final var validationResults =
        validate(process).getResults().values().stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    final var validationResultsAsString =
        validationResults.stream()
            .map(ExpectedValidationResult::toString)
            .collect(Collectors.joining(",\n"));

    assertThat(validationResults)
        .describedAs(
            "Expected validation failure%n<%s>%n but actual validation validationResults was%n<%s>",
            expectation, validationResultsAsString)
        .anyMatch(expectation::matches);
  }

  /**
   * Validate the provided {@link BpmnModelInstance}, asserting that should be a valid process
   *
   * @param process the element to validate
   */
  public static void validateProcess(final BpmnModelInstance process) {
    Bpmn.validateModel(process);

    assertThat(
            validate(process).getResults().values().stream().flatMap(Collection::stream).toList())
        .isEmpty();
  }

  private static ValidationResults validate(final BpmnModelInstance model) {
    final ModelWalker walker = new ModelWalker(model);
    final ExpressionLanguage expressionLanguage =
        ExpressionLanguageFactory.createExpressionLanguage(
            new ZeebeFeelEngineClock(InstantSource.system()));
    final ScopedEvaluationContext emptyLookup = ScopedEvaluationContext.NONE_INSTANCE;
    final var expressionProcessor =
        new ExpressionProcessor(
            expressionLanguage,
            emptyLookup,
            EngineConfiguration.DEFAULT_EXPRESSION_EVALUATION_TIMEOUT);
    final ValidationVisitor visitor =
        new ValidationVisitor(
            Stream.of(
                    ZeebeRuntimeValidators.getValidators(expressionLanguage, expressionProcessor),
                    ZeebeDesignTimeValidators.VALIDATORS,
                    ZeebeConfigurationValidators.getValidators(
                        BpmnValidatorConfig.builder()
                            .withMaxIdFieldLength(MAX_ID_FIELD_LENGTH)
                            .withMaxNameFieldLength(MAX_NAME_FIELD_LENGTH)
                            .build()))
                .flatMap(Collection::stream)
                .collect(Collectors.toList()));
    walker.walk(visitor);

    return visitor.getValidationResult();
  }
}
