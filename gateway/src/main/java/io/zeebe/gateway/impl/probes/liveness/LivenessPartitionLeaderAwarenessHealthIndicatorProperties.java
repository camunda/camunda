/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl.probes.liveness;

import io.zeebe.util.health.AbstractDelayedHealthIndicatorProperties;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "management.health.liveness.gateway-partitionleaderawareness")
public class LivenessPartitionLeaderAwarenessHealthIndicatorProperties
    extends AbstractDelayedHealthIndicatorProperties {

  @Override
  protected Duration getDefaultMaxDowntime() {
    return Duration.ofMinutes(5);
  }
}
