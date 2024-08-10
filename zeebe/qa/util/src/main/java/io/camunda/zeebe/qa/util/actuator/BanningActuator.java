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
import feign.Param;
import feign.RequestLine;
import feign.Retryer;
import feign.Target.HardCodedTarget;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import io.camunda.zeebe.qa.util.cluster.TestApplication;
import io.zeebe.containers.ZeebeNode;

public interface BanningActuator {
  static BanningActuator of(final ZeebeNode<?> node) {
    final var endpoint =
        String.format("http://%s/actuator/banning", node.getExternalMonitoringAddress());
    return of(endpoint);
  }

  static BanningActuator of(final TestApplication<?> node) {
    return of(node.actuatorUri("banning").toString());
  }

  static BanningActuator of(final String endpoint) {
    final var target = new HardCodedTarget<>(BanningActuator.class, endpoint);
    return Feign.builder()
        .encoder(new JacksonEncoder())
        .decoder(new JacksonDecoder())
        .retryer(Retryer.NEVER_RETRY)
        .target(target);
  }

  /**
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /{id}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  void ban(@Param final long id);
}
