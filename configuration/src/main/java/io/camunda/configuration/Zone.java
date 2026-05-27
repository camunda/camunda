/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.broker.system.configuration.partitioning.ZoneCfg;
import io.camunda.zeebe.util.MemberIdUtil;

/** Per-region configuration for the {@link Partitioning.Scheme#ZONE_AWARE} partitioning scheme. */
public record Zone(String name, int numberOfBrokers, int numberOfReplicas, int priority) {

  public Zone {
    try {
      if (name == null) {
        throw new IllegalArgumentException("name is null");
      }
      MemberIdUtil.validateZone(name);
    } catch (final IllegalArgumentException e) {
      throw new UnifiedConfigurationException(e);
    }
  }

  public ZoneCfg toZoneCfg() {
    return new ZoneCfg(name, numberOfBrokers, numberOfReplicas, priority);
  }
}
