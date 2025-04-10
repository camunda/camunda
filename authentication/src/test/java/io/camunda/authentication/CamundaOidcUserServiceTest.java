/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.authentication.entity.AuthenticationContext;
import io.camunda.authentication.entity.CamundaOidcUser;
import io.camunda.authentication.entity.OAuthContext;
import io.camunda.search.entities.RoleEntity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class CamundaOidcUserServiceTest {
  private static final String REGISTRATION_ID = "test";
  private static final String TOKEN_VALUE = "{}";
  private static final Instant TOKEN_ISSUED_AT = Instant.now().truncatedTo(ChronoUnit.SECONDS);
  private static final Instant TOKEN_EXPIRES_AT = TOKEN_ISSUED_AT.plus(1, ChronoUnit.DAYS);

  private CamundaOidcUserService camundaOidcUserService;

  @Mock private CamundaOAuthPrincipalService camundaOAuthPrincipalService;

  @BeforeEach
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    camundaOidcUserService = new CamundaOidcUserService(camundaOAuthPrincipalService);
  }

  @Test
  public void loadUser() {
    // given
    final Map<String, Object> claims =
        Map.of(
            "sub", "test|foo@camunda.test",
            "email", "foo@camunda.test",
            "role", "R1",
            "group", "G1");

    final var roleR1 = new RoleEntity(8L, "Role R1");

    when(camundaOAuthPrincipalService.loadOAuthContext(claims))
        .thenReturn(
            new OAuthContext(
                Set.of(5L, 7L),
                Set.of("test-id", "test-id-2"),
                new AuthenticationContext(
                    null, List.of(roleR1), List.of("*"), new ArrayList<>(), new ArrayList<>())));

    // when
    final OidcUser oidcUser = camundaOidcUserService.loadUser(createOidcUserRequest(claims));

    // then
    assertThat(oidcUser).isNotNull();
    assertThat(oidcUser).isInstanceOf(CamundaOidcUser.class);
    final var camundaUser = (CamundaOidcUser) oidcUser;
    assertThat(camundaUser.getEmail()).isEqualTo("foo@camunda.test");
    assertThat(camundaUser.getMappingKeys()).isEqualTo(Set.of(5L, 7L));
    assertThat(camundaUser.getMappingIds()).isEqualTo(Set.of("test-id", "test-id-2"));
    final AuthenticationContext authenticationContext = camundaUser.getAuthenticationContext();
    assertThat(authenticationContext.roles()).containsAll(Set.of(roleR1));
    assertThat(authenticationContext.groups()).isEmpty();
    assertThat(authenticationContext.tenants()).isEmpty();
    assertThat(authenticationContext.authorizedApplications()).containsAll(Set.of("*"));
  }

  private static OidcUserRequest createOidcUserRequest(final Map<String, Object> claims) {
    return new OidcUserRequest(
        ClientRegistration.withRegistrationId(REGISTRATION_ID)
            .authorizationGrantType(AuthorizationGrantType.JWT_BEARER)
            .build(),
        new OAuth2AccessToken(
            TokenType.BEARER, TOKEN_VALUE, TOKEN_ISSUED_AT, TOKEN_EXPIRES_AT, Set.of()),
        new OidcIdToken(TOKEN_VALUE, TOKEN_ISSUED_AT, TOKEN_EXPIRES_AT, claims));
  }
}
