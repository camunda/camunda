/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.cluster;

import io.camunda.zeebe.qa.util.actuator.BrokerHealthActuator;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;

public interface ZeebeBrokerNode<T extends ZeebeBrokerNode<T>> extends ZeebeNode<T> {

  /** Returns true if this node has an embedded gateway. */
  boolean hasEmbeddedGateway();

  /**
   * Returns the address to access the command API of this node, over which gateways can communicate
   * with the broker.
   *
   * @return the command address
   */
  default String commandAddress() {
    return address(ZeebePort.COMMAND);
  }

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

  /** Convenience method to return this instance as a generic broker */
  default ZeebeBrokerNode<T> asBroker() {
    return this;
  }
}
