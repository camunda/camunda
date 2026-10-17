/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import io.camunda.search.entities.UserEntity;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.api.model.config.oidc.OidcPrincipalClaims;
import io.camunda.security.core.oidc.OidcGroupsExtractor;
import io.camunda.security.core.oidc.OidcPrincipalLoader;
import io.camunda.security.core.oidc.OidcPrincipalLoader.OidcPrincipals;
import io.camunda.security.oidc.OidcClaimExtractor;
import io.camunda.service.UserServices;
import io.camunda.zeebe.util.Either;
import io.grpc.Context;
import io.grpc.Status;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/** Used by the {@link AuthenticationInterceptor} to authenticate incoming requests. */
public sealed interface AuthenticationHandler {
  Context.Key<String> USERNAME = Context.key("io.camunda.zeebe:username");
  Context.Key<String> CLIENT_ID = Context.key("io.camunda.zeebe:client_id");
  Context.Key<List<String>> GROUPS_CLAIMS = Context.key("io.camunda.zeebe:groups_claims");
  Context.Key<Boolean> IS_CAMUNDA_USERS_ENABLED = Context.key("is_camunda_users_enabled");
  Context.Key<Boolean> IS_CAMUNDA_GROUPS_ENABLED = Context.key("is_camunda_groups_enabled");

  /**
   * Applies authentication logic for the given authorization header. Must not throw exceptions, but
   * return a {@link Status} if the authentication failed.
   *
   * @return either a {@link Status} if the authentication failed, or a {@link Context} with
   *     authentication information if the authentication succeeded.
   */
  Either<Status, Context> authenticate(String authorizationHeader);

  final class Oidc implements AuthenticationHandler {
    public static final Context.Key<Map<String, Object>> USER_CLAIMS =
        Context.key("io.camunda.zeebe:user_claim");

    public static final String BEARER_PREFIX = "Bearer ";
    private static final Logger LOG = LoggerFactory.getLogger(Oidc.class);
    private final JwtDecoder jwtDecoder;
    private final OidcClaimsProvider claimsProvider;
    private final ClaimSettings defaultClaimSettings;
    private final Map<String, ClaimSettings> claimSettingsByIssuer;

    public Oidc(
        final JwtDecoder jwtDecoder,
        final OidcClaimsProvider claimsProvider,
        final OidcConfiguration oidcAuthenticationConfiguration) {
      this(jwtDecoder, claimsProvider, oidcAuthenticationConfiguration, Map.of());
    }

    /**
     * @param oidcAuthenticationConfiguration the tenant's default slot; its {@code groups-claim}
     *     sets the tenant's groups mode. Its groups, username, client-id and prefer-username claims
     *     apply to every provider that does not set its own
     * @param claimConfigsByIssuer claim settings of each provider keyed by its {@code issuer-uri};
     *     a token whose {@code iss} matches no entry is resolved with {@code
     *     oidcAuthenticationConfiguration}
     */
    public Oidc(
        final JwtDecoder jwtDecoder,
        final OidcClaimsProvider claimsProvider,
        final OidcConfiguration oidcAuthenticationConfiguration,
        final Map<String, OidcConfiguration> claimConfigsByIssuer) {
      this.jwtDecoder = Objects.requireNonNull(jwtDecoder);
      this.claimsProvider = Objects.requireNonNull(claimsProvider);
      Objects.requireNonNull(oidcAuthenticationConfiguration);
      final String tenantGroupsClaim = oidcAuthenticationConfiguration.getGroupsClaim();
      defaultClaimSettings =
          new ClaimSettings(
              oidcAuthenticationConfiguration, oidcAuthenticationConfiguration, tenantGroupsClaim);
      claimSettingsByIssuer = new LinkedHashMap<>();
      claimConfigsByIssuer.forEach(
          (issuer, config) ->
              claimSettingsByIssuer.put(
                  issuer,
                  new ClaimSettings(config, oidcAuthenticationConfiguration, tenantGroupsClaim)));
    }

    @Override
    public Either<Status, Context> authenticate(final String authorizationHeader) {
      if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
        return Either.left(
            Status.UNAUTHENTICATED.augmentDescription(
                "Expected authentication information to start with '%s'".formatted(BEARER_PREFIX)));
      }

      final String tokenValue = authorizationHeader.substring(BEARER_PREFIX.length());
      final Jwt token;
      try {
        token = jwtDecoder.decode(tokenValue);
      } catch (final JwtException e) {
        // Exception details may include the IdP URL or issuer mismatch strings; log them
        // server-side and return a generic status so API callers don't see them.
        LOG.debug("Rejecting bearer token: JWT decode failed", e);
        return Either.left(Status.UNAUTHENTICATED.augmentDescription("Invalid bearer token"));
      }

      final ClaimSettings claimSettings =
          claimSettingsByIssuer.getOrDefault(
              token.getClaimAsString(JwtClaimNames.ISS), defaultClaimSettings);

      final Map<String, Object> claims;
      try {
        claims = claimsProvider.claimsFor(token.getClaims(), tokenValue);
      } catch (final Exception e) {
        LOG.warn("Rejecting bearer token: OIDC claims resolution failed", e);
        return Either.left(
            Status.UNAUTHENTICATED.augmentDescription("Failed to resolve OIDC claims"));
      }

