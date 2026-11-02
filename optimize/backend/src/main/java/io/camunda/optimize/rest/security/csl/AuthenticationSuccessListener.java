/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static io.camunda.optimize.service.db.repository.UserIdMigrationRepository.maskForLog;
import static io.camunda.optimize.service.db.repository.UserIdMigrationRepository.sanitize;

import io.camunda.optimize.service.security.UserIdMigrationService;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Runs the CCSaaS user-id migration on login under CSL. See <a
 * href="https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0038-optimize-reuses-stateful-oidc-webapp-chain.md">ADR-0038</a>.
 *
 * <p>The OIDC login success handler belongs to CSL, so the hook is an event listener rather than a
 * handler of Optimize's own. {@code OAuth2LoginAuthenticationFilter} publishes {@link
 * InteractiveAuthenticationSuccessEvent} after populating the {@code SecurityContext}, so the
 * authenticated principal is already resolvable here.
 *
 * <p>A user who adds a new SSO login method gets a new SaaS identity, and Auth0 then carries the
 * previous one in the {@code https://camunda.com/originalUserId} claim. When it differs from the id
 * the user is now authenticated as, stored entity ownership is rewritten so the user keeps their
 * entities. Repeated logins are safe: {@link UserIdMigrationService#migrateUserIdIfNeeded} owns the
 * deduplication and no-ops once an old id has been migrated.
 *
 * <p>SaaS provisioning resolved users by email on early 8.10 clusters, so entities created there
 * are owned by the email. The listener moves them to the current id for the same reason.
 */
@Component
@Conditional(CCSaaSCondition.class)
@ConditionalOnProperty(
    name = "optimize.security.csl.enabled",
    havingValue = "true",
    matchIfMissing = true)
public final class AuthenticationSuccessListener {

  /** Auth0 claim carrying the user's previous SaaS identity, absent unless it changed. */
  static final String ORIGINAL_USER_ID_CLAIM = "https://camunda.com/originalUserId";

  static final String CAMUNDA_META_CLAIM = "https://camunda.com/meta";
  static final String CAMUNDA_META_EMAIL = "email";

  private static final Logger LOG = LoggerFactory.getLogger(AuthenticationSuccessListener.class);

  private final UserIdMigrationService userIdMigrationService;
  private final CamundaAuthenticationProvider camundaAuthenticationProvider;

  public AuthenticationSuccessListener(
      final UserIdMigrationService userIdMigrationService,
      final CamundaAuthenticationProvider camundaAuthenticationProvider) {
    this.userIdMigrationService = userIdMigrationService;
    this.camundaAuthenticationProvider = camundaAuthenticationProvider;
  }

  @EventListener
  public void onInteractiveAuthenticationSuccess(
      final InteractiveAuthenticationSuccessEvent event) {
    final Authentication authentication = event.getAuthentication();
    if (!(authentication instanceof final OAuth2AuthenticationToken oauthToken)
        || !(oauthToken.getPrincipal() instanceof final OidcUser oidcUser)) {
      return;
    }
    final String subject = maskForLog(oidcUser.getSubject());
    LOG.debug("CSL OIDC login success for subject [{}]", subject);

    try {
      migrateIfIdentityChanged(oidcUser);
    } catch (final RuntimeException e) {
      // Never let a migration problem fail the login. This event is published before the success
      // handler redirects (AbstractAuthenticationProcessingFilter#successfulAuthentication), and
      // Spring propagates listener exceptions to the publisher, so an escape would turn a
      // successful login into an error page. Resolving the CSL authentication can throw, for
      // example when no CamundaAuthenticationConverter matches or the claims mapping is empty.
      // The migration is best effort: the user keeps their session and a later login retries.
      LOG.warn(
          "User-id migration failed for subject [{}]; the login itself is unaffected and entities"
              + " owned by the previous identity stay with it until a later login retries.",
          subject,
          sanitize(
              e,
              oidcUser.getSubject(),
              oidcUser.getClaimAsString(ORIGINAL_USER_ID_CLAIM),
              emailClaim(oidcUser)));
    }
  }

  private void migrateIfIdentityChanged(final OidcUser oidcUser) {
    final String originalUserId = oidcUser.getClaimAsString(ORIGINAL_USER_ID_CLAIM);
    final String emailUserId = emailClaim(oidcUser);
    if (isBlank(originalUserId) && isBlank(emailUserId)) {
      return;
    }

    final String currentUserId = resolveCurrentUserId();
    if (isBlank(currentUserId)) {
      // Fail safe rather than guess: migrating onto the wrong id would reassign entity ownership.
      LOG.warn(
          "Skipping user-id migration for subject [{}]: CSL resolved no authenticated username."
              + " Entities owned by the previous identity stay with it until the next login.",
          maskForLog(oidcUser.getSubject()));
      return;
    }

    migrateIfDifferent(originalUserId, currentUserId, "originalUserId claim");
    migrateIfDifferent(emailUserId, currentUserId, "email claim");
  }

  private void migrateIfDifferent(
      final String previousUserId, final String currentUserId, final String trigger) {
    if (!isBlank(previousUserId) && !previousUserId.equals(currentUserId)) {
      userIdMigrationService.migrateUserIdIfNeeded(currentUserId, previousUserId, trigger);
    }
  }

  /**
   * The SaaS token has no standard email_verified claim, because the login asks only for the openid
   * and profile scopes. The Auth0 post-login flow denies every login with an unverified email
   * before it sets the meta claim. A token that does carry email_verified must confirm it.
   */
  private static String emailClaim(final OidcUser oidcUser) {
    final Object emailVerified = oidcUser.getClaims().get(StandardClaimNames.EMAIL_VERIFIED);
    if (emailVerified != null && !Boolean.TRUE.equals(emailVerified)) {
      return null;
    }
    return oidcUser.getClaims().get(CAMUNDA_META_CLAIM) instanceof final Map<?, ?> meta
            && meta.get(CAMUNDA_META_EMAIL) instanceof final String email
        ? email
        : null;
  }

  private static boolean isBlank(final String value) {
    return value == null || value.isBlank();
  }

  /**
   * Resolves the id Optimize stores entity ownership under, which is CSL's authenticated username
   * (the configured {@code username-claim}), the same source {@code
   * CCSMTokenService#getCurrentUserIdFromAuthToken} reads. Deriving it from the OIDC principal name
   * instead would silently diverge whenever {@code username-claim} is not {@code sub}.
   */
  private String resolveCurrentUserId() {
    final CamundaAuthentication authentication =
        camundaAuthenticationProvider.getCamundaAuthentication();
    return authentication == null ? null : authentication.authenticatedUsername();
  }
}
