/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.optimize.service.security.UserIdMigrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * SPIKE (ADR-0036): login-success hook for the CSL OIDC webapp chain.
 *
 * <p>The legacy {@code CCSaaSSecurityConfigurerAdapter} ran SaaS user-id migration inside its
 * OAuth2 login success handler. Under CSL the success handler belongs to the library, so Optimize
 * reacts to Spring Security's {@link InteractiveAuthenticationSuccessEvent} instead (published by
 * the {@code OAuth2LoginAuthenticationFilter}). No CSL change is needed for this.
 *
 * <p>When the OIDC principal carries the SaaS {@code https://camunda.com/originalUserId} claim and
 * it differs from the current user id, it triggers {@link UserIdMigrationService} to rewrite stored
 * entity ownership. The service exists only in CCSaaS (it is
 * {@code @Conditional(CCSaaSCondition)}), so it is injected as an optional {@link ObjectProvider};
 * in CCSM the claim is absent and the service missing, making this a no-op there. The DEBUG line
 * confirms the event fires on every CSL login, regardless of deployment.
 */
@Component
@ConditionalOnProperty(name = "optimize.security.csl.enabled", havingValue = "true")
public final class OptimizeCslLoginSuccessListener {

  private static final String ORIGINAL_USER_ID_CLAIM = "https://camunda.com/originalUserId";
  private static final Logger LOG = LoggerFactory.getLogger(OptimizeCslLoginSuccessListener.class);

  private final ObjectProvider<UserIdMigrationService> userIdMigrationServiceProvider;

  public OptimizeCslLoginSuccessListener(
      final ObjectProvider<UserIdMigrationService> userIdMigrationServiceProvider) {
    this.userIdMigrationServiceProvider = userIdMigrationServiceProvider;
  }

  @EventListener
  public void onInteractiveAuthenticationSuccess(
      final InteractiveAuthenticationSuccessEvent event) {
    final Authentication authentication = event.getAuthentication();
    if (!(authentication instanceof final OAuth2AuthenticationToken oauthToken)) {
      return;
    }
    final String userId = authentication.getName();
    LOG.debug("CSL OIDC login success for principal [{}]", userId);

    if (!(oauthToken.getPrincipal() instanceof final OidcUser oidcUser)) {
      return;
    }
    final String originalUserId = oidcUser.getClaimAsString(ORIGINAL_USER_ID_CLAIM);
    if (originalUserId == null || originalUserId.isBlank() || originalUserId.equals(userId)) {
      return;
    }

    final UserIdMigrationService migrationService = userIdMigrationServiceProvider.getIfAvailable();
    if (migrationService == null) {
      LOG.warn(
          "OIDC token for [{}] carries an originalUserId but no UserIdMigrationService is available"
              + " (non-SaaS deployment?); skipping user-id migration",
          userId);
      return;
    }
    migrationService.migrateUserIdIfNeeded(userId, originalUserId);
  }
}
