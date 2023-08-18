/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.cluster;

import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.qa.util.actuator.GatewayHealthActuator;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;

public interface ZeebeGateway<T extends ZeebeGateway<T>> extends Zeebe<T> {

  /**
   * Returns the address used by clients to interact with the gateway.
   *
   * <p>You can build your client like this:
   *
   * <pre>@{code
   *   ZeebeClient.newClientBuilder()
   *     .gatewayAddress(gateway.gatewayAddress())
   *     .usePlaintext()
   *     .build();
   * }</pre>
   *
   * @return the gateway address
   */
  default String gatewayAddress() {
    return address(ZeebePort.GATEWAY);
  }

  /**
   * Returns the health actuator for this gateway. You can use this to check for liveness,
   * readiness, and startup.
   */
  default GatewayHealthActuator gatewayHealth() {
    return GatewayHealthActuator.of("http://" + monitoringAddress());
  }

  @Override
  default HealthActuator healthActuator() {
    return gatewayHealth();
  }

  /** Returns a new pre-configured client builder for this gateway */
  ZeebeClientBuilder newClientBuilder();
}
