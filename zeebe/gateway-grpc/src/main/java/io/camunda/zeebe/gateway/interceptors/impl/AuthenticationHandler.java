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
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.OidcGroupsLoader;
import io.camunda.security.auth.OidcPrincipalLoader;
import io.camunda.security.auth.OidcPrincipalLoader.OidcPrincipals;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.service.UserServices;
import io.camunda.zeebe.util.Either;
import io.grpc.Context;
import io.grpc.Status;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
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
    private final JwtDecoder jwtDecoder;
    private final OidcAuthenticationConfiguration oidcAuthenticationConfiguration;
    private final OidcPrincipalLoader oidcPrincipalLoader;
    private final OidcGroupsLoader oidcGroupsLoader;

    public Oidc(
        final JwtDecoder jwtDecoder,
        final OidcAuthenticationConfiguration oidcAuthenticationConfiguration) {
      this.jwtDecoder = Objects.requireNonNull(jwtDecoder);
      this.oidcAuthenticationConfiguration =
          Objects.requireNonNull(oidcAuthenticationConfiguration);
      oidcPrincipalLoader =
          new OidcPrincipalLoader(
              oidcAuthenticationConfiguration.getUsernameClaim(),
              oidcAuthenticationConfiguration.getClientIdClaim());
      oidcGroupsLoader = new OidcGroupsLoader(oidcAuthenticationConfiguration.getGroupsClaim());
    }

    @Override
    public Either<Status, Context> authenticate(final String authorizationHeader) {
      if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
        return Either.left(
            Status.UNAUTHENTICATED.augmentDescription(
                "Expected authentication information to start with '%s'".formatted(BEARER_PREFIX)));
      }

      final Jwt token;
      try {
        token = jwtDecoder.decode(authorizationHeader.substring(BEARER_PREFIX.length()));
      } catch (final JwtException e) {
        return Either.left(
            Status.UNAUTHENTICATED
                .augmentDescription("Expected a valid token, see cause for details")
                .withCause(e));
      }

      var context = Context.current();
      context = context.withValue(IS_CAMUNDA_USERS_ENABLED, false);
      context =
          context.withValue(
              IS_CAMUNDA_GROUPS_ENABLED,
              !oidcAuthenticationConfiguration.isGroupsClaimConfigured());
      if (oidcAuthenticationConfiguration.isGroupsClaimConfigured()) {
        try {
          context = context.withValue(GROUPS_CLAIMS, oidcGroupsLoader.load(token.getClaims()));
        } catch (final Exception e) {
          return Either.left(
              Status.UNAUTHENTICATED
                  .augmentDescription("Failed to load OIDC groups, see cause for details")
                  .withCause(e));
        }
      }

      final OidcPrincipals principals;
      try {
        principals = oidcPrincipalLoader.load(token.getClaims());
      } catch (final Exception e) {
        return Either.left(
            Status.UNAUTHENTICATED
                .augmentDescription("Failed to load OIDC principals, see cause for details")
                .withCause(e));
      }

      if (principals.username() == null && principals.clientId() == null) {
        return Either.left(
            Status.UNAUTHENTICATED.augmentDescription(
                "Expected either a username (claim: %s) or client ID (claim: %s) on the token, but no matching claim found"
                    .formatted(
                        oidcAuthenticationConfiguration.getUsernameClaim(),
                        oidcAuthenticationConfiguration.getClientIdClaim())));
      }

      final var preferUsernameClaim = oidcAuthenticationConfiguration.isPreferUsernameClaim();
      if ((preferUsernameClaim && principals.username() != null) || principals.clientId() == null) {
        return Either.right(
            context
                .withValue(USERNAME, principals.username())
                .withValue(USER_CLAIMS, token.getClaims()));
      } else {
        return Either.right(
            context
                .withValue(CLIENT_ID, principals.clientId())
                .withValue(USER_CLAIMS, token.getClaims()));
      }
    }
  }

  final class BasicAuth implements AuthenticationHandler {
    private static final String BASIC_PREFIX = "Basic ";
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

      final var decodedAuth =
          new String(
              Base64.getDecoder().decode(authorizationHeader.substring(BASIC_PREFIX.length())));
      final var authParts = decodedAuth.split(":", 2);
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
      return userServices
          .withAuthentication(CamundaAuthentication.anonymous())
          .search(userQuery)
          .items()
          .stream()
          .filter(Objects::nonNull)
          .findFirst();
    }

    private boolean isPasswordValid(final String password, final String userPassword) {
      return passwordEncoder.matches(password, userPassword);
    }
  }
}
