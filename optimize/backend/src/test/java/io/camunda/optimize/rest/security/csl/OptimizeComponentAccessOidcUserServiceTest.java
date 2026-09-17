/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

@ExtendWith(MockitoExtension.class)
class OptimizeComponentAccessOidcUserServiceTest {

  @Mock private OptimizeComponentAccessPolicy policy;

  @Test
  void shouldCompleteLoginWhenThePolicyAllows() {
    // given
    when(policy.loginDenialReason(eq("access-token"), any())).thenReturn(Optional.empty());

    // when
    final var user = service().loadUser(userRequest());

    // then
    assertThat(user.getSubject()).isEqualTo("kermit");
  }

  @Test
  void shouldFailLoginWhenThePolicyDenies() {
    // A failed login ends in a terminal error. Letting the login complete would only send the user
    // back to the identity provider, which signs them in again.
    // given
    when(policy.loginDenialReason(eq("access-token"), any()))
        .thenReturn(Optional.of("User is not authorized to access Optimize"));

    // when, then
    assertThatThrownBy(() -> service().loadUser(userRequest()))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .hasMessageContaining("User is not authorized to access Optimize");
  }

  private OptimizeComponentAccessOidcUserService service() {
    final OptimizeComponentAccessOidcUserService service =
        new OptimizeComponentAccessOidcUserService(policy);
    // The user info endpoint is out of scope here, the id token claims are enough to decide.
    service.setRetrieveUserInfo(request -> false);
    return service;
  }

  private static OidcUserRequest userRequest() {
    final Instant now = Instant.now();
    return new OidcUserRequest(
        ClientRegistration.withRegistrationId("oidc")
            .clientId("optimize")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/api/authentication/callback")
            .authorizationUri("http://idp/authorize")
            .tokenUri("http://idp/token")
            .userNameAttributeName("sub")
            .build(),
        new OAuth2AccessToken(TokenType.BEARER, "access-token", now, now.plusSeconds(300)),
        new OidcIdToken(
            "id-token", now, now.plusSeconds(300), Map.of("sub", "kermit", "iss", "http://idp")));
  }
}
