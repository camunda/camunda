/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.webapp.graphql.entity.ProcessDTO;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import java.util.Objects;

public class ProcessPublicEndpointsResponse {

  private String bpmnProcessId;
  private String processDefinitionKey;
  private String endpoint;
  private String tenantId;

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ProcessPublicEndpointsResponse setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessPublicEndpointsResponse setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public ProcessPublicEndpointsResponse setEndpoint(String endpoint) {
    this.endpoint = endpoint;
    return this;
  }

  public static ProcessPublicEndpointsResponse fromProcessDTO(ProcessDTO process) {
    return new ProcessPublicEndpointsResponse()
        .setBpmnProcessId(process.getProcessDefinitionId())
        .setProcessDefinitionKey(process.getId())
        .setEndpoint(
            String.format(
                TasklistURIs.START_PUBLIC_PROCESS.concat("%s"), process.getProcessDefinitionId()));
  }

  public static ProcessPublicEndpointsResponse fromProcessEntity(ProcessEntity process) {
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

  public ProcessPublicEndpointsResponse setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public boolean equals(Object o) {
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
  public int hashCode() {
    return Objects.hash(bpmnProcessId, processDefinitionKey, endpoint, tenantId);
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
