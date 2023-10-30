/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class Incident {

  public static final String
    KEY = IncidentTemplate.KEY,
    PROCESS_DEFINITION_KEY = IncidentTemplate.PROCESS_DEFINITION_KEY,
    PROCESS_INSTANCE_KEY = IncidentTemplate.PROCESS_INSTANCE_KEY,
    TYPE = IncidentTemplate.ERROR_TYPE,
    MESSAGE = IncidentTemplate.ERROR_MSG,
    CREATION_TIME = IncidentTemplate.CREATION_TIME,
    STATE = IncidentTemplate.STATE,
    JOB_KEY = IncidentTemplate.JOB_KEY,
    TENANT_ID = IncidentTemplate.TENANT_ID;

  public static final String MESSAGE_FIELD = "message";
  public static final String TYPE_FIELD = "type";
  public static final Map<String,String> OBJECT_TO_ELASTICSEARCH = Map.of(TYPE_FIELD, TYPE, MESSAGE_FIELD, MESSAGE);

  private Long key;
  private Long processDefinitionKey;
  private Long processInstanceKey;
  @Schema(implementation = ErrorType.class)
  private String type;
  private String message;
  private String creationTime;
  @Schema(implementation = IncidentState.class)
  private String state;
  private Long jobKey;
  private String tenantId;

  public Long getKey() {
    return key;
  }

  public Incident setKey(final Long key) {
    this.key = key;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public Incident setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public Incident setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public String getType() {
    return type;
  }

  public Incident setType(final String type) {
    this.type = type;
    return this;
  }

  public String getMessage() {
    return message;
  }

  public Incident setMessage(final String message) {
    this.message = message;
    return this;
  }

  public String getCreationTime() {
    return creationTime;
  }

  public Incident setCreationTime(final String creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public String getState() {
    return state;
  }

  public Incident setState(final String state) {
    this.state = state;
    return this;
  }

  public Long getJobKey() {
    return jobKey;
  }

  public Incident setJobKey(final Long jobKey) {
    this.jobKey = jobKey;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public Incident setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    Incident incident = (Incident) o;
    return Objects.equals(key, incident.key) && Objects.equals(processDefinitionKey,
        incident.processDefinitionKey) && Objects.equals(processInstanceKey,
        incident.processInstanceKey) && Objects.equals(type, incident.type) && Objects.equals(message,
        incident.message) && Objects.equals(creationTime, incident.creationTime) && Objects.equals(state,
        incident.state) && Objects.equals(jobKey, incident.jobKey) && Objects.equals(tenantId, incident.tenantId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, processDefinitionKey, processInstanceKey, type, message, creationTime, state, jobKey, tenantId);
  }

  @Override
  public String toString() {
    return "Incident{" + "key=" + key + ", processDefinitionKey=" + processDefinitionKey + ", processInstanceKey="
        + processInstanceKey + ", type='" + type + '\'' + ", message='" + message + '\'' + ", creationTime='" + creationTime
        + '\'' + ", state='" + state + '\'' + ", jobKey='" + jobKey + '\'' + ", tenantId='" + tenantId + '\'' + '}';
  }
}