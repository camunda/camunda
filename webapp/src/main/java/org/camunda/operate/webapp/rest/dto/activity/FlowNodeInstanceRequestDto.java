/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto.activity;

import io.swagger.annotations.ApiModelProperty;
import java.util.Arrays;
import java.util.Objects;

/**
 * The request to get the list of batch operations, created by current user.
 */
public class FlowNodeInstanceRequestDto {

  private String workflowInstanceId;

  private String parentTreePath;

  private int level;

  private Object[] searchBefore;

  /**
   * Search for the batch operations that goes exactly after the given sort values.
   */
  private Object[] searchAfter;
  /**
   * Page size.
   */
  private Integer pageSize = 50;

  public FlowNodeInstanceRequestDto() {
  }

  public FlowNodeInstanceRequestDto(final String workflowInstanceId, final String parentTreePath) {
    this.workflowInstanceId = workflowInstanceId;
    this.parentTreePath = parentTreePath;
  }

  public String getWorkflowInstanceId() {
    return workflowInstanceId;
  }

  public void setWorkflowInstanceId(final String workflowInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
  }

  public String getParentTreePath() {
    return parentTreePath;
  }

  public void setParentTreePath(final String parentTreePath) {
    this.parentTreePath = parentTreePath;
  }

  public int getLevel() {
    return level;
  }

  public void setLevel(final int level) {
    this.level = level;
  }

  @ApiModelProperty(value= "Array of two strings: copy/paste of sortValues field from one of the operations.",
      example = "[\"9223372036854775807\", \"1583836503404\"]")
  public Object[] getSearchBefore() {
    return searchBefore;
  }

  public FlowNodeInstanceRequestDto setSearchBefore(Object[] searchBefore) {
    this.searchBefore = searchBefore;
    return this;
  }

  @ApiModelProperty(value= "Array of two strings: copy/paste of sortValues field from one of the operations.",
      example = "[\"1583836151645\", \"1583836128180\"]")
  public Object[] getSearchAfter() {
    return searchAfter;
  }

  public FlowNodeInstanceRequestDto setSearchAfter(Object[] searchAfter) {
    this.searchAfter = searchAfter;
    return this;
  }

  public Integer getPageSize() {
    return pageSize;
  }

  public FlowNodeInstanceRequestDto setPageSize(Integer pageSize) {
    this.pageSize = pageSize;
    return this;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FlowNodeInstanceRequestDto that = (FlowNodeInstanceRequestDto) o;
    return level == that.level &&
        Objects.equals(workflowInstanceId, that.workflowInstanceId) &&
        Objects.equals(parentTreePath, that.parentTreePath) &&
        Arrays.equals(searchBefore, that.searchBefore) &&
        Arrays.equals(searchAfter, that.searchAfter) &&
        Objects.equals(pageSize, that.pageSize);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(workflowInstanceId, parentTreePath, level, pageSize);
    result = 31 * result + Arrays.hashCode(searchBefore);
    result = 31 * result + Arrays.hashCode(searchAfter);
    return result;
  }
}
