/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.cluster;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.qa.util.actuator.BrokerHealthActuator;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;

public interface TestBroker<T extends TestBroker<T>> extends TestZeebe<T> {

  /** Returns true if this node has an embedded gateway. */
  boolean hasEmbeddedGateway();

  /**
   * Returns the health actuator for this broker. You can use this to check for liveness, readiness,
   * and startup.
   */
  default BrokerHealthActuator brokerHealth() {
    return BrokerHealthActuator.of("http://" + monitoringAddress());
  }

  @Override
  default HealthActuator healthActuator() {
    return brokerHealth();
  }

  /**
   * The configuration for this broker. This is a mutable object, but changes to it will not take
   * effect until the broker is started after.
   */
  BrokerCfg brokerConfig();
}
