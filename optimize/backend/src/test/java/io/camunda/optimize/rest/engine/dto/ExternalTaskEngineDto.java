/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.engine.dto;

import java.util.Objects;

public class ExternalTaskEngineDto {

  protected String id;
  protected String processInstanceId;

  public ExternalTaskEngineDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ExternalTaskEngineDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ExternalTaskEngineDto that = (ExternalTaskEngineDto) o;
    return Objects.equals(id, that.id) && Objects.equals(processInstanceId, that.processInstanceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, processInstanceId);
  }

  @Override
  public String toString() {
    return "ExternalTaskEngineDto(id="
        + getId()
        + ", processInstanceId="
        + getProcessInstanceId()
        + ")";
  }
}
