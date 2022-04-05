/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto;

import java.util.Objects;

public class ProcessInstanceReferenceDto {

  private String instanceId;
  private String processDefinitionId;
  private String processDefinitionName;

  public String getInstanceId() {
    return instanceId;
  }

  public ProcessInstanceReferenceDto setInstanceId(final String instanceId) {
    this.instanceId = instanceId;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public ProcessInstanceReferenceDto setProcessDefinitionId(
      final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public String getProcessDefinitionName() {
    return processDefinitionName;
  }

  public ProcessInstanceReferenceDto setProcessDefinitionName(
      final String processDefinitionName) {
    this.processDefinitionName = processDefinitionName;
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
    final ProcessInstanceReferenceDto that = (ProcessInstanceReferenceDto) o;
    return Objects.equals(instanceId, that.instanceId) &&
        Objects.equals(processDefinitionId, that.processDefinitionId) &&
        Objects.equals(processDefinitionName, that.processDefinitionName);
  }

  @Override
  public int hashCode() {
    return Objects
        .hash(instanceId, processDefinitionId, processDefinitionName);
  }
}
