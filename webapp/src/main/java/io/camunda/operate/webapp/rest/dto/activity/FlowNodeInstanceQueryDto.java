/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.activity;

import io.swagger.annotations.ApiModelProperty;
import java.util.Arrays;
import java.util.Objects;

/**
 * The query to get the list of batch operations, created by current user.
 */
public class FlowNodeInstanceQueryDto {

  private String processInstanceId;

  private String treePath;

  /**
   * Search for the flow node instances that goes before the given sort values
   */
  private Object[] searchBefore;
  /**
   * Search for the flow node instances that goes before the given sort values plus same sort values.
   */
  private Object[] searchBeforeOrEqual;
  /**
   * Search for the flow node instances that goes exactly after the given sort values.
   */
  private Object[] searchAfter;
  /**
   * Search for the flow node instances that goes after the given sort values plus same sort values.
   */
  private Object[] searchAfterOrEqual;

  /**
   * Page size.
   */
  private Integer pageSize;

  public FlowNodeInstanceQueryDto() {
  }

  public FlowNodeInstanceQueryDto(final String processInstanceId, final String treePath) {
    this.processInstanceId = processInstanceId;
    this.treePath = treePath;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public FlowNodeInstanceQueryDto setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public String getTreePath() {
    return treePath;
  }

  public FlowNodeInstanceQueryDto setTreePath(final String treePath) {
    this.treePath = treePath;
    return this;
  }

  @ApiModelProperty(value= "Array of two strings: copy/paste of sortValues field from one of the operations.",
      example = "[\"9223372036854775807\", \"1583836503404\"]")
  public Object[] getSearchBefore() {
    return searchBefore;
  }

  public FlowNodeInstanceQueryDto setSearchBefore(Object[] searchBefore) {
    this.searchBefore = searchBefore;
    return this;
  }

  public Object[] getSearchBeforeOrEqual() {
    return searchBeforeOrEqual;
  }

  public FlowNodeInstanceQueryDto setSearchBeforeOrEqual(final Object[] searchBeforeOrEqual) {
    this.searchBeforeOrEqual = searchBeforeOrEqual;
    return this;
  }

  @ApiModelProperty(value= "Array of two strings: copy/paste of sortValues field from one of the operations.",
      example = "[\"1583836151645\", \"1583836128180\"]")
  public Object[] getSearchAfter() {
    return searchAfter;
  }

  public FlowNodeInstanceQueryDto setSearchAfter(Object[] searchAfter) {
    this.searchAfter = searchAfter;
    return this;
  }

  public Object[] getSearchAfterOrEqual() {
    return searchAfterOrEqual;
  }

  public FlowNodeInstanceQueryDto setSearchAfterOrEqual(final Object[] searchAfterOrEqual) {
    this.searchAfterOrEqual = searchAfterOrEqual;
    return this;
  }

  public Integer getPageSize() {
    return pageSize;
  }

  public FlowNodeInstanceQueryDto setPageSize(Integer pageSize) {
    this.pageSize = pageSize;
    return this;
  }

  public FlowNodeInstanceQueryDto createCopy() {
    return new FlowNodeInstanceQueryDto()
        .setSearchBefore(this.searchBefore)
        .setSearchAfter(this.searchAfter)
        .setPageSize(this.pageSize)
        .setSearchAfterOrEqual(this.searchAfterOrEqual)
        .setSearchBeforeOrEqual(this.searchBeforeOrEqual)
        .setTreePath(this.treePath)
        .setProcessInstanceId(this.processInstanceId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FlowNodeInstanceQueryDto queryDto = (FlowNodeInstanceQueryDto) o;
    return Objects.equals(processInstanceId, queryDto.processInstanceId) &&
        Objects.equals(treePath, queryDto.treePath) &&
        Arrays.equals(searchBefore, queryDto.searchBefore) &&
        Arrays.equals(searchBeforeOrEqual, queryDto.searchBeforeOrEqual) &&
        Arrays.equals(searchAfter, queryDto.searchAfter) &&
        Arrays.equals(searchAfterOrEqual, queryDto.searchAfterOrEqual) &&
        Objects.equals(pageSize, queryDto.pageSize);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(processInstanceId, treePath, pageSize);
    result = 31 * result + Arrays.hashCode(searchBefore);
    result = 31 * result + Arrays.hashCode(searchBeforeOrEqual);
    result = 31 * result + Arrays.hashCode(searchAfter);
    result = 31 * result + Arrays.hashCode(searchAfterOrEqual);
    return result;
  }
}
