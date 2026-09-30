/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.engine.processing.deployment.transform.BpmnElementsWithDeploymentBinding;
import io.camunda.zeebe.engine.processing.deployment.transform.ValidationErrorFormatter;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledDecision;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledElement;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeFormDefinition;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import java.io.StringWriter;
import java.util.List;
import org.camunda.bpm.model.xml.impl.validation.ModelValidationResultsImpl;
import org.camunda.bpm.model.xml.impl.validation.ValidationResultsCollectorImpl;
import org.camunda.bpm.model.xml.validation.ValidationResults;

public class BpmnDeploymentBindingValidator {

  private final ValidationErrorFormatter formatter = new ValidationErrorFormatter();
  private final ZeebeCalledElementDeploymentBindingValidator calledElementValidator;
  private final ZeebeCalledDecisionDeploymentBindingValidator calledDecisionValidator;
  private final ZeebeFormDefinitionDeploymentBindingValidator formDefinitionValidator;

  public BpmnDeploymentBindingValidator(final DeploymentRecord deployment) {
    calledElementValidator = new ZeebeCalledElementDeploymentBindingValidator(deployment);
    calledDecisionValidator = new ZeebeCalledDecisionDeploymentBindingValidator(deployment);
    formDefinitionValidator = new ZeebeFormDefinitionDeploymentBindingValidator(deployment);
  }

  public String validate(final BpmnElementsWithDeploymentBinding elements) {
    final var resultsCollector = new ValidationResultsCollectorImpl();

    validateCalledElements(elements.getCalledElements(), resultsCollector);
    validateCalledDecisions(elements.getCalledDecisions(), resultsCollector);
    validateFormDefinitions(elements.getFormDefinitions(), resultsCollector);

    final var validationResults = resultsCollector.getResults();

    return validationResults.hasErrors() ? createErrorMessage(validationResults) : null;
  }

  private void validateCalledElements(
      final List<ZeebeCalledElement> calledElements,
      final ValidationResultsCollectorImpl collector) {
    calledElements.forEach(
        element -> {
          collector.setCurrentElement(element);
          calledElementValidator.validate(element, collector);
        });
  }

  private void validateCalledDecisions(
      final List<ZeebeCalledDecision> calledDecisions,
      final ValidationResultsCollectorImpl collector) {
    calledDecisions.forEach(
        element -> {
          collector.setCurrentElement(element);
          calledDecisionValidator.validate(element, collector);
        });
  }

  private void validateFormDefinitions(
      final List<ZeebeFormDefinition> formDefinitions,
      final ValidationResultsCollectorImpl collector) {
    formDefinitions.forEach(
        element -> {
          collector.setCurrentElement(element);
          formDefinitionValidator.validate(element, collector);
        });
  }

  private String createErrorMessage(final ValidationResults validationResults) {
    final var writer = new StringWriter();
    final var results = new ModelValidationResultsImpl(validationResults);
    results.write(writer, formatter);
    return writer.toString();
  }
}
