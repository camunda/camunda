/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class FlowNodeInstance {

  public static final String
      KEY = FlowNodeInstanceTemplate.KEY,
      START_DATE = FlowNodeInstanceTemplate.START_DATE,
      END_DATE = FlowNodeInstanceTemplate.END_DATE,
      FLOW_NODE_ID = FlowNodeInstanceTemplate.FLOW_NODE_ID,
      INCIDENT_KEY = FlowNodeInstanceTemplate.INCIDENT_KEY,
      TYPE = FlowNodeInstanceTemplate.TYPE,
      STATE = FlowNodeInstanceTemplate.STATE,
      INCIDENT = FlowNodeInstanceTemplate.INCIDENT,
      PROCESS_INSTANCE_KEY = FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY,
      PROCESS_DEFINITION_KEY = FlowNodeInstanceTemplate.PROCESS_DEFINITION_KEY;

  private Long key;
  private Long processInstanceKey;
  private Long processDefinitionKey;
  private String startDate;
  private String endDate;
  private String flowNodeId;
  private String flowNodeName;
  private Long incidentKey;
  private String type;
  private String state;
  private Boolean incident;

  public Long getKey() {
    return key;
  }

  public FlowNodeInstance setKey(final Long key) {
    this.key = key;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public FlowNodeInstance setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public FlowNodeInstance setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getStartDate() {
    return startDate;
  }

  public FlowNodeInstance setStartDate(final String startDate) {
    this.startDate = startDate;
    return this;
  }

  public String getEndDate() {
    return endDate;
  }

  public FlowNodeInstance setEndDate(final String endDate) {
    this.endDate = endDate;
    return this;
  }

  public String getFlowNodeId() {
   return flowNodeId;
  }

  public FlowNodeInstance setFlowNodeId(String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

   public String getFlowNodeName() {
      return flowNodeName;
   }

   public FlowNodeInstance setFlowNodeName(String flowNodeName) {
      this.flowNodeName = flowNodeName;
      return this;
   }

  public Long getIncidentKey() {
    return incidentKey;
  }

  public FlowNodeInstance setIncidentKey(final Long incidentKey) {
    this.incidentKey = incidentKey;
    return this;
  }

  public String getType() {
    return type;
  }

  public FlowNodeInstance setType(final String type) {
    this.type = type;
    return this;
  }

  public String getState() {
    return state;
  }

  public FlowNodeInstance setState(final String state) {
    this.state = state;
    return this;
  }

  public Boolean getIncident() {
    return incident;
  }

  public FlowNodeInstance setIncident(final Boolean incident) {
    this.incident = incident;
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
    final FlowNodeInstance that = (FlowNodeInstance) o;
    return Objects.equals(key, that.key) && Objects.equals(processInstanceKey, that.processInstanceKey) &&
        Objects.equals(processDefinitionKey, that.processDefinitionKey) &&
        Objects.equals(startDate, that.startDate) && Objects.equals(endDate, that.endDate) &&
        Objects.equals(flowNodeId, that.flowNodeId) && Objects.equals(flowNodeName, that.flowNodeName) &&  Objects.equals(incidentKey, that.incidentKey) &&
        Objects.equals(type, that.type) && Objects.equals(state, that.state) && Objects.equals(incident, that.incident);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, processInstanceKey, processDefinitionKey, startDate, endDate, flowNodeId, flowNodeName, incidentKey, type, state, incident);
  }

  @Override
  public String toString() {
    return "FlowNodeInstance{" +
        "key=" + key +
        ", processInstanceKey=" + processInstanceKey +
        ", processDefinitionKey=" + processDefinitionKey +
        ", startDate='" + startDate + '\'' +
        ", endDate='" + endDate + '\'' +
        ", flowNodeId='" + flowNodeId + '\'' +
        ", flowNodeName='" + flowNodeName + '\'' +
        ", incidentKey=" + incidentKey +
        ", type='" + type + '\'' +
        ", state='" + state + '\'' +
        ", incident=" + incident +
        '}';
  }
}
