/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.discovery;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static io.camunda.spring.utils.PhysicalTenantContext.PHYSICAL_TENANTS_PATH_SEGMENT;

import io.camunda.authentication.discovery.CliAuthDiscoveryResponse.Provider;
import io.camunda.authentication.discovery.CliClientsProperties.CliClient;
import io.camunda.authentication.pt.PhysicalTenantOidcProviders;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tells command-line clients how to authenticate against this cluster, so a tool like c8ctl needs
 * only the cluster URL: the authentication method, and for OIDC each provider's issuer together
 * with the public client the tool logs the user in with (authorization code + PKCE on a loopback
 * redirect).
 *
 * <p>Each provider also carries the audiences the cluster validates for its issuer, so a client can
 * recognize a token it would be rejected with, typically one minted for another API.
 *
 * <p>Physical tenants authenticate independently - each has its own providers, audiences and CLI
 * clients - so each has its own document, at the RFC 9728 style path suffix {@code
 * /.well-known/camunda-auth/physical-tenants/<id>}. The unsuffixed document describes the default
 * tenant, which also serves the unprefixed API. Tenants are not listed: a client names the one it
 * wants, so the endpoint does not disclose which tenants exist.
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

  private static final String TENANT_CLI_CLIENTS_PREFIX =
      "camunda.physical-tenants.%s.security.authentication.cli-clients";

  private final String method;
  private final Map<String, List<Provider>> providersByTenant;

  public CliAuthDiscoveryController(
      final CamundaSecurityLibraryProperties securityProperties,
      final ObjectProvider<OidcProviderConfigurationPort> oidcProviders,
      final CliClientsProperties cliClientsProperties,
      final Environment environment) {
    final var authentication = securityProperties.getAuthentication();
    if (authentication.isUnprotectedApi()) {
      method = "none";
    } else if (authentication.getMethod() == AuthenticationMethod.OIDC) {
      method = "oidc";
    } else {
      method = "basic";
    }
    // Resolved once, so an invalid tenant CLI client fails the start like a root one does.
    providersByTenant =
        "oidc".equals(method)
            ? providersByTenant(oidcProviders, cliClientsProperties, environment)
            : Map.of();
  }

  @GetMapping(path = PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public CliAuthDiscoveryResponse discover() {
    return response(DEFAULT_PHYSICAL_TENANT_ID, "");
  }

  @GetMapping(
      path = PATH + PHYSICAL_TENANTS_PATH_SEGMENT + "{tenantId}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public CliAuthDiscoveryResponse discover(@PathVariable final String tenantId) {
    if ("oidc".equals(method) && !providersByTenant.containsKey(tenantId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return response(tenantId, PHYSICAL_TENANTS_PATH_SEGMENT + tenantId);
  }

  private CliAuthDiscoveryResponse response(final String tenantId, final String apiBase) {
    return new CliAuthDiscoveryResponse(
        CliAuthDiscoveryResponse.VERSION,
        method,
        tenantId,
        apiBase,
        providersByTenant.getOrDefault(tenantId, List.of()));
  }

  /**
   * The default tenant's providers are the cluster's (with physical tenants configured, the cluster
   * configuration already is the default tenant's); every other tenant's are its own merged and
   * narrowed set - the same each tenant's security chain is built from.
   */
  private static Map<String, List<Provider>> providersByTenant(
      final ObjectProvider<OidcProviderConfigurationPort> oidcProviders,
      final CliClientsProperties cliClientsProperties,
      final Environment environment) {
    final Map<String, List<Provider>> result = new LinkedHashMap<>();
    final var clusterProviders = oidcProviders.getIfAvailable();
    result.put(
        DEFAULT_PHYSICAL_TENANT_ID,
        providers(
            clusterProviders == null
                ? Map.of()
                : clusterProviders.getOidcAuthenticationConfigurations(),
            cliClients(DEFAULT_PHYSICAL_TENANT_ID, cliClientsProperties, environment)));
    PhysicalTenantOidcProviders.providersByPhysicalTenant(environment)
        .forEach(
            (tenantId, configurations) ->
                result.put(
                    tenantId,
                    providers(
                        configurations, cliClients(tenantId, cliClientsProperties, environment))));
    return Map.copyOf(result);
  }

  /** The cluster's CLI clients, replaced per registration id by the tenant's own. */
  private static Map<String, CliClient> cliClients(
      final String tenantId,
      final CliClientsProperties cliClientsProperties,
      final Environment environment) {
    final Map<String, CliClient> cliClients =
        new LinkedHashMap<>(cliClientsProperties.cliClients());
    Binder.get(environment)
        .bind(
            TENANT_CLI_CLIENTS_PREFIX.formatted(tenantId),
            Bindable.mapOf(String.class, CliClient.class))
        .ifBound(cliClients::putAll);
    return cliClients;
  }

  /** Providers without a configured CLI client are left out: a CLI cannot log in through them. */
  private static List<Provider> providers(
      final Map<String, OidcConfiguration> configurations,
      final Map<String, CliClient> cliClients) {
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
    return List.copyOf(providers);
  }

  private static List<String> audiences(final OidcConfiguration configuration) {
    final var audiences = configuration.getAudiences();
    return audiences == null ? List.of() : audiences.stream().sorted().toList();
  }
}
