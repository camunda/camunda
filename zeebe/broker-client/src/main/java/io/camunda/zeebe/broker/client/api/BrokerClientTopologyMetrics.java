/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

import io.camunda.zeebe.broker.client.api.BrokerClientMetricsDoc.PartitionRoleValues;
import io.camunda.zeebe.broker.client.impl.BrokerClientTopologyMetricsImpl;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Metrics related to the cluster topology as seen by the current gateway. See {@link
 * BrokerClientMetricsDoc} for documentation on the specific metrics.
 */
public interface BrokerClientTopologyMetrics {
  Noop NOOP = new Noop();

  /** Returns an implementation which will register and updates metrics on the given registry */
  static BrokerClientTopologyMetrics of(final MeterRegistry meterRegistry) {
    return new BrokerClientTopologyMetricsImpl(meterRegistry);
  }

  /**
   * Sets the role of the broker with the given {@code brokerId} for the partition with the given
   * {@code partitionId}
   */
  void setRoleForPartition(
      final int partitionId, final int brokerId, final PartitionRoleValues roleValue);

  /** An implementation which simply does nothing, mostly useful for testing. */
  final class Noop implements BrokerClientTopologyMetrics {

    @Override
    public void setRoleForPartition(
        final int partitionId, final int brokerId, final PartitionRoleValues roleValue) {}
  }
}
