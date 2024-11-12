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
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;

@SuppressWarnings({"unused", "UnusedReturnValue"})
public interface PrometheusActuator {

  static PrometheusActuator of(final TestStandaloneBroker node) {
    return of(node.actuatorUri("prometheus").toString());
  }

  static PrometheusActuator of(final String endpoint) {
    final var target = new HardCodedTarget<>(PrometheusActuator.class, endpoint);
    return Feign.builder().retryer(Retryer.NEVER_RETRY).target(target);
  }

  @RequestLine("GET")
  @Headers("Accept: text/plain")
  String metrics();
}
