/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto;

import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

@Schema(description = "Process instances variables request")
public class VariableRequestDto extends PaginatedQuery<VariableRequestDto> {

  @Schema(
      description =
          "Variable scope. Must be processInstanceId for process instance level variables.")
  private String scopeId;

  public String getScopeId() {
    return scopeId;
  }

  public VariableRequestDto setScopeId(final String scopeId) {
    this.scopeId = scopeId;
    return this;
  }

  @Override
  public VariableRequestDto setSorting(final SortingDto sorting) {
    if (sorting != null) {
      throw new InvalidRequestException("Sorting is not supported.");
    }
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), scopeId);
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
    final VariableRequestDto that = (VariableRequestDto) o;
    return Objects.equals(scopeId, that.scopeId);
  }

  public VariableRequestDto createCopy() {
    return new VariableRequestDto()
        .setSearchBefore(getSearchBefore())
        .setSearchAfter(getSearchAfter())
        .setPageSize(getPageSize())
        .setSearchAfterOrEqual(getSearchAfterOrEqual())
        .setSearchBeforeOrEqual(getSearchBeforeOrEqual())
        .setScopeId(scopeId);
  }

  @Override
  public String toString() {
    return "VariableRequestDto{" + "scopeId='" + scopeId + '\'' + '}';
  }
}
