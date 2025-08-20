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
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import feign.Feign;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.Retryer;
import feign.Target.HardCodedTarget;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import io.camunda.zeebe.management.cluster.ExporterStatus;
import io.camunda.zeebe.management.cluster.PlannedOperationsResponse;
import io.camunda.zeebe.qa.util.cluster.TestApplication;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.zeebe.containers.ZeebeNode;
import java.util.List;

public interface ExportersActuator {

  /**
   * Returns a {@link ExportersActuator} instance using the given node as upstream.
   *
   * @param node the node to connect to
   * @return a new instance of {@link ExportersActuator}
   */
  static ExportersActuator of(final ZeebeNode<?> node) {
    return ofAddress(node.getExternalMonitoringAddress());
  }

  /**
   * Returns a {@link ExportersActuator} instance using the given node as upstream.
   *
   * @param node the node to connect to
   * @return a new instance of {@link ExportersActuator}
   */
  static ExportersActuator of(final TestApplication<?> node) {
    return of(node.actuatorUri("exporters").toString());
  }

  /**
   * Returns a {@link ExportersActuator} instance using the given address as upstream.
   *
   * @param address the monitoring address
   * @return a new instance of {@link ExportersActuator}
   */
  static ExportersActuator ofAddress(final String address) {
    final var endpoint = String.format("http://%s/actuator/exporters", address);
    return of(endpoint);
  }

  /**
   * Returns a {@link ExportersActuator} instance using the given endpoint as upstream.
   *
   * @param endpoint the endpoint to connect to
   * @return a new instance of {@link ExportersActuator}
   */
  static ExportersActuator of(final String endpoint) {
    final var target = new HardCodedTarget<>(ExportersActuator.class, endpoint);
    return Feign.builder()
        .encoder(new JacksonEncoder(List.of(new Jdk8Module(), new JavaTimeModule())))
        .decoder(new JacksonDecoder(List.of(new Jdk8Module(), new JavaTimeModule())))
        .retryer(Retryer.NEVER_RETRY)
        .target(target);
  }

  /**
   * Request to disable an exporter
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /{exporterId}/disable")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse disableExporter(@Param final String exporterId);

  /**
   * Request to enable an exporter
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /{exporterId}/enable")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse enableExporter(
      @Param final String exporterId, @RequestBody final InitializationInfo initializeFrom);

  default PlannedOperationsResponse enableExporter(
      final String exporterId, final String initializeFrom) {
    return enableExporter(exporterId, new InitializationInfo(initializeFrom));
  }

  @RequestLine("POST /{exporterId}/enable")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse enableExporter(@Param final String exporterId);

  /**
   * Request to delete an exporter
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("DELETE /{exporterId}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  PlannedOperationsResponse deleteExporter(@Param final String exporterId);

  /**
   * Returns the list of exporters with their status
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("GET")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  List<ExporterStatus> getExporters();

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(Include.NON_EMPTY)
  record InitializationInfo(String initializeFrom) {}
}
