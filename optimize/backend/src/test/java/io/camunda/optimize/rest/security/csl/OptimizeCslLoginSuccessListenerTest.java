/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static io.camunda.optimize.rest.security.csl.OptimizeCslLoginSuccessListener.ORIGINAL_USER_ID_CLAIM;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.security.UserIdMigrationService;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/**
 * Verifies the CCSaaS user-id migration hook on the CSL login-success event. The listener only
 * decides whether a migration is warranted; deduplicating repeat logins for an already-migrated old
 * id is {@link UserIdMigrationService}'s job and is covered by its own test.
 */
@ExtendWith(MockitoExtension.class)
class OptimizeCslLoginSuccessListenerTest {

  private static final String CURRENT_USER_ID = "auth0|new-identity";
  private static final String PREVIOUS_USER_ID = "auth0|old-identity";

  @Mock private UserIdMigrationService userIdMigrationService;
  @Mock private CamundaAuthenticationProvider camundaAuthenticationProvider;
  @Mock private CamundaAuthentication camundaAuthentication;

  @InjectMocks private OptimizeCslLoginSuccessListener listener;

  @BeforeEach
  void setUp() {
    lenient()
        .when(camundaAuthenticationProvider.getCamundaAuthentication())
        .thenReturn(camundaAuthentication);
    lenient().when(camundaAuthentication.authenticatedUsername()).thenReturn(CURRENT_USER_ID);
  }

  @Test
  void shouldMigrateOnFirstLoginAfterIdentityChange() {
    listener.onInteractiveAuthenticationSuccess(loginEventWith(PREVIOUS_USER_ID));

    verify(userIdMigrationService).migrateUserIdIfNeeded(CURRENT_USER_ID, PREVIOUS_USER_ID);
  }

  @Test
  void shouldDelegateEveryLoginSoTheMigrationServiceCanDeduplicate() {
    // Auth0 keeps sending the claim after a migration, so the listener keeps delegating and
    // UserIdMigrationService no-ops on an old id it has already handled.
    listener.onInteractiveAuthenticationSuccess(loginEventWith(PREVIOUS_USER_ID));
    listener.onInteractiveAuthenticationSuccess(loginEventWith(PREVIOUS_USER_ID));

    verify(userIdMigrationService, times(2))
        .migrateUserIdIfNeeded(CURRENT_USER_ID, PREVIOUS_USER_ID);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   ", CURRENT_USER_ID})
  void shouldNotMigrateWhenThereIsNoDifferentPreviousIdentity(final String originalUserId) {
    listener.onInteractiveAuthenticationSuccess(loginEventWith(originalUserId));

    verifyNoInteractions(userIdMigrationService);
  }

  @Test
  void shouldNotMigrateWhenCslResolvesNoAuthenticatedUsername() {
    // Fail safe: migrating onto a guessed id would reassign entity ownership to the wrong user.
    when(camundaAuthentication.authenticatedUsername()).thenReturn(null);

    listener.onInteractiveAuthenticationSuccess(loginEventWith(PREVIOUS_USER_ID));

    verifyNoInteractions(userIdMigrationService);
  }

  @Test
  void shouldNotPropagateWhenResolvingTheCslUserThrows() {
    // given — the delegating converter throws when no converter matches the authentication, which
    // must not surface as a failed login: the event fires before the success handler redirects
    when(camundaAuthenticationProvider.getCamundaAuthentication())
        .thenThrow(new IllegalStateException("no matching converter"));

    // when - then
    assertThatNoException()
        .isThrownBy(
            () -> listener.onInteractiveAuthenticationSuccess(loginEventWith(PREVIOUS_USER_ID)));
    verifyNoInteractions(userIdMigrationService);
  }

  @Test
  void shouldNotPropagateWhenTheMigrationItselfThrows() {
    doThrow(new IllegalStateException("boom"))
        .when(userIdMigrationService)
        .migrateUserIdIfNeeded(CURRENT_USER_ID, PREVIOUS_USER_ID);

    assertThatNoException()
        .isThrownBy(
            () -> listener.onInteractiveAuthenticationSuccess(loginEventWith(PREVIOUS_USER_ID)));
  }

  @Test
  void shouldIgnoreNonOidcAuthentication() {
    final Authentication authentication =
        new TestingAuthenticationToken(CURRENT_USER_ID, "credentials");

    listener.onInteractiveAuthenticationSuccess(
        new InteractiveAuthenticationSuccessEvent(authentication, getClass()));

    verify(userIdMigrationService, never()).migrateUserIdIfNeeded(anyString(), any());
  }

  private static InteractiveAuthenticationSuccessEvent loginEventWith(final String originalUserId) {
    final Map<String, Object> claims = new HashMap<>();
    claims.put("sub", CURRENT_USER_ID);
    if (originalUserId != null) {
      claims.put(ORIGINAL_USER_ID_CLAIM, originalUserId);
    }
    final OidcIdToken idToken =
        new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(300), claims);
    final OAuth2AuthenticationToken authentication =
        new OAuth2AuthenticationToken(
            new DefaultOidcUser(AuthorityUtils.createAuthorityList("ROLE_USER"), idToken),
            List.of(),
            "auth0");
    return new InteractiveAuthenticationSuccessEvent(
        authentication, OptimizeCslLoginSuccessListenerTest.class);
  }
}
