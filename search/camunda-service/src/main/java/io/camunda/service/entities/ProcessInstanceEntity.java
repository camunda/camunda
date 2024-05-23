/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProcessInstanceEntity extends BaseEntity {

  private Integer processVersion;
  private String bpmnProcessId;

  private Long parentKey;
  private Long parentFlowNodeInstanceKey;

  private String startDate;
  private String endDate;

  public Integer getProcessVersion() {
    return processVersion;
  }

  public ProcessInstanceEntity setProcessVersion(final Integer processVersion) {
    this.processVersion = processVersion;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ProcessInstanceEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public Long getParentKey() {
    return parentKey;
  }

  public ProcessInstanceEntity setParentKey(final Long parentKey) {
    this.parentKey = parentKey;
    return this;
  }

  public Long getParentFlowNodeInstanceKey() {
    return parentFlowNodeInstanceKey;
  }

  public ProcessInstanceEntity setParentFlowNodeInstanceKey(final Long parentFlowNodeInstanceKey) {
    this.parentFlowNodeInstanceKey = parentFlowNodeInstanceKey;
    return this;
  }

  public String getStartDate() {
    return startDate;
  }

  public ProcessInstanceEntity setStartDate(final String startDate) {
    this.startDate = startDate;
    return this;
  }

  public String getEndDate() {
    return endDate;
  }

  public ProcessInstanceEntity setEndDate(final String endDate) {
    this.endDate = endDate;
    return this;
  }

  @Override
  public String toString() {
    return "ProcessInstanceEntity [processVersion="
        + processVersion
        + ", bpmnProcessId="
        + bpmnProcessId
        + ", parentKey="
        + parentKey
        + ", parentFlowNodeInstanceKey="
        + parentFlowNodeInstanceKey
        + ", startDate="
        + startDate
        + ", endDate="
        + endDate
        + "]";
  }
}
