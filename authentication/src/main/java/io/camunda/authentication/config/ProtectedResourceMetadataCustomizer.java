/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

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
 * Lifted verbatim from {@code WebSecurityConfig#oauthProtectedResourceMetadataCustomizer}.
 */
public class ProtectedResourceMetadataCustomizer implements OidcResourceServerCustomizer {

  private final ClientRegistrationRepository clientRegistrationRepository;

  public ProtectedResourceMetadataCustomizer(
      final ClientRegistrationRepository clientRegistrationRepository) {
    this.clientRegistrationRepository = clientRegistrationRepository;
  }

  @Override
  public void customize(final OAuth2ResourceServerConfigurer<HttpSecurity> oauth2) {
    final var issuerUris =
        extractClientRegistrations(clientRegistrationRepository).stream()
            .map(cr -> cr.getProviderDetails().getIssuerUri())
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    oauth2.protectedResourceMetadata(
        prmConfigurer ->
            prmConfigurer.protectedResourceMetadataCustomizer(
                prmBuilder -> issuerUris.forEach(prmBuilder::authorizationServer)));
  }

  private static List<ClientRegistration> extractClientRegistrations(
      final ClientRegistrationRepository repository) {
    if (!(repository instanceof final Iterable<?> iterable)) {
      throw new IllegalStateException(
          "Unable to extract OAuth 2.0 client registrations as clientRegistrationRepository %s is not iterable"
              .formatted(repository.getClass()));
    }
    return StreamSupport.stream(iterable.spliterator(), false)
        .filter(ClientRegistration.class::isInstance)
        .map(ClientRegistration.class::cast)
        .toList();
  }
}
