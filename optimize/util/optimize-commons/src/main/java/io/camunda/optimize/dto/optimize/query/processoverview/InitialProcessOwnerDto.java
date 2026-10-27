/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.processoverview;

import java.util.Objects;

public class InitialProcessOwnerDto {

  private String processDefinitionKey;
  private String owner;

  public InitialProcessOwnerDto(final String processDefinitionKey, final String owner) {
    this.processDefinitionKey = processDefinitionKey;
    this.owner = owner;
  }

  public InitialProcessOwnerDto() {}

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(final String owner) {
    this.owner = owner;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof InitialProcessOwnerDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final InitialProcessOwnerDto that = (InitialProcessOwnerDto) o;
    return Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(owner, that.owner);
  }

  @Override
  public int hashCode() {
    return Objects.hash(processDefinitionKey, owner);
  }

  @Override
  public String toString() {
    return "InitialProcessOwnerDto(processDefinitionKey="
        + getProcessDefinitionKey()
        + ", owner="
        + getOwner()
        + ")";
  }
}
