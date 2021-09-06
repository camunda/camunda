/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import java.util.Collection;
import java.util.List;

public final class BrokerContextImpl implements BrokerContext {

  private final BrokerHealthCheckService healthCheckService;
  private final List<PartitionListener> partitionListeners;

  public BrokerContextImpl(
      final BrokerHealthCheckService healthCheckService,
      final List<PartitionListener> partitionListeners) {
    this.healthCheckService = requireNonNull(healthCheckService);
    this.partitionListeners = unmodifiableList(requireNonNull(partitionListeners));
  }

  @Override
  public BrokerHealthCheckService getHealthCheckService() {
    return healthCheckService;
  }

  @Override
  public Collection<? extends PartitionListener> getPartitionListeners() {
    return partitionListeners;
  }
}
