/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.validation;

import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.ReportConstants.APPLIED_TO_ALL_DEFINITIONS;

public class DecisionFiltersMustReferenceExistingDefinitionsConstraintValidator
  implements ConstraintValidator<DecisionFiltersMustReferenceExistingDefinitionsConstraint, DecisionReportDataDto> {

  @Override
  public void initialize(final DecisionFiltersMustReferenceExistingDefinitionsConstraint constraintAnnotation) {
    ConstraintValidator.super.initialize(constraintAnnotation);
  }

  @Override
  public boolean isValid(final DecisionReportDataDto value, final ConstraintValidatorContext context) {
    final Set<String> validIdentifiers = value.getDefinitions()
      .stream()
      .map(ReportDataDefinitionDto::getIdentifier)
      .collect(Collectors.toSet());
    return value.getFilter().stream()
      .map(DecisionFilterDto::getAppliedTo)
      .flatMap(Collection::stream)
      .allMatch(appliedTo -> APPLIED_TO_ALL_DEFINITIONS.equals(appliedTo) || validIdentifiers.contains(appliedTo));
  }
}
