/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
public class AuthorizedDashboardDefinitionDto extends AuthorizedEntityDto {
  @JsonUnwrapped
  private DashboardDefinitionDto definitionDto;

  public AuthorizedDashboardDefinitionDto(final RoleType currentUserRole, final DashboardDefinitionDto definitionDto) {
    super(currentUserRole);
    this.definitionDto = definitionDto;
  }
}
