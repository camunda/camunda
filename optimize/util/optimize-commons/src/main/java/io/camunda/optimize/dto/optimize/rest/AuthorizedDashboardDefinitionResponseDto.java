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
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import java.util.Objects;

public class AuthorizedDashboardDefinitionResponseDto extends AuthorizedEntityDto {

  @JsonUnwrapped private DashboardDefinitionRestDto definitionDto;

  public AuthorizedDashboardDefinitionResponseDto(
      final RoleType currentUserRole, final DashboardDefinitionRestDto definitionDto) {
    super(currentUserRole);
    this.definitionDto = definitionDto;
  }

  protected AuthorizedDashboardDefinitionResponseDto() {}

  public DashboardDefinitionRestDto getDefinitionDto() {
    return definitionDto;
  }

  @JsonUnwrapped
  public void setDefinitionDto(final DashboardDefinitionRestDto definitionDto) {
    this.definitionDto = definitionDto;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof AuthorizedDashboardDefinitionResponseDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), definitionDto);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final AuthorizedDashboardDefinitionResponseDto that =
        (AuthorizedDashboardDefinitionResponseDto) o;
    return Objects.equals(definitionDto, that.definitionDto);
  }

  @Override
  public String toString() {
    return "AuthorizedDashboardDefinitionResponseDto(definitionDto=" + getDefinitionDto() + ")";
  }
}
