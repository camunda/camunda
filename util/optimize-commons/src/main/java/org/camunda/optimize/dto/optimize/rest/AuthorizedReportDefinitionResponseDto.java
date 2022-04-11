/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.AuthorizedEntityDto;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants
public class AuthorizedReportDefinitionResponseDto extends AuthorizedEntityDto {
  @JsonUnwrapped
  private ReportDefinitionDto definitionDto;

  public AuthorizedReportDefinitionResponseDto(final ReportDefinitionDto definitionDto, final RoleType currentUserRole) {
    super(currentUserRole);
    this.definitionDto = definitionDto;
  }
}
