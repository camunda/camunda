/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.decision.validation;

import static io.camunda.optimize.dto.optimize.ReportConstants.APPLIED_TO_ALL_DEFINITIONS;

import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class DecisionFiltersMustReferenceExistingDefinitionsConstraintValidator
    implements ConstraintValidator<
        DecisionFiltersMustReferenceExistingDefinitionsConstraint, DecisionReportDataDto> {

  @Override
  public void initialize(
      final DecisionFiltersMustReferenceExistingDefinitionsConstraint constraintAnnotation) {
    ConstraintValidator.super.initialize(constraintAnnotation);
  }

  @Override
  public boolean isValid(
      final DecisionReportDataDto value, final ConstraintValidatorContext context) {
    final Set<String> validIdentifiers =
        value.getDefinitions().stream()
            .map(ReportDataDefinitionDto::getIdentifier)
            .collect(Collectors.toSet());
    return value.getFilter().stream()
        .map(DecisionFilterDto::getAppliedTo)
        .flatMap(Collection::stream)
        .allMatch(
            appliedTo ->
                APPLIED_TO_ALL_DEFINITIONS.equals(appliedTo)
                    || validIdentifiers.contains(appliedTo));
  }
}
