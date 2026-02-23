/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.deployment.model.validation.ZeebeConfigurationValidators;
import io.camunda.zeebe.engine.processing.deployment.model.validation.ZeebeRuntimeValidators;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.traversal.ModelWalker;
import io.camunda.zeebe.model.bpmn.validation.CompositeValidationVisitor;
import io.camunda.zeebe.model.bpmn.validation.ValidationVisitor;
import io.camunda.zeebe.model.bpmn.validation.zeebe.ZeebeDesignTimeValidators;
import java.io.StringWriter;
import java.util.List;
import org.camunda.bpm.model.xml.impl.validation.ModelValidationResultsImpl;
import org.camunda.bpm.model.xml.validation.ValidationResults;

public final class BpmnValidator {
  private final CompositeValidationVisitor validationVisitor;
  private final ValidationErrorFormatter formatter = new ValidationErrorFormatter();
  private final int validatorResultsOutputMaxSize;

  public BpmnValidator(
      final ExpressionLanguage expressionLanguage,
      final ExpressionProcessor expressionProcessor,
      final BpmnValidatorConfig config) {
    final var designTimeAspectValidator =
        new ValidationVisitor(ZeebeDesignTimeValidators.VALIDATORS);
    final var runtimeAspectValidator =
        new ValidationVisitor(
            ZeebeRuntimeValidators.getValidators(expressionLanguage, expressionProcessor));
    final var configurationAspectValidator =
        new ValidationVisitor(ZeebeConfigurationValidators.getValidators(config));

    validationVisitor =
        new CompositeValidationVisitor(
            designTimeAspectValidator, runtimeAspectValidator, configurationAspectValidator);

    validatorResultsOutputMaxSize = config.validatorResultsOutputMaxSize();
  }

  public String validate(final BpmnModelInstance modelInstance) {
    validationVisitor.reset();

    final ModelWalker walker = new ModelWalker(modelInstance);
    walker.walk(validationVisitor);

    final List<ValidationResults> validationResults = validationVisitor.getValidationResults();

    if (validationResults.stream().anyMatch(ValidationResults::hasErrors)) {
      final StringWriter writer = new StringWriter();
      final var results =
          new ModelValidationResultsImpl(validationResults.toArray(new ValidationResults[0]));
      results.write(writer, formatter, validatorResultsOutputMaxSize);

      return writer.toString();
    } else {
      return null;
    }
  }
}
