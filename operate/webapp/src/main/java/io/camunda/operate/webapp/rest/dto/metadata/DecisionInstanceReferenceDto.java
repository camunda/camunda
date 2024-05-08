/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.metadata;

import java.util.Objects;

public class DecisionInstanceReferenceDto {

  private String instanceId;
  private String decisionName;

  public String getInstanceId() {
    return instanceId;
  }

  public DecisionInstanceReferenceDto setInstanceId(final String instanceId) {
    this.instanceId = instanceId;
    return this;
  }

  public String getDecisionName() {
    return decisionName;
  }

  public DecisionInstanceReferenceDto setDecisionName(final String decisionName) {
    this.decisionName = decisionName;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(instanceId, decisionName);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DecisionInstanceReferenceDto that = (DecisionInstanceReferenceDto) o;
    return Objects.equals(instanceId, that.instanceId)
        && Objects.equals(decisionName, that.decisionName);
  }
}
