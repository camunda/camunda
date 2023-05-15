/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.camunda.tasklist.webapp.graphql.entity.ProcessDTO;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import java.util.Objects;

public class ProcessPublicEndpointsResponse {

  private String processId;
  private String processDefinitionKey;
  private String endpoint;

  public String getProcessId() {
    return processId;
  }

  public ProcessPublicEndpointsResponse setProcessId(String processId) {
    this.processId = processId;
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
        .setProcessId(process.getId())
        .setProcessDefinitionKey(process.getProcessDefinitionId())
        .setEndpoint(
            String.format(
                TasklistURIs.START_PUBLIC_PROCESS.concat("%s"), process.getProcessDefinitionId()));
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
    return Objects.equals(processId, that.processId)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(endpoint, that.endpoint);
  }

  @Override
  public int hashCode() {
    return Objects.hash(processId, processDefinitionKey, endpoint);
  }
}
