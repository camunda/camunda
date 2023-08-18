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

public interface ExportingActuator {
  static ExportingActuator of(final ZeebeNode<?> node) {
    return ofAddress(node.getExternalMonitoringAddress());
  }

  static ExportingActuator ofAddress(final String address) {
    return of("http://" + address + "/actuator/exporting");
  }

  static ExportingActuator of(final String endpoint) {
    final var target = new HardCodedTarget<>(ExportingActuator.class, endpoint);
    return Feign.builder()
        .encoder(new JacksonEncoder())
        .decoder(new JacksonDecoder())
        .retryer(Retryer.NEVER_RETRY)
        .target(target);
  }

  /**
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /pause")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  void pause();

  /**
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /resume")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  void resume();
}
