/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker;

import io.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Helper class that allows Spring beans to access information from the Broker code that is not
 * managed by Spring
 */
@Component
public class SpringBrokerBridge {

  private Supplier<BrokerHealthCheckService> healthCheckServiceSupplier;

  public void registerBrokerHealthCheckServiceSupplier(
      Supplier<BrokerHealthCheckService> healthCheckServiceSupplier) {
    this.healthCheckServiceSupplier = healthCheckServiceSupplier;
  }

  public Optional<BrokerHealthCheckService> getBrokerHealthCheckService() {
    return Optional.ofNullable(healthCheckServiceSupplier).map(Supplier::get);
  }
}
