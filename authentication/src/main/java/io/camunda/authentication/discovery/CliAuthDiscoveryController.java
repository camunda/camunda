/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.discovery;

import io.camunda.authentication.discovery.CliAuthDiscoveryResponse.Provider;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells command-line clients how to authenticate against this cluster, so a tool like c8ctl needs
 * only the cluster URL: the authentication method, and for OIDC each provider's issuer together
 * with the public client the tool logs the user in with (authorization code + PKCE on a loopback
 * redirect).
 *
 * <p>Each provider also carries the audiences the cluster validates for its issuer, so a client can
 * recognize a token it would be rejected with, typically one minted for another API.
 *
 * <p>Served on {@code SecurityPaths.UNPROTECTED_PATHS}, a chain without any authentication filter,
 * because a client calls it before it holds a credential, and a stray one must not turn it into a
 * 401. Issuers are read from the configuration, never resolved, so the endpoint answers while an
 * identity provider is unreachable.
 */
@NullMarked
@RestController
public class CliAuthDiscoveryController {

  public static final String PATH = "/.well-known/camunda-auth";

  private final CamundaSecurityLibraryProperties securityProperties;
  private final ObjectProvider<OidcProviderConfigurationPort> oidcProviders;
  private final CliClientsProperties cliClientsProperties;

  public CliAuthDiscoveryController(
      final CamundaSecurityLibraryProperties securityProperties,
      final ObjectProvider<OidcProviderConfigurationPort> oidcProviders,
      final CliClientsProperties cliClientsProperties) {
    this.securityProperties = securityProperties;
    this.oidcProviders = oidcProviders;
    this.cliClientsProperties = cliClientsProperties;
  }

  @GetMapping(path = PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public CliAuthDiscoveryResponse discover() {
    final var authentication = securityProperties.getAuthentication();
    if (authentication.isUnprotectedApi()) {
      return response("none", List.of());
    }
    if (authentication.getMethod() != AuthenticationMethod.OIDC) {
      return response("basic", List.of());
    }
    final var providers = oidcProviders.getIfAvailable();
    return response(
        "oidc",
        providers == null ? List.of() : providers(providers.getOidcAuthenticationConfigurations()));
  }

  /** Providers without a configured CLI client are left out: a CLI cannot log in through them. */
  private List<Provider> providers(final Map<String, OidcConfiguration> configurations) {
    final var cliClients = cliClientsProperties.cliClients();
    final List<Provider> providers = new ArrayList<>();
    configurations.forEach(
        (registrationId, configuration) -> {
          final var cliClient = cliClients.get(registrationId);
          final var issuer = configuration.getIssuerUri();
          if (cliClient != null && issuer != null) {
            providers.add(
                new Provider(
                    registrationId,
                    issuer,
                    cliClient.clientId(),
                    cliClient.scopes(),
                    cliClient.redirectUri(),
                    audiences(configuration),
                    cliClient.resource()));
          }
        });
    return providers;
  }

  private static List<String> audiences(final OidcConfiguration configuration) {
    final var audiences = configuration.getAudiences();
    return audiences == null ? List.of() : audiences.stream().sorted().toList();
  }

  private static CliAuthDiscoveryResponse response(
      final String method, final List<Provider> providers) {
    return new CliAuthDiscoveryResponse(CliAuthDiscoveryResponse.VERSION, method, providers);
  }
}
