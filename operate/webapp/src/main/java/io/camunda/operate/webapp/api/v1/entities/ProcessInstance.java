/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ProcessInstance {

  public static final String KEY = ListViewTemplate.PROCESS_INSTANCE_KEY,
      VERSION = ListViewTemplate.PROCESS_VERSION,
      VERSION_TAG = ListViewTemplate.PROCESS_VERSION_TAG,
      BPMN_PROCESS_ID = ListViewTemplate.BPMN_PROCESS_ID,
      PROCESS_DEFINITION_KEY = ListViewTemplate.PROCESS_KEY,
      PARENT_KEY = ListViewTemplate.PARENT_PROCESS_INSTANCE_KEY,
      PARENT_FLOW_NODE_INSTANCE_KEY = ListViewTemplate.PARENT_FLOW_NODE_INSTANCE_KEY,
      START_DATE = ListViewTemplate.START_DATE,
      END_DATE = ListViewTemplate.END_DATE,
      STATE = ListViewTemplate.STATE,
      INCIDENT = ListViewTemplate.INCIDENT,
      TENANT_ID = ListViewTemplate.TENANT_ID;

  private Long key;
  private Integer processVersion;
  private String processVersionTag;
  private String bpmnProcessId;
  private Long parentKey;
  private Long parentFlowNodeInstanceKey;
  private String startDate;
  private String endDate;

  @Schema(implementation = ProcessInstanceState.class)
  private String state;

  private Boolean incident;

  private Long processDefinitionKey;
  private String tenantId;

  public Long getKey() {
    return key;
  }

  public ProcessInstance setKey(final long key) {
    this.key = key;
    return this;
  }

  public Integer getProcessVersion() {
    return processVersion;
  }

  public ProcessInstance setProcessVersion(final int processVersion) {
    this.processVersion = processVersion;
    return this;
  }

  public String getProcessVersionTag() {
    return processVersionTag;
  }

  public ProcessInstance setProcessVersionTag(final String processVersionTag) {
    this.processVersionTag = processVersionTag;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ProcessInstance setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public Long getParentKey() {
    return parentKey;
  }

  public ProcessInstance setParentKey(final Long parentKey) {
    this.parentKey = parentKey;
    return this;
  }

  @JsonProperty("parentProcessInstanceKey")
  public ProcessInstance setParentProcessInstanceKey(final Long parentProcessInstanceKey) {
    parentKey = parentProcessInstanceKey;
    return this;
  }

  public Long getParentFlowNodeInstanceKey() {
    return parentFlowNodeInstanceKey;
  }

  public ProcessInstance setParentFlowNodeInstanceKey(final Long parentFlowNodeInstanceKey) {
    this.parentFlowNodeInstanceKey = parentFlowNodeInstanceKey;
    return this;
  }

  public String getStartDate() {
    return startDate;
  }

  public ProcessInstance setStartDate(final String startDate) {
    this.startDate = startDate;
    return this;
  }

  public String getEndDate() {
    return endDate;
  }

  public ProcessInstance setEndDate(final String endDate) {
    this.endDate = endDate;
    return this;
  }

  public String getState() {
    return state;
  }

  public ProcessInstance setState(final String state) {
    this.state = state;
    return this;
  }

  public Boolean getIncident() {
    return incident;
  }

  public ProcessInstance setIncident(final Boolean incident) {
    this.incident = incident;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessInstance setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public ProcessInstance setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        key,
        processVersion,
        processVersionTag,
        bpmnProcessId,
        parentKey,
        parentFlowNodeInstanceKey,
        startDate,
        endDate,
        state,
        incident,
        processDefinitionKey,
        tenantId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessInstance that = (ProcessInstance) o;
    return Objects.equals(key, that.key)
        && Objects.equals(processVersion, that.processVersion)
        && Objects.equals(processVersionTag, that.processVersionTag)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(parentKey, that.parentKey)
        && Objects.equals(parentFlowNodeInstanceKey, that.parentFlowNodeInstanceKey)
        && Objects.equals(startDate, that.startDate)
        && Objects.equals(endDate, that.endDate)
        && Objects.equals(state, that.state)
        && Objects.equals(incident, that.incident)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return "ProcessInstance{"
        + "key="
        + key
        + ", processVersion="
        + processVersion
        + ", processVersionTag="
        + processVersionTag
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", parentKey="
        + parentKey
        + ", parentFlowNodeInstanceKey="
        + parentFlowNodeInstanceKey
        + ", startDate='"
        + startDate
        + '\''
        + ", endDate='"
        + endDate
        + '\''
        + ", state='"
        + state
        + '\''
        + ", incident="
        + incident
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
