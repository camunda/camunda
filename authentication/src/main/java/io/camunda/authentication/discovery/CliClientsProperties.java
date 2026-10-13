/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.discovery;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Public OAuth clients that command-line tools use to log a user in through the browser, keyed by
 * the registration id of the OIDC provider they belong to ({@code oidc} for the flat {@code
 * camunda.security.authentication.oidc.*} block, otherwise the {@code providers.oidc.<id>} key).
 *
 * <p>These clients are announced unauthenticated by {@link CliAuthDiscoveryController}, so they
 * must be public clients: a configured secret aborts the start instead of being published.
 */
@NullMarked
@ConfigurationProperties(prefix = "camunda.security.authentication")
public record CliClientsProperties(Map<String, CliClient> cliClients) {

  @SuppressWarnings("ConstantValue") // Spring passes null for an unset property
  public CliClientsProperties {
    cliClients = cliClients == null ? Map.of() : Map.copyOf(cliClients);
  }

  @SuppressWarnings("ConstantValue") // Spring passes null for an unset property
  public record CliClient(String clientId, List<String> scopes, @Nullable String clientSecret) {

    public CliClient {
      if (clientId == null || clientId.isBlank()) {
        throw new IllegalStateException(
            "camunda.security.authentication.cli-clients.*.client-id must be set.");
      }
      if (clientSecret != null) {
        throw new IllegalStateException(
            "camunda.security.authentication.cli-clients.*.client-secret must not be set: CLI"
                + " clients are announced without authentication and must be public clients.");
      }
      scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }
  }
}
