/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.broker.system.configuration.partitioning.RegionCfg;

/**
 * Per-region configuration for the {@link Partitioning.Scheme#REGION_AWARE} partitioning scheme.
 */
public record Region(String name, int numberOfBrokers, int numberOfReplicas, int priority) {

  public RegionCfg toRegionCfg() {
    return new RegionCfg(name, numberOfReplicas, numberOfBrokers, priority);
  }
}
