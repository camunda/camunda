/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.optimize;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CredentialsProvider;
import io.camunda.zeebe.config.OptimizeProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Executors;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Wires up an {@link OptimizeReportEvaluator} with its OAuth credentials provider and API client.
 * Keeps the Optimize-specific construction (and its Keycloak/OAuth string details) out of the
 * caller.
 */
public final class OptimizeReportEvaluatorFactory {

  /** OAuth scope requested for the Optimize bearer token. */
  private static final String OAUTH_SCOPE = "openid";

  /** Keycloak token endpoint, relative to {@code <keycloakUrl>/auth/realms/<realm>}. */
  private static final String REALMS_PATH = "/auth/realms/";

  private static final String TOKEN_ENDPOINT_PATH = "/protocol/openid-connect/token";

  private OptimizeReportEvaluatorFactory() {}

  public static OptimizeReportEvaluator create(
      final OptimizeProperties props,
      final WebClient.Builder webClientBuilder,
      final ObjectMapper objectMapper,
      final MeterRegistry registry) {
    // applyEnvironmentOverrides(false): keep this provider from inheriting the Zeebe client's OAuth
    // env vars (orchestration creds). Token caching/refresh/retry are handled by the provider.
    final CredentialsProvider credentials =
        CredentialsProvider.newCredentialsProviderBuilder()
            .clientId(props.getClientId())
            .clientSecret(props.getClientSecret())
            .audience(props.getAudience())
            .scope(OAUTH_SCOPE)
            .authorizationServerUrl(tokenEndpointUrl(props))
            .applyEnvironmentOverrides(false)
            .build();
    final OptimizeApiClient apiClient =
        new OptimizeApiClient(props, webClientBuilder, objectMapper, credentials);
    return new OptimizeReportEvaluator(
        props, apiClient, Executors.newScheduledThreadPool(1), registry);
  }

  private static String tokenEndpointUrl(final OptimizeProperties props) {
    return props.getKeycloakUrl() + REALMS_PATH + props.getRealm() + TOKEN_ENDPOINT_PATH;
  }
}
