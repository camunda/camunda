/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.entity.CamundaOidcUser;
import io.camunda.authentication.service.OidcCamundaUserService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

public class OidcCamundaUserServiceTest {
  private static final String TOKEN_VALUE =
      "{\"access_token\":\"test-access-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}";
  private static final Instant TOKEN_ISSUED_AT = Instant.now().truncatedTo(ChronoUnit.SECONDS);
  private static final Instant TOKEN_EXPIRES_AT = TOKEN_ISSUED_AT.plus(1, ChronoUnit.DAYS);

  private Authentication mockAuthentication;
  private CamundaOidcUser mockCamundaOidcUser;
  private SecurityContext mockSecurityContext;
  private OidcCamundaUserService oidcCamundaUserService;

  @BeforeEach
  public void setUp() throws Exception {
    oidcCamundaUserService = new OidcCamundaUserService();
  }

  @Test
  public void givenCamundaOidcUserWhenGetUserTokenThenTokenIsCalled() {
    final var principal =
        new CamundaOidcUser(
            new DefaultOidcUser(
                Collections.emptyList(),
                new OidcIdToken(
                    TOKEN_VALUE,
                    Instant.now(),
                    Instant.now().plusSeconds(3600),
                    Map.of("sub", "not-tested"))),
            Collections.emptySet(),
            null);

    final var auth = new OAuth2AuthenticationToken(principal, List.of(), "oidc");
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertThat(oidcCamundaUserService.getUserToken()).isEqualTo(TOKEN_VALUE);
  }
}
