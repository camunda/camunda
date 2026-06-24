/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledDecision;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import java.util.List;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ZeebeCalledDecisionDeploymentBindingValidator
    implements ModelElementValidator<ZeebeCalledDecision> {

  private final List<DecisionRecordValue> decisionsMetadata;

  public ZeebeCalledDecisionDeploymentBindingValidator(final DeploymentRecord deployment) {
    decisionsMetadata = deployment.getDecisionsMetadata();
  }

  @Override
  public Class<ZeebeCalledDecision> getElementType() {
    return ZeebeCalledDecision.class;
  }

  @Override
  public void validate(
      final ZeebeCalledDecision calledDecision,
      final ValidationResultCollector validationResultCollector) {
    if (decisionsMetadata.stream()
        .noneMatch(decision -> calledDecision.getDecisionId().equals(decision.getDecisionId()))) {
      validationResultCollector.addError(
          0,
          "Expected to find decision with id '%s' in current deployment, but not found."
              .formatted(calledDecision.getDecisionId()));
    }
  }
}
