/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

class OidcClientRegistrationTest {

  @Test
  public void validAuthenticationMethodIsSet() {
    final var config = new OidcAuthenticationConfiguration();
    config.setClientId("clientId");
    config.setRedirectUri("redirectUri");
    config.setAuthorizationUri("authorizationUri");
    config.setTokenUri("tokenUri");
    config.setClientAuthenticationMethod("client_secret_basic");

    Assertions.assertThat(ClientRegistrationFactory.createClientRegistration("foo", config))
        .isInstanceOf(ClientRegistration.class);
  }

  @Test
  public void invalidAuthenticationMethodThrows() {
    final var config = new OidcAuthenticationConfiguration();
    config.setClientId("clientId");
    config.setRedirectUri("redirectUri");
    config.setAuthorizationUri("authorizationUri");
    config.setTokenUri("tokenUri");
    config.setClientAuthenticationMethod("does_not_exist");

    Assertions.assertThatThrownBy(
            () -> ClientRegistrationFactory.createClientRegistration("foo", config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported client authentication method: does_not_exist");
  }
}
