/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class Engine {

  /** Configuration properties for the engine's distribution settings. */
  @NestedConfigurationProperty private Distribution distribution = new Distribution();

  /** Configuration properties for the engine's batch operation settings. */
  @NestedConfigurationProperty
  private EngineBatchOperation batchOperations = new EngineBatchOperation();

  public Distribution getDistribution() {
    return distribution;
  }

  public void setDistribution(final Distribution distribution) {
    this.distribution = distribution;
  }

  public EngineBatchOperation getBatchOperations() {
    return batchOperations;
  }

  public void setBatchOperations(final EngineBatchOperation batchOperations) {
    this.batchOperations = batchOperations;
  }
}
