/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.discovery;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *
 * <p>{@code redirect-uri} must equal what is registered for the client at the identity provider,
 * since providers compare it exactly (only the port of a loopback URI is ignored, RFC 8252). It
 * must be an {@code http} loopback URI, because the CLI receives the browser's redirect on its own
 * machine; without a port the CLI picks a free one per login.
 *
 * <p>{@code resource} is the RFC 8707 resource indicator a CLI sends, for identity providers that
 * set the token audience from it (Auth0's {@code audience} works the same way); providers that
 * derive the audience from a scope, like Keycloak client scopes or Entra ID, need it in {@code
 * scopes} instead.
 */
@NullMarked
@ConfigurationProperties(prefix = "camunda.security.authentication")
public record CliClientsProperties(Map<String, CliClient> cliClients) {

  @SuppressWarnings("ConstantValue") // Spring passes null for an unset property
  public CliClientsProperties {
    cliClients = cliClients == null ? Map.of() : Map.copyOf(cliClients);
  }

  @SuppressWarnings("ConstantValue") // Spring passes null for an unset property
  public record CliClient(
      String clientId,
      List<String> scopes,
      String redirectUri,
      @Nullable String resource,
      @Nullable String clientSecret) {

    public static final String DEFAULT_REDIRECT_URI = "http://127.0.0.1/callback";
    private static final Set<String> LOOPBACK_HOSTS = Set.of("127.0.0.1", "[::1]", "localhost");

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
      redirectUri = redirectUri == null ? DEFAULT_REDIRECT_URI : validateRedirectUri(redirectUri);
    }

    private static String validateRedirectUri(final String redirectUri) {
      final URI uri;
      try {
        uri = new URI(redirectUri);
      } catch (final URISyntaxException e) {
        throw invalidRedirectUri(redirectUri, "it is not a valid URI");
      }
      if (!"http".equals(uri.getScheme())
          || uri.getHost() == null
          || !LOOPBACK_HOSTS.contains(uri.getHost().toLowerCase())) {
        throw invalidRedirectUri(redirectUri, "it must be http on 127.0.0.1, [::1] or localhost");
      }
      if (uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getUserInfo() != null) {
        throw invalidRedirectUri(redirectUri, "it must not carry user info, a query or a fragment");
      }
      return redirectUri;
    }

    private static IllegalStateException invalidRedirectUri(
        final String redirectUri, final String reason) {
      return new IllegalStateException(
          "camunda.security.authentication.cli-clients.*.redirect-uri '%s' is invalid: %s."
              .formatted(redirectUri, reason));
    }
  }
}
