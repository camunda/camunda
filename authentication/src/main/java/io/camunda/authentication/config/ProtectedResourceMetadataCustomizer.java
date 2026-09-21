/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.spring.oidc.LazyClientRegistrationRepository;
import io.camunda.security.spring.security.OidcResourceServerCustomizer;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Wires RFC 9728 protected-resource metadata into the OIDC resource-server DSL of both OIDC chains.
 *
 * <p>The issuer URIs come from the configured providers rather than from resolved client
 * registrations: reading a registration performs OIDC discovery, which would abort the application
 * context while the chain is built, and would run on every anonymous call to the metadata endpoint
 * once deferred to request time.
 */
public class ProtectedResourceMetadataCustomizer implements OidcResourceServerCustomizer {

  private final ClientRegistrationRepository clientRegistrationRepository;

  public ProtectedResourceMetadataCustomizer(
      final ClientRegistrationRepository clientRegistrationRepository) {
    this.clientRegistrationRepository = clientRegistrationRepository;
  }

  @Override
  public void customize(final OAuth2ResourceServerConfigurer<HttpSecurity> oauth2) {
    final List<String> issuerUris = issuerUris(clientRegistrationRepository);
    oauth2.protectedResourceMetadata(
        prmConfigurer ->
            prmConfigurer.protectedResourceMetadataCustomizer(
                prmBuilder -> issuerUris.forEach(prmBuilder::authorizationServer)));
  }

  private static List<String> issuerUris(final ClientRegistrationRepository repository) {
    if (repository instanceof final LazyClientRegistrationRepository lazy) {
      return lazy.providers().values().stream()
          .map(OidcConfiguration::getIssuerUri)
          .filter(Objects::nonNull)
          .distinct()
          .toList();
    }
    if (!(repository instanceof final Iterable<?> iterable)) {
      throw new IllegalStateException(
          "Unable to extract OAuth 2.0 client registrations as clientRegistrationRepository %s is not iterable"
              .formatted(repository.getClass()));
    }
    return StreamSupport.stream(iterable.spliterator(), false)
        .filter(ClientRegistration.class::isInstance)
        .map(ClientRegistration.class::cast)
        .map(cr -> cr.getProviderDetails().getIssuerUri())
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }
}
