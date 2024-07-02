/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.actuator;

import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import feign.Retryer;
import feign.Target.HardCodedTarget;
import io.camunda.zeebe.qa.util.cluster.TestApplication;
import io.zeebe.containers.ZeebeNode;

public interface GetFlowControlActuator {
  static GetFlowControlActuator of(final ZeebeNode<?> node) {
    final var endpoint =
        String.format("http://%s/actuator/flowControl", node.getExternalMonitoringAddress());
    return of(endpoint);
  }

  static GetFlowControlActuator of(final TestApplication<?> node) {
    return of(node.actuatorUri("flowControl").toString());
  }

  static GetFlowControlActuator of(final String endpoint) {
    final var target = new HardCodedTarget<>(GetFlowControlActuator.class, endpoint);
    return Feign.builder().retryer(Retryer.NEVER_RETRY).target(target);
  }

  /**
   * Gets the flow control configuration.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("GET")
  @Headers({"Content-Type: application/json"})
  String getFlowControlConfiguration();
}
