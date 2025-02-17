/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import java.io.IOException;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

@Configuration
public class ManagementIdentityConnector {

  @Bean
  public Identity identity(final IdentityMigrationProperties identityMigrationProperties) {
    final var properties = identityMigrationProperties.getManagementIdentity();
    final var configuration =
        new IdentityConfiguration(
            properties.getBaseUrl(),
            properties.getIssuerBackendUrl(),
            properties.getIssuerBackendUrl(),
            properties.getClientId(),
            properties.getClientSecret(),
            properties.getAudience(),
            properties.getIssuerType());
    return new Identity(configuration);
  }

  @Bean
  public ManagementIdentityClient managementIdentityClient(
      final RestTemplateBuilder builder,
      final IdentityMigrationProperties identityMigrationProperties,
      final Identity identity) {
    final var properties = identityMigrationProperties.getManagementIdentity();
    final var restTemplate =
        builder
            .rootUri(properties.getBaseUrl())
            .interceptors(
                new M2MTokenInterceptor(identity, properties.getAudience()),
                new EndpointUnavailableInterceptor())
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .build();
    return new ManagementIdentityClient(
        restTemplate, identityMigrationProperties.getOrganizationId());
  }

  public static final class M2MTokenInterceptor implements ClientHttpRequestInterceptor {

    final Identity identity;
    final String audience;

    public M2MTokenInterceptor(final Identity identity, final String audience) {
      this.identity = identity;
      this.audience = audience;
    }

    @Override
    public ClientHttpResponse intercept(
        final HttpRequest request, final byte[] body, final ClientHttpRequestExecution execution)
        throws IOException {
      final String token = identity.authentication().requestToken(audience).getAccessToken();
      final HttpHeaders headers = request.getHeaders();
      headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
      return execution.execute(request, body);
    }
  }

  public class EndpointUnavailableInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
        final HttpRequest request, final byte[] body, final ClientHttpRequestExecution execution)
        throws IOException {
      final var result = execution.execute(request, body);
      if (result.getStatusCode() == HttpStatus.NOT_FOUND) {
        throw new NotImplementedException(
            "Endpoint is not implemented", request.getURI().toString());
      }
      return result;
    }
  }
}
