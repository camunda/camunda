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

public class AuthorizedReportDefinitionResponseDto extends AuthorizedEntityDto {

  @JsonUnwrapped private ReportDefinitionDto definitionDto;

  public AuthorizedReportDefinitionResponseDto(
      final ReportDefinitionDto definitionDto, final RoleType currentUserRole) {
    super(currentUserRole);
    this.definitionDto = definitionDto;
  }

  protected AuthorizedReportDefinitionResponseDto() {}

  public ReportDefinitionDto getDefinitionDto() {
    return definitionDto;
  }

  @JsonUnwrapped
  public void setDefinitionDto(final ReportDefinitionDto definitionDto) {
    this.definitionDto = definitionDto;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof AuthorizedReportDefinitionResponseDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $definitionDto = getDefinitionDto();
    result = result * PRIME + ($definitionDto == null ? 43 : $definitionDto.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AuthorizedReportDefinitionResponseDto)) {
      return false;
    }
    final AuthorizedReportDefinitionResponseDto other = (AuthorizedReportDefinitionResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$definitionDto = getDefinitionDto();
    final Object other$definitionDto = other.getDefinitionDto();
    if (this$definitionDto == null
        ? other$definitionDto != null
        : !this$definitionDto.equals(other$definitionDto)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "AuthorizedReportDefinitionResponseDto(definitionDto=" + getDefinitionDto() + ")";
  }

  public static final class Fields {

    public static final String definitionDto = "definitionDto";
  }
}
