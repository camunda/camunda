/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.validation;

import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.ReportConstants.APPLIED_TO_ALL_DEFINITIONS;

public class ProcessFiltersMustReferenceExistingDefinitionsConstraintValidator
  implements ConstraintValidator<ProcessFiltersMustReferenceExistingDefinitionsConstraint, ProcessReportDataDto> {

  @Override
  public void initialize(final ProcessFiltersMustReferenceExistingDefinitionsConstraint constraintAnnotation) {
    ConstraintValidator.super.initialize(constraintAnnotation);
  }

  @Override
  public boolean isValid(final ProcessReportDataDto value, final ConstraintValidatorContext context) {
    final Set<String> validIdentifiers = value.getDefinitions()
      .stream()
      .map(ReportDataDefinitionDto::getIdentifier)
      .collect(Collectors.toSet());
    return value.getFilter().stream()
      .map(ProcessFilterDto::getAppliedTo)
      .flatMap(Collection::stream)
      .allMatch(appliedTo -> APPLIED_TO_ALL_DEFINITIONS.equals(appliedTo) || validIdentifiers.contains(appliedTo));
  }
}
