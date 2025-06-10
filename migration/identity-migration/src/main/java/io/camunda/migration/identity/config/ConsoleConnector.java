/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config;

import io.camunda.migration.identity.midentity.ConsoleClient;
import java.io.IOException;
import java.util.Map;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ConsoleConnector {

  @Bean
  public ConsoleClient consoleClient(
      final RestTemplateBuilder builder,
      final IdentityMigrationProperties identityMigrationProperties,
      final ConsoleTokenService consoleTokenService) {
    final var consoleProperties = identityMigrationProperties.getConsole();
    final var restTemplate =
        builder
            .rootUri(consoleProperties.getBaseUrl())
            .interceptors(new TokenInterceptor(consoleProperties, consoleTokenService))
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .build();
    return new ConsoleClient(identityMigrationProperties, restTemplate);
  }

  public static final class TokenInterceptor implements ClientHttpRequestInterceptor {

    final ConsoleProperties consoleProperties;
    final ConsoleTokenService consoleTokenService;

    public TokenInterceptor(
        final ConsoleProperties consoleProperties, final ConsoleTokenService consoleTokenService) {
      this.consoleProperties = consoleProperties;
      this.consoleTokenService = consoleTokenService;
    }

    @Override
    public ClientHttpResponse intercept(
        final HttpRequest request, final byte[] body, final ClientHttpRequestExecution execution)
        throws IOException {
      final var token = consoleTokenService.getAccessToken();
      final var headers = request.getHeaders();
      headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
      return execution.execute(request, body);
    }
  }

  @Service
  public static class ConsoleTokenService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final IdentityMigrationProperties managementIdentityProperties;

    public ConsoleTokenService(final IdentityMigrationProperties managementIdentityProperties) {
      this.managementIdentityProperties = managementIdentityProperties;
    }

    public String getAccessToken() {
      final var consoleProperties = managementIdentityProperties.getConsole();
      final var headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("client_id", consoleProperties.getClientId());
      form.add("client_secret", consoleProperties.getClientSecret());
      form.add("audience", consoleProperties.getAudience());
      form.add("grant_type", "client_credentials");

      final var entity = new HttpEntity<>(form, headers);
      final var response =
          restTemplate.exchange(
              consoleProperties.getIssuerBackendUrl(), HttpMethod.POST, entity, Map.class);

      return (String) response.getBody().get("access_token");
    }
  }
}
