/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

import static io.camunda.zeebe.broker.client.api.BrokerClientMetricsDoc.PARTITION_ROLE;

import io.camunda.zeebe.broker.client.api.BrokerClientMetricsDoc.PartitionRoleValues;
import io.camunda.zeebe.broker.client.api.BrokerClientMetricsDoc.TopologyKeyNames;
import io.camunda.zeebe.util.collection.Table;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Metrics related to the cluster topology as seen by the current gateway. See {@link
 * BrokerClientMetricsDoc} for documentation on the specific metrics.
 */
public final class BrokerClientTopologyMetrics {

  private final MeterRegistry registry;
  private final Table<Integer, Integer, AtomicInteger> brokerTopologyRole;

  public BrokerClientTopologyMetrics(final MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "must specify a meter registry");
    brokerTopologyRole = Table.simple();
  }

  /**
   * Sets the role of the broker with the given {@code brokerId} for the partition with the given
   * {@code partitionId}
   */
  public void setRoleForPartition(
      final int partitionId, final int brokerId, final PartitionRoleValues roleValue) {
    brokerTopologyRole
        .computeIfAbsent(partitionId, brokerId, this::registerBrokerTopologyRole)
        .set(roleValue.value());
  }

  private AtomicInteger registerBrokerTopologyRole(final int partitionId, final int brokerId) {
    final var role = new AtomicInteger();
    Gauge.builder(PARTITION_ROLE.getName(), role, Number::intValue)
        .description(PARTITION_ROLE.getDescription())
        .tag(PartitionKeyNames.PARTITION.asString(), String.valueOf(partitionId))
        .tag(TopologyKeyNames.BROKER.asString(), String.valueOf(brokerId))
        .register(registry);
    return role;
  }
}
