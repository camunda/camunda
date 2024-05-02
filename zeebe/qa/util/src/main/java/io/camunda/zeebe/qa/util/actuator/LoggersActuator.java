/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.actuator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
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
import org.slf4j.event.Level;

/**
 * Java interface for any node's loggers actuator. To instantiate this interface, you can use {@link
 * Feign}; see {@link #of(String)} as an example.
 *
 * <p>You can use one of {@link #of(String)} or {@link #of(ZeebeNode)} to create a new client to use
 * for yourself.
 *
 * <p>Adding a new method is simple: simply define the input/output here as you normally would, and
 * make sure to add the correct JSON encoding headers (`Accept` for the response type,
 * `Content-Type` if there's a body to send).
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public interface LoggersActuator {

  /**
   * Returns a {@link LoggersActuator} instance using the given node as upstream.
   *
   * @param node the node to connect to
   * @return a new instance of {@link LoggersActuator}
   */
  static LoggersActuator of(final ZeebeNode<?> node) {
    final var endpoint =
        String.format("http://%s/actuator/loggers", node.getExternalMonitoringAddress());
    return of(endpoint);
  }

  /**
   * Returns a {@link LoggersActuator} instance using the given node as upstream.
   *
   * @param node the node to connect to
   * @return a new instance of {@link LoggersActuator}
   */
  static LoggersActuator of(final TestApplication<?> node) {
    return of(node.actuatorUri("loggers").toString());
  }

  /**
   * Returns a {@link LoggersActuator} instance using the given endpoint as upstream. The endpoint
   * is expected to be a complete absolute URL, e.g. "http://localhost:9600/actuator/loggers".
   *
   * @param endpoint the actuator URL to connect to
   * @return a new instance of {@link LoggersActuator}
   */
  @SuppressWarnings("JavadocLinkAsPlainText")
  static LoggersActuator of(final String endpoint) {
    final var target = new HardCodedTarget<>(LoggersActuator.class, endpoint);
    return Feign.builder()
        .encoder(new JacksonEncoder())
        .decoder(new JacksonDecoder())
        .retryer(Retryer.NEVER_RETRY)
        .target(target);
  }

  /**
   * A convenience method to set the level of a logger remotely via the Spring Boot actuator, using
   * SLF4J levels as input to avoid any errors (e.g. typos).
   *
   * @param id the logger ID, e.g. io.camunda.zeebe.gateway
   * @param level the level you wish to set
   */
  default void set(final String id, final Level level) {
    set(id, new LoggerInfo(level.toString(), null));
  }

  @RequestLine("POST /{id}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  void set(@Param final String id, final LoggerInfo level);

  @RequestLine("POST /{id}")
  @Headers("Accept: application/json")
  LoggerInfo get(@Param final String id);

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(Include.NON_EMPTY)
  record LoggerInfo(String configuredLevel, String effectiveLevel) {}
}
