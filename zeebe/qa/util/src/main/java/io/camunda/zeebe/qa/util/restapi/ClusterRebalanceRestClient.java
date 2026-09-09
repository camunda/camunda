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
import feign.codec.Decoder;
import feign.jackson.JacksonDecoder;
import io.camunda.gateway.protocol.model.ClusterBalanceResponse;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import java.lang.reflect.ParameterizedType;
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
    return Feign.builder()
        .decoder(typedResponseDecoder())
        .retryer(Retryer.NEVER_RETRY)
        .target(target);
  }

  /** Triggers a rebalance of the cluster's leadership. */
  @RequestLine("POST")
  @Headers("Accept: application/json")
  TypedResponse<ClusterBalanceResponse> triggerRebalance();

  /** Reports the plan a rebalance would carry out, without transferring any leadership. */
  @RequestLine("POST ?dryRun=true")
  @Headers("Accept: application/json")
  TypedResponse<ClusterBalanceResponse> triggerDryRun();

  /** Returns the status of the cluster's rebalance. */
  @RequestLine("GET")
  @Headers("Accept: application/json")
  TypedResponse<ClusterBalanceResponse> getRebalance();

  private static Decoder typedResponseDecoder() {
    final Decoder bodyDecoder = new JacksonDecoder();
    return (response, type) -> {
      if (!(type instanceof final ParameterizedType parameterizedType)
          || parameterizedType.getRawType() != TypedResponse.class) {
        return bodyDecoder.decode(response, type);
      }

      final var bodyType = parameterizedType.getActualTypeArguments()[0];
      final var body = response.body() == null ? null : bodyDecoder.decode(response, bodyType);
      return new TypedResponse<>(response.status(), body);
    };
  }

  record TypedResponse<T>(int status, T body) {}
}
