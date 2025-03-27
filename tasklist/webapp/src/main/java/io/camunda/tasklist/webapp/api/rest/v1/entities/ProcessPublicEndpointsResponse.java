/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.camunda.tasklist.webapp.dto.ProcessDTO;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

public class ProcessPublicEndpointsResponse {

  @Schema(description = "The BPMN process ID")
  private String bpmnProcessId;

  @Schema(description = "The process definition key")
  private String processDefinitionKey;

  @Schema(description = "The endpoint associated with the process")
  private String endpoint;

  @Schema(description = "The tenant ID associated with the process")
  private String tenantId;

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ProcessPublicEndpointsResponse setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessPublicEndpointsResponse setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public ProcessPublicEndpointsResponse setEndpoint(final String endpoint) {
    this.endpoint = endpoint;
    return this;
  }

  public static ProcessPublicEndpointsResponse fromProcessDTO(final ProcessDTO process) {
    return new ProcessPublicEndpointsResponse()
        .setBpmnProcessId(process.getProcessDefinitionId())
        .setProcessDefinitionKey(process.getId())
        .setEndpoint(
            String.format(
                TasklistURIs.START_PUBLIC_PROCESS.concat("%s"), process.getProcessDefinitionId()));
  }

  public static ProcessPublicEndpointsResponse fromProcessEntity(final ProcessEntity process) {
    return new ProcessPublicEndpointsResponse()
        .setBpmnProcessId(process.getBpmnProcessId())
        .setProcessDefinitionKey(process.getId())
        .setEndpoint(
            String.format(
                TasklistURIs.START_PUBLIC_PROCESS.concat("%s"), process.getBpmnProcessId()))
        .setTenantId(process.getTenantId());
  }

  public String getTenantId() {
    return tenantId;
  }

  public ProcessPublicEndpointsResponse setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(bpmnProcessId, processDefinitionKey, endpoint, tenantId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessPublicEndpointsResponse that = (ProcessPublicEndpointsResponse) o;
    return Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(endpoint, that.endpoint)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return "ProcessPublicEndpointsResponse{"
        + "bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", processDefinitionKey='"
        + processDefinitionKey
        + '\''
        + ", endpoint='"
        + endpoint
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
