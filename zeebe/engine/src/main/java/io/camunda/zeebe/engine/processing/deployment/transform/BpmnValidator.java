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
import io.camunda.zeebe.engine.processing.deployment.model.validation.ZeebeRuntimeValidators;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.traversal.ModelWalker;
import io.camunda.zeebe.model.bpmn.validation.ValidationVisitor;
import io.camunda.zeebe.model.bpmn.validation.zeebe.ZeebeDesignTimeValidators;
import java.io.StringWriter;
import org.camunda.bpm.model.xml.impl.validation.ModelValidationResultsImpl;
import org.camunda.bpm.model.xml.validation.ValidationResults;

public final class BpmnValidator {
  // 2kb is half of the default nginx ingress proxy header size
  public static final int VALIDATION_RESULTS_OUTPUT_MAX_SIZE = 2000;
  private final ValidationVisitor designTimeAspectValidator;
  private final ValidationVisitor runtimeAspectValidator;

  private final ValidationErrorFormatter formatter = new ValidationErrorFormatter();

  public BpmnValidator(
      final ExpressionLanguage expressionLanguage, final ExpressionProcessor expressionProcessor) {
    designTimeAspectValidator = new ValidationVisitor(ZeebeDesignTimeValidators.VALIDATORS);
    runtimeAspectValidator =
        new ValidationVisitor(
            ZeebeRuntimeValidators.getValidators(expressionLanguage, expressionProcessor));
  }

  public String validate(final BpmnModelInstance modelInstance) {
    designTimeAspectValidator.reset();
    runtimeAspectValidator.reset();

    final ModelWalker walker = new ModelWalker(modelInstance);
    walker.walk(designTimeAspectValidator);
    walker.walk(runtimeAspectValidator);

    final ValidationResults results1 = designTimeAspectValidator.getValidationResult();
    final ValidationResults results2 = runtimeAspectValidator.getValidationResult();

    if (results1.hasErrors() || results2.hasErrors()) {
      final StringWriter writer = new StringWriter();
      final var results = new ModelValidationResultsImpl(results1, results2);
      results.write(writer, formatter, VALIDATION_RESULTS_OUTPUT_MAX_SIZE);

      return writer.toString();
    } else {
      return null;
    }
  }
}
