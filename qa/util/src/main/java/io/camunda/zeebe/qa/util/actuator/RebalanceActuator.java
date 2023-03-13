/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.actuator;

import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import feign.Retryer;
import feign.Target.HardCodedTarget;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import io.zeebe.containers.ZeebeNode;

/**
 * Java interface for the node's rebalance actuator. To instantiate this interface, you can use
 * {@link Feign}; see {@link #of(String)} as an example.
 *
 * <p>You can use one of {@link #of(String)} or {@link #of(ZeebeNode)} to create a new client to use
 * for yourself.
 *
 * <p>Adding a new method is simple: simply define the input/output here as you normally would, and
 * make sure to add the correct JSON encoding headers (`Accept` for the response type,
 * `Content-Type` if there's a body to send). See {@link LoggersActuator} for a more complete
 * example.
 */
public interface RebalanceActuator {

  /**
   * Returns a {@link RebalanceActuator} instance using the given node as upstream.
   *
   * @param node the node to connect to
   * @return a new instance of {@link RebalanceActuator}
   */
  static RebalanceActuator of(final ZeebeNode<?> node) {
    final var endpoint =
        String.format("http://%s/actuator/rebalance", node.getExternalMonitoringAddress());
    return of(endpoint);
  }

  /**
   * Returns a {@link RebalanceActuator} instance using the given endpoint as upstream. The endpoint
   * is expected to be a complete absolute URL, e.g. "http://localhost:9600/actuator/partitions".
   *
   * @param endpoint the actuator URL to connect to
   * @return a new instance of {@link RebalanceActuator}
   */
  @SuppressWarnings("JavadocLinkAsPlainText")
  static RebalanceActuator of(final String endpoint) {
    final var target = new HardCodedTarget<>(RebalanceActuator.class, endpoint);
    return Feign.builder()
        .encoder(new JacksonEncoder())
        .decoder(new JacksonDecoder())
        .retryer(Retryer.NEVER_RETRY)
        .target(target);
  }

  /**
   * Triggers rebalancing on of the cluster leadership.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  void rebalance();
}
