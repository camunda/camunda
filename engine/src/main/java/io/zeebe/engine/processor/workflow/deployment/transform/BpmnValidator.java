/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.transform;

import io.zeebe.engine.processor.workflow.deployment.model.validation.ZeebeRuntimeValidators;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.traversal.ModelWalker;
import io.zeebe.model.bpmn.validation.ValidationVisitor;
import io.zeebe.model.bpmn.validation.zeebe.ZeebeDesignTimeValidators;
import java.io.StringWriter;
import org.camunda.bpm.model.xml.validation.ValidationResults;

public class BpmnValidator {

  private final ValidationVisitor designTimeAspectValidator;
  private final ValidationVisitor runtimeAspectValidator;

  private final ValidationErrorFormatter formatter = new ValidationErrorFormatter();

  public BpmnValidator() {
    designTimeAspectValidator = new ValidationVisitor(ZeebeDesignTimeValidators.VALIDATORS);
    runtimeAspectValidator = new ValidationVisitor(ZeebeRuntimeValidators.VALIDATORS);
  }

  public String validate(BpmnModelInstance modelInstance) {
    designTimeAspectValidator.reset();
    runtimeAspectValidator.reset();

    final ModelWalker walker = new ModelWalker(modelInstance);
    walker.walk(designTimeAspectValidator);
    walker.walk(runtimeAspectValidator);

    final ValidationResults results1 = designTimeAspectValidator.getValidationResult();
    final ValidationResults results2 = runtimeAspectValidator.getValidationResult();

    if (results1.hasErrors() || results2.hasErrors()) {
      final StringWriter writer = new StringWriter();
      results1.write(writer, formatter);
      results2.write(writer, formatter);

      return writer.toString();
    } else {
      return null;
    }
  }
}
