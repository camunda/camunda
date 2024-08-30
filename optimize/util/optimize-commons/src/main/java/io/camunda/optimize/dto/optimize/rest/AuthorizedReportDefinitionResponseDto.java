/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.camunda.optimize.dto.optimize.AuthorizedEntityDto;
import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
@EqualsAndHashCode(callSuper = true)
public class AuthorizedReportDefinitionResponseDto extends AuthorizedEntityDto {

  @JsonUnwrapped private ReportDefinitionDto definitionDto;

  public AuthorizedReportDefinitionResponseDto(
      final ReportDefinitionDto definitionDto, final RoleType currentUserRole) {
    super(currentUserRole);
    this.definitionDto = definitionDto;
  }

  public static final class Fields {

    public static final String definitionDto = "definitionDto";
  }
}
