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
import feign.Response;
import feign.Retryer;
import feign.Target.HardCodedTarget;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import java.net.URI;

/**
 * Java interface for the cluster's {@code cluster/v2/rebalance} REST endpoint. To instantiate this
 * interface, you can use {@link Feign}; see {@link #of(TestGateway)} as an example.
 */
public interface ClusterRebalanceRestClient {
  static ClusterRebalanceRestClient of(final TestGateway<?> gateway) {
    return of(gateway.restAddress());
  }

  static ClusterRebalanceRestClient of(final URI restAddress) {
    final var path = restAddress.getPath();
    final var baseWithTrailingSlash =
        path.endsWith("/") ? restAddress : URI.create(restAddress + "/");
    final var endpoint = baseWithTrailingSlash.resolve("cluster/v2/rebalance").toString();
    final var target = new HardCodedTarget<>(ClusterRebalanceRestClient.class, endpoint);
    return Feign.builder().retryer(Retryer.NEVER_RETRY).target(target);
  }

  /** Triggers a rebalance of the cluster's leadership. */
  @RequestLine("POST")
  @Headers("Accept: application/json")
  Response triggerRebalance();

  /** Returns the status of the cluster's rebalance. */
  @RequestLine("GET")
  @Headers("Accept: application/json")
  Response getRebalance();
}
