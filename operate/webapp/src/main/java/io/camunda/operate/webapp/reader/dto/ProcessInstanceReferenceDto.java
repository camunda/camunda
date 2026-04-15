/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.reader.dto;

import java.util.Objects;

public class ProcessInstanceReferenceDto {

  private String processDefinitionId;
  private String processDefinitionName;

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public ProcessInstanceReferenceDto setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public String getProcessDefinitionName() {
    return processDefinitionName;
  }

  public ProcessInstanceReferenceDto setProcessDefinitionName(final String processDefinitionName) {
    this.processDefinitionName = processDefinitionName;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(processDefinitionId, processDefinitionName);
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
    return Objects.equals(processDefinitionId, that.processDefinitionId)
        && Objects.equals(processDefinitionName, that.processDefinitionName);
  }
}
