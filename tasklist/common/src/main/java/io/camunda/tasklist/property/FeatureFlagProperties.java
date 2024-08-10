/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.property;

public class FeatureFlagProperties {

  private Boolean processPublicEndpoints = true;

  public Boolean getProcessPublicEndpoints() {
    return processPublicEndpoints;
  }

  public FeatureFlagProperties setProcessPublicEndpoints(Boolean processPublicEndpoints) {
    this.processPublicEndpoints = processPublicEndpoints;
    return this;
  }
}
