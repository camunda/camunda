/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;

@ApiModel("Process instances variables request")
public class VariableRequestDto extends PaginatedQuery<VariableRequestDto> {

  @ApiModelProperty(
      value = "Variable scope. Must be processInstanceId for process instance level variables.")
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

  public VariableRequestDto createCopy() {
    return new VariableRequestDto()
        .setSearchBefore(getSearchBefore())
        .setSearchAfter(getSearchAfter())
        .setPageSize(getPageSize())
        .setSearchAfterOrEqual(getSearchAfterOrEqual())
        .setSearchBeforeOrEqual(getSearchBeforeOrEqual())
        .setScopeId(this.scopeId);
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

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), scopeId);
  }

  @Override
  public String toString() {
    return "VariableRequestDto{" +
        "scopeId='" + scopeId + '\'' +
        '}';
  }
}
