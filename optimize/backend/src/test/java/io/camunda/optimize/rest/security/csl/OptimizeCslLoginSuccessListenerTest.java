/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.security.UserIdMigrationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

@ExtendWith(MockitoExtension.class)
class OptimizeCslLoginSuccessListenerTest {

  private static final String ORIGINAL_USER_ID_CLAIM = "https://camunda.com/originalUserId";

  @Mock private ObjectProvider<UserIdMigrationService> userIdMigrationServiceProvider;
  @Mock private UserIdMigrationService userIdMigrationService;
  @Mock private OidcUser oidcUser;

  private OptimizeCslLoginSuccessListener listener;

  @BeforeEach
  void setUp() {
    listener = new OptimizeCslLoginSuccessListener(userIdMigrationServiceProvider);
  }

  private InteractiveAuthenticationSuccessEvent oidcEvent(
      final String userId, final String originalUserId) {
    when(oidcUser.getName()).thenReturn(userId);
    when(oidcUser.getClaimAsString(ORIGINAL_USER_ID_CLAIM)).thenReturn(originalUserId);
    final OAuth2AuthenticationToken authentication =
        new OAuth2AuthenticationToken(
            oidcUser, List.of(new SimpleGrantedAuthority("ROLE_USER")), "oidc");
    return new InteractiveAuthenticationSuccessEvent(authentication, getClass());
  }

  @Test
  void migratesWhenOriginalUserIdDiffersAndServiceAvailable() {
    when(userIdMigrationServiceProvider.getIfAvailable()).thenReturn(userIdMigrationService);

    listener.onInteractiveAuthenticationSuccess(oidcEvent("user-new", "user-old"));

    verify(userIdMigrationService).migrateUserIdIfNeeded("user-new", "user-old");
  }

  @Test
  void doesNothingWhenOriginalUserIdClaimAbsent() {
    listener.onInteractiveAuthenticationSuccess(oidcEvent("user-new", null));

    verifyNoInteractions(userIdMigrationService);
  }

  @Test
  void doesNothingWhenOriginalUserIdEqualsCurrentUserId() {
    listener.onInteractiveAuthenticationSuccess(oidcEvent("user-new", "user-new"));

    verifyNoInteractions(userIdMigrationService);
  }

  @Test
  void doesNotThrowWhenMigrationServiceAbsent() {
    when(userIdMigrationServiceProvider.getIfAvailable()).thenReturn(null);

    listener.onInteractiveAuthenticationSuccess(oidcEvent("user-new", "user-old"));

    verifyNoInteractions(userIdMigrationService);
  }

  @Test
  void ignoresNonOauth2Authentication() {
    final UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            "user", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    final InteractiveAuthenticationSuccessEvent event =
        new InteractiveAuthenticationSuccessEvent(authentication, getClass());

    listener.onInteractiveAuthenticationSuccess(event);

    verifyNoInteractions(userIdMigrationServiceProvider, userIdMigrationService);
  }
}
