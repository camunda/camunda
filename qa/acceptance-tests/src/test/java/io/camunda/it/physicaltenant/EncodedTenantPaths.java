/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

/**
 * The two spellings of one physical tenant, and a request that reaches the server with the spelling
 * intact, shared by {@link PhysicalTenantEncodedPathRoutingIT} and {@link
 * PhysicalTenantEncodedPathOidcRoutingIT}.
 *
 * <p>The pair of spellings belongs in one place: they only mean anything relative to each other,
 * and a change to one that missed the other would leave a test that still passes while no longer
 * testing an encoded path at all.
 */
final class EncodedTenantPaths {

  /** A configured physical tenant. */
  static final String TENANT_ID = "tenanta";

  /** {@link #TENANT_ID} with its last character written as {@code %61}. */
  static final String ENCODED_TENANT_ID = "tenant%61";

  /** An endpoint that resolves the caller's memberships, so the stamped tenant is acted on. */
  static final String ME_ENDPOINT = "/v2/authentication/me";

  private EncodedTenantPaths() {}

  /** The tenant-prefixed path to {@link #ME_ENDPOINT}, with {@code tenantId} spelled verbatim. */
  static String meEndpointFor(final String tenantId) {
    return "/physical-tenants/" + tenantId + ME_ENDPOINT;
  }

  /**
   * Sends {@code rawPath} to {@code baseAddress} without touching its encoding. A {@code
   * CamundaClient} builds the tenant prefix itself and would normalize away the very thing under
   * test, so these tests go direct.
   *
   * @param authorization the value of the {@code Authorization} header, which is where the two
   *     callers differ
   */
  static HttpResponse<String> get(
      final String baseAddress, final String rawPath, final String authorization) throws Exception {
    final var base = baseAddress.replaceAll("/+$", "");
    try (final var client = HttpClient.newHttpClient()) {
      return client.send(
          HttpRequest.newBuilder(URI.create(base + rawPath))
              .header("Authorization", authorization)
              .GET()
              .build(),
          BodyHandlers.ofString());
    }
  }
}
