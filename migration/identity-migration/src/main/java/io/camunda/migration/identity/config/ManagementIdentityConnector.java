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
import java.nio.charset.StandardCharsets;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;

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
            .interceptors(new M2MTokenInterceptor(identity, properties.getAudience()))
            .errorHandler(new CustomErrorHandler())
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

  public static final class CustomErrorHandler extends DefaultResponseErrorHandler {

    @Override
    public boolean hasError(final ClientHttpResponse response) throws IOException {
      return super.hasError(response) && response.getStatusCode() != HttpStatus.NOT_FOUND;
    }

    @Override
    public void handleError(final ClientHttpResponse response) throws IOException {
      if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
        return;
      }
      super.handleError(response);
    }

    @Override
    public byte[] getResponseBody(final ClientHttpResponse response) {
      try {
        if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
          return "[]".getBytes(StandardCharsets.UTF_8);
        }
      } catch (final IOException ignored) {
        // ignored exception
      }
      return super.getResponseBody(response);
    }
  }
}
