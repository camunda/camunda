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
import feign.Response;
import feign.Retryer;
import feign.Target.HardCodedTarget;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.prometheus.metrics.exporter.common.PrometheusHttpResponse;

@SuppressWarnings({"unused", "UnusedReturnValue"})
public interface PrometheusActuator {

  static PrometheusActuator of(final TestStandaloneBroker node) {
    return of(node.actuatorUri("prometheus").toString());
  }

  static io.camunda.zeebe.qa.util.actuator.PrometheusActuator of(final String endpoint) {
    final var target =
        new HardCodedTarget<>(io.camunda.zeebe.qa.util.actuator.PrometheusActuator.class, endpoint);
    return Feign.builder()
        .encoder(new JacksonEncoder())
        .decoder(new JacksonDecoder())
        .retryer(Retryer.NEVER_RETRY)
        .target(target);
  }

  @RequestLine("GET")
  @Headers("Accept: text/plain")
  PrometheusHttpResponse metrics();
}
