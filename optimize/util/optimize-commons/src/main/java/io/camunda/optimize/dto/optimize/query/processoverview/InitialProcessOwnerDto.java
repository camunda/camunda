/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.processoverview;

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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $processDefinitionKey = getProcessDefinitionKey();
    result =
        result * PRIME + ($processDefinitionKey == null ? 43 : $processDefinitionKey.hashCode());
    final Object $owner = getOwner();
    result = result * PRIME + ($owner == null ? 43 : $owner.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof InitialProcessOwnerDto)) {
      return false;
    }
    final InitialProcessOwnerDto other = (InitialProcessOwnerDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$processDefinitionKey = getProcessDefinitionKey();
    final Object other$processDefinitionKey = other.getProcessDefinitionKey();
    if (this$processDefinitionKey == null
        ? other$processDefinitionKey != null
        : !this$processDefinitionKey.equals(other$processDefinitionKey)) {
      return false;
    }
    final Object this$owner = getOwner();
    final Object other$owner = other.getOwner();
    if (this$owner == null ? other$owner != null : !this$owner.equals(other$owner)) {
      return false;
    }
    return true;
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
