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
import io.camunda.zeebe.qa.util.cluster.TestApplication;

/**
 * Java interface for any node's Prometheus scrape endpoint. To instantiate this interface, you can
 * use {@link Feign}; see {@link #of(String)} as an example.
 *
 * <p>The endpoint returns the raw Prometheus text exposition format, so the response is returned
 * as-is without any decoding.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public interface PrometheusActuator {

  /**
   * Returns a {@link PrometheusActuator} instance using the given node as upstream.
   *
   * @param node the node to connect to
   * @return a new instance of {@link PrometheusActuator}
   */
  static PrometheusActuator of(final TestApplication<?> node) {
    return of(String.format("http://%s/actuator/prometheus", node.monitoringAddress()));
  }

  /**
   * Returns a {@link PrometheusActuator} instance using the given endpoint as upstream. The
   * endpoint is expected to be a complete absolute URL, e.g.
   * "http://localhost:9600/actuator/prometheus".
   *
   * @param endpoint the actuator URL to connect to
   * @return a new instance of {@link PrometheusActuator}
   */
  @SuppressWarnings("JavadocLinkAsPlainText")
  static PrometheusActuator of(final String endpoint) {
    final var target = new HardCodedTarget<>(PrometheusActuator.class, endpoint);
    return Feign.builder().retryer(Retryer.NEVER_RETRY).target(target);
  }

  /** Returns the raw Prometheus text exposition of all registered metrics. */
  @RequestLine("GET")
  @Headers("Accept: text/plain")
  String metrics();
}