      // the groups mode is the tenant's, so it matches what the broker and the Groups API use
      final boolean groupsClaimConfigured = defaultClaimSettings.config().isGroupsClaimConfigured();
      var context = Context.current();
      context = context.withValue(IS_CAMUNDA_USERS_ENABLED, false);
      context = context.withValue(IS_CAMUNDA_GROUPS_ENABLED, !groupsClaimConfigured);
      if (groupsClaimConfigured) {
        // A malformed groups claim contributes no groups instead of rejecting the token, so it
        // degrades exactly like a missing claim — the same way the REST membership path handles it.
        // Authorization then fails closed on the reduced set of groups.
        context =
            context.withValue(
                GROUPS_CLAIMS,
                OidcClaimExtractor.extractOrFallback(
                    () -> claimSettings.groupsExtractor().extract(claims),
                    List.of(),
                    "grpcGroups"));
      }

      final OidcPrincipals principals;
      try {
        principals = claimSettings.principalLoader().load(claims);
      } catch (final Exception e) {
        LOG.warn("Rejecting bearer token: OIDC principal loader failed", e);
        return Either.left(
            Status.UNAUTHENTICATED.augmentDescription("Failed to load OIDC principals"));
      }

      if (principals.username() == null && principals.clientId() == null) {
        return Either.left(
            Status.UNAUTHENTICATED.augmentDescription(
                "Expected either a username (claim: %s) or client ID (claim: %s) on the token, but no matching claim found"
                    .formatted(
                        claimSettings.principalClaims().usernameClaim(),
                        claimSettings.principalClaims().clientIdClaim())));
      }

      final var preferUsernameClaim = claimSettings.principalClaims().preferUsernameClaim();
      if ((preferUsernameClaim && principals.username() != null) || principals.clientId() == null) {
        return Either.right(
            context.withValue(USERNAME, principals.username()).withValue(USER_CLAIMS, claims));
      } else {
        return Either.right(
            context.withValue(CLIENT_ID, principals.clientId()).withValue(USER_CLAIMS, claims));
      }
    }

    private record ClaimSettings(
        OidcConfiguration config,
        OidcPrincipalClaims principalClaims,
        OidcPrincipalLoader principalLoader,
        OidcGroupsExtractor groupsExtractor) {

      /**
       * A provider uses its own claims, and the claims of the tenant's default slot for each claim
       * that it does not set.
       */
      private ClaimSettings(
          final OidcConfiguration config,
          final OidcConfiguration tenantDefaultSlot,
          final String tenantGroupsClaim) {
        this(config, OidcPrincipalClaims.resolve(config, tenantDefaultSlot), tenantGroupsClaim);
      }

      private ClaimSettings(
          final OidcConfiguration config,
          final OidcPrincipalClaims principalClaims,
          final String tenantGroupsClaim) {
        this(
            config,
            principalClaims,
            new OidcPrincipalLoader(
                principalClaims.usernameClaim(), principalClaims.clientIdClaim()),
            new OidcGroupsExtractor(
                config.isGroupsClaimConfigured() ? config.getGroupsClaim() : tenantGroupsClaim));
      }
    }
  }

  final class BasicAuth implements AuthenticationHandler {
    private static final String BASIC_PREFIX = "Basic ";
    private static final Status MALFORMED_CREDENTIALS =
        Status.UNAUTHENTICATED.augmentDescription("Malformed Basic credentials");
    private final UserServices userServices;
    private final PasswordEncoder passwordEncoder;

    public BasicAuth(final UserServices userServices, final PasswordEncoder passwordEncoder) {
      this.userServices = userServices;
      this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Either<Status, Context> authenticate(final String authorizationHeader) {
      if (!authorizationHeader.startsWith(BASIC_PREFIX)) {
        return Either.left(
            Status.UNAUTHENTICATED.augmentDescription(
                "Expected authentication information to start with '%s'".formatted(BASIC_PREFIX)));
      }

      final String decodedAuth;
      try {
        decodedAuth =
            new String(
                Base64.getDecoder().decode(authorizationHeader.substring(BASIC_PREFIX.length())));
      } catch (final IllegalArgumentException e) {
        return Either.left(MALFORMED_CREDENTIALS);
      }

      final var authParts = decodedAuth.split(":", 2);
      if (authParts.length < 2) {
        return Either.left(MALFORMED_CREDENTIALS);
      }

      final var username = authParts[0];
      final var password = authParts[1];

      final Optional<UserEntity> userOpt;
      try {
        userOpt = loadUserByUsername(username);
      } catch (final RuntimeException e) {
        return Either.left(
            Status.UNAUTHENTICATED.augmentDescription("Failed to authenticate").withCause(e));
      }

      if (userOpt.isEmpty()) {
        return Either.left(Status.UNAUTHENTICATED.augmentDescription("Invalid credentials"));
      }

      final var user = userOpt.get();
      if (!isPasswordValid(password, user.password())) {
        return Either.left(Status.UNAUTHENTICATED.augmentDescription("Invalid credentials"));
      }

      return Either.right(
          Context.current()
              .withValue(IS_CAMUNDA_GROUPS_ENABLED, true)
              .withValue(IS_CAMUNDA_USERS_ENABLED, true)
              .withValue(USERNAME, user.username()));
    }

    private Optional<UserEntity> loadUserByUsername(final String username) {
      final var userQuery =
          SearchQueryBuilders.userSearchQuery(
              fn -> fn.filter(f -> f.usernames(username)).page(p -> p.size(1)));
      return userServices.search(userQuery, CamundaAuthentication.anonymous()).items().stream()
          .filter(Objects::nonNull)
          .findFirst();
    }

    private boolean isPasswordValid(final String password, final String userPassword) {
      return passwordEncoder.matches(password, userPassword);
    }
  }
}
