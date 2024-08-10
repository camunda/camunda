/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.actuator;

import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import feign.Feign;
import feign.RequestLine;
import feign.Retryer;
import feign.Target.HardCodedTarget;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.zeebe.containers.ZeebeNode;
import java.util.List;

/**
 * Java interface for the broker's health endpoints. To instantiate this interface, you can use
 * {@link Feign}; see {@link #of(String)} as an example.
 *
 * <p>You can use one of {@link #of(String)} or {@link #of(ZeebeNode)} to create a new client to use
 * for yourself.
 */
public interface BrokerHealthActuator extends HealthActuator {
  /**
   * Returns a {@link BrokerHealthActuator} instance using the given node as upstream.
   *
   * @param node the node to connect to
   * @return a new instance of {@link BrokerHealthActuator}
   */
  static BrokerHealthActuator of(final ZeebeNode<?> node) {
    final String address = node.getExternalMonitoringAddress();
    return of("http://" + address);
  }

  /**
   * Returns a {@link BrokerHealthActuator} instance using the given node as upstream.
   *
   * @param node the node to connect to
   * @return a new instance of {@link BrokerHealthActuator}
   */
  static BrokerHealthActuator of(final TestStandaloneBroker node) {
    return of(node.monitoringUri().toString());
  }

  /**
   * Returns a {@link BrokerHealthActuator} instance using the given endpoint as upstream. The
   * endpoint is expected to be a complete absolute URL, e.g. "http://localhost:9600/".
   *
   * @param endpoint the actuator URL to connect to
   * @return a new instance of {@link BrokerHealthActuator}
   */
  @SuppressWarnings("JavadocLinkAsPlainText")
  static BrokerHealthActuator of(final String endpoint) {
    final var target = new HardCodedTarget<>(BrokerHealthActuator.class, endpoint);
    final var decoder = new JacksonDecoder(List.of(new Jdk8Module(), new JavaTimeModule()));

    return Feign.builder()
        .encoder(new JacksonEncoder())
        .decoder(decoder)
        .retryer(Retryer.NEVER_RETRY)
        .target(target);
  }

  @Override
  @RequestLine("GET /ready")
  void ready();

  @Override
  @RequestLine("GET /startup")
  void startup();

  @Override
  @RequestLine("GET /health")
  void live();
}
