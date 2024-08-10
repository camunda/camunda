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
import feign.Headers;
import feign.RequestLine;
import feign.Retryer;
import feign.Target.HardCodedTarget;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import io.camunda.zeebe.qa.util.cluster.TestApplication;
import io.camunda.zeebe.shared.management.JobStreamEndpoint.ClientJobStream;
import io.camunda.zeebe.shared.management.JobStreamEndpoint.JobStreams;
import io.camunda.zeebe.shared.management.JobStreamEndpoint.RemoteJobStream;
import io.zeebe.containers.ZeebeNode;
import java.util.List;

/**
 * Java interface for the node's job stream actuator. To instantiate this interface, you can use
 * {@link Feign}; see {@link #of(String)} as an example.
 *
 * <p>You can use one of {@link #of(String)} or {@link #of(ZeebeNode)} to create a new client to use
 * for yourself.
 */
public interface JobStreamActuator {
  /**
   * Returns a {@link JobStreamActuator} instance using the given node as upstream.
   *
   * @param node the node to connect to
   * @return a new instance of {@link JobStreamActuator}
   */
  static JobStreamActuator of(final ZeebeNode<?> node) {
    final var endpoint =
        String.format("http://%s/actuator/jobstreams", node.getExternalMonitoringAddress());
    return of(endpoint);
  }

  /**
   * Returns a {@link JobStreamActuator} instance using the given node as upstream.
   *
   * @param node the node to connect to
   * @return a new instance of {@link JobStreamActuator}
   */
  static JobStreamActuator of(final TestApplication<?> node) {
    return of(node.actuatorUri("jobstreams").toString());
  }

  /**
   * Returns a {@link JobStreamActuator} instance using the given endpoint as upstream. The endpoint
   * is expected to be a complete absolute URL, e.g. "http://localhost:9600/actuator/jobstreams".
   *
   * @param endpoint the actuator URL to connect to
   * @return a new instance of {@link JobStreamActuator}
   */
  @SuppressWarnings("JavadocLinkAsPlainText")
  static JobStreamActuator of(final String endpoint) {
    final var target = new HardCodedTarget<>(JobStreamActuator.class, endpoint);
    final var decoder = new JacksonDecoder(List.of(new Jdk8Module(), new JavaTimeModule()));

    return Feign.builder()
        .encoder(new JacksonEncoder())
        .decoder(decoder)
        .retryer(Retryer.NEVER_RETRY)
        .target(target);
  }

  @RequestLine("GET ")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  JobStreams list();

  @RequestLine("GET /client")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  List<ClientJobStream> listClient();

  @RequestLine("GET /remote")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  List<RemoteJobStream> listRemote();
}
