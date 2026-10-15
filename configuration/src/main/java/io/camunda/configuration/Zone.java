/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.broker.system.configuration.partitioning.ZoneCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.ZoneRole;
import io.camunda.zeebe.util.MemberIdUtil;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Per-region configuration for the {@link Partitioning.Scheme#ZONE_AWARE} partitioning scheme.
 *
 * @param name the name of the zone. see {@link Cluster#getZone()} for more information
 * @param numberOfBrokers the total number of brokers deployed in that zone
 * @param numberOfReplicas the number of replicas for each replication group in the zone; 0 for a
 *     control zone
 * @param priority the priority of the zone: higher priorities are translated to higher raft
 *     priorities.
 * @param role {@code data} (default) or {@code control}: the brokers of a control zone host no data
 *     partitions and only run the system partition
 */
public record Zone(
    String name, int numberOfBrokers, int numberOfReplicas, int priority, @Nullable ZoneRole role) {

  @ConstructorBinding
  public Zone {
    try {
      if (name == null) {
        throw new IllegalArgumentException("name is null");
      }
      MemberIdUtil.validateZone(name);
    } catch (final IllegalArgumentException e) {
      throw new UnifiedConfigurationException(e);
    }
    role = role == null ? ZoneRole.DATA : role;
  }

  public Zone(
      final String name,
      final int numberOfBrokers,
      final int numberOfReplicas,
      final int priority) {
    this(name, numberOfBrokers, numberOfReplicas, priority, null);
  }

  public ZoneCfg toZoneCfg() {
    return new ZoneCfg(name, numberOfBrokers, numberOfReplicas, priority, role);
  }
}
