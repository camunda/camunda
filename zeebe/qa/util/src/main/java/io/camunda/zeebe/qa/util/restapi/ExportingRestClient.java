/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.restapi;

import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import feign.Retryer;
import feign.Target.HardCodedTarget;
import feign.auth.BasicAuthRequestInterceptor;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import io.camunda.zeebe.qa.util.cluster.TestGateway;

/**
 * Wraps the v2 REST API's exporting endpoints: the cluster-wide ones ({@code
 * /cluster/v2/exporting/*}), served by the cluster-admin security chain and thus requiring its
 * Basic Auth credentials, and, given a physical tenant id, their per-tenant counterparts ({@code
 * /physical-tenants/{physicalTenantId}/v2/exporting/*}).
 */
public interface ExportingRestClient {
  static ExportingRestClient of(
      final TestGateway<?> node, final String clusterAdminUser, final String clusterAdminPassword) {
    final var endpoint = node.restAddress().resolve("/cluster/v2/exporting").toString();
    return of(endpoint, new BasicAuthRequestInterceptor(clusterAdminUser, clusterAdminPassword));
  }

  static ExportingRestClient of(final TestGateway<?> node, final String physicalTenantId) {
    final var path = "/physical-tenants/%s/v2/exporting".formatted(physicalTenantId);
    return of(node.restAddress().resolve(path).toString(), request -> {});
  }

  private static ExportingRestClient of(
      final String endpoint, final feign.RequestInterceptor requestInterceptor) {
    final var target = new HardCodedTarget<>(ExportingRestClient.class, endpoint);
    return Feign.builder()
        .encoder(new JacksonEncoder())
        .decoder(new JacksonDecoder())
        .requestInterceptor(requestInterceptor)
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
  @RequestLine("POST /pause?soft=true")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  void softPause();

  /**
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST /resume")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  void resume();
}
