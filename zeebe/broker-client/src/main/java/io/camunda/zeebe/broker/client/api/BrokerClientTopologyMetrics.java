/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.client.api;

import io.camunda.zeebe.broker.client.api.BrokerClientMetricsDoc.PartitionRoleValues;
import io.camunda.zeebe.broker.client.impl.BrokerClientTopologyMetricsImpl;
import io.micrometer.core.instrument.MeterRegistry;

public interface BrokerClientTopologyMetrics {
  Noop NOOP = new Noop();

  static BrokerClientTopologyMetrics of(final MeterRegistry meterRegistry) {
    return new BrokerClientTopologyMetricsImpl(meterRegistry);
  }

  void setRoleForPartition(
      final int partitionId, final int brokerId, final PartitionRoleValues roleValue);

  final class Noop implements BrokerClientTopologyMetrics {

    @Override
    public void setRoleForPartition(
        final int partitionId, final int brokerId, final PartitionRoleValues roleValue) {}
  }
}
