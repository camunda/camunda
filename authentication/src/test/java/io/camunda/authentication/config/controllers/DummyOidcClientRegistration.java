/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.controllers;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * A single-registration {@link ClientRegistrationRepository} that satisfies the OIDC filter chain's
 * dependencies without a real IdP, for slice tests booted under {@code method=oidc}.
 */
public final class DummyOidcClientRegistration {

  private DummyOidcClientRegistration() {}

  public static ClientRegistrationRepository repository() {
    final var dummyRegistration =
        ClientRegistration.withRegistrationId("test")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .clientId("test-client")
            .redirectUri("{baseUrl}/sso-callback")
            .authorizationUri("https://example.com/authorize")
            .tokenUri("https://example.com/token")
            .issuerUri("https://example.com")
            .build();
    return new InMemoryClientRegistrationRepository(dummyRegistration);
  }
}
