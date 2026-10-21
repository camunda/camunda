/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * The UserInfo endpoint of an issuer comes from the registration that owns the issuer, in the order
 * the access-token decoder routes by, also when another registration resolved to the same issuer,
 * as two issuer-uris that differ only by a trailing slash now can.
 */
class UserInfoUriByIssuerTest {

  private static final String ISSUER = "https://idp.example.com";
  private static final String USER_INFO_URI = "https://idp.example.com/userinfo";

  /** The owner disables UserInfo, so its tokens are not augmented from the other's endpoint. */
  @Test
  void shouldNotTakeTheEndpointOfAnotherRegistrationWhenTheOwnerHasUserInfoDisabled() {
    // given - the owner comes first and has no UserInfo endpoint
    final var registrations =
        List.of(
            registration("disabled", ISSUER, null), registration("enabled", ISSUER, USER_INFO_URI));

    // when / then
    assertThat(WebSecurityConfig.OidcConfiguration.userInfoUriByIssuer(repository(registrations)))
        .isEmpty();
  }

  @Test
  void shouldTakeTheOwnersEndpointWhenTheOwnerHasUserInfoEnabled() {
    // given - the owner comes first and has an endpoint
    final var registrations =
        List.of(
            registration("enabled", ISSUER, USER_INFO_URI), registration("disabled", ISSUER, null));

    // when / then
    assertThat(WebSecurityConfig.OidcConfiguration.userInfoUriByIssuer(repository(registrations)))
        .containsExactlyEntriesOf(Map.of(ISSUER, URI.create(USER_INFO_URI)));
  }

  @Test
  void shouldMapEachIssuerToItsOwnEndpoint() {
    // given
    final var registrations =
        List.of(
            registration("a", "https://a.example.com", "https://a.example.com/userinfo"),
            registration("b", "https://b.example.com", "https://b.example.com/userinfo"));

    // when / then
    assertThat(WebSecurityConfig.OidcConfiguration.userInfoUriByIssuer(repository(registrations)))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "https://a.example.com", URI.create("https://a.example.com/userinfo"),
                "https://b.example.com", URI.create("https://b.example.com/userinfo")));
  }

  /** In the given order, as the decoder iterates it; Spring's in-memory repository has none. */
  private static OrderedRepository repository(final List<ClientRegistration> registrations) {
    return new OrderedRepository(registrations);
  }

  private static ClientRegistration registration(
      final String registrationId, final String issuer, final String userInfoUri) {
    return ClientRegistration.withRegistrationId(registrationId)
        .clientId("client")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/sso-callback")
        .authorizationUri(issuer + "/auth")
        .tokenUri(issuer + "/token")
        .jwkSetUri(issuer + "/jwks")
        .userInfoUri(userInfoUri)
        .issuerUri(issuer)
        .build();
  }

  private record OrderedRepository(List<ClientRegistration> registrations)
      implements ClientRegistrationRepository, Iterable<ClientRegistration> {

    @Override
    public ClientRegistration findByRegistrationId(final String registrationId) {
      return registrations.stream()
          .filter(cr -> cr.getRegistrationId().equals(registrationId))
          .findFirst()
          .orElse(null);
    }

    @Override
    public Iterator<ClientRegistration> iterator() {
      return registrations.iterator();
    }
  }
}
