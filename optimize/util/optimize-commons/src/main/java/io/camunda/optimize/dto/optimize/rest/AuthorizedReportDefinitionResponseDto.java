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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "AuthorizedReportDefinitionResponseDto(definitionDto=" + getDefinitionDto() + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String definitionDto = "definitionDto";
  }
}
