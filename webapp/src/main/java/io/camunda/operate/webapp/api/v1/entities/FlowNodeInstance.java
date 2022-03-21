/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
      INCIDENT_KEY = FlowNodeInstanceTemplate.INCIDENT_KEY,
      TYPE = FlowNodeInstanceTemplate.TYPE,
      STATE = FlowNodeInstanceTemplate.STATE,
      INCIDENT = FlowNodeInstanceTemplate.INCIDENT,
      PROCESS_INSTANCE_KEY = FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY;

  private Long key;
  private Long processInstanceKey;

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public FlowNodeInstance setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  private String startDate;
  private String endDate;
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
    return Objects.equals(key, that.key) && Objects.equals(processInstanceKey,
        that.processInstanceKey) && Objects.equals(startDate, that.startDate)
        && Objects.equals(endDate, that.endDate) && Objects.equals(incidentKey,
        that.incidentKey) && Objects.equals(type, that.type) && Objects.equals(
        state, that.state) && Objects.equals(incident, that.incident);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, processInstanceKey, startDate, endDate, incidentKey, type, state,
        incident);
  }

  @Override
  public String toString() {
    return "FlowNodeInstance{" +
        "key=" + key +
        ", processInstanceKey=" + processInstanceKey +
        ", startDate='" + startDate + '\'' +
        ", endDate='" + endDate + '\'' +
        ", incidentKey=" + incidentKey +
        ", type='" + type + '\'' +
        ", state='" + state + '\'' +
        ", incident=" + incident +
        '}';
  }
}
