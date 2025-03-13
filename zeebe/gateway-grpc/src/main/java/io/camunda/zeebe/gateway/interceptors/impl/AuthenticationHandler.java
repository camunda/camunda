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
import io.camunda.service.UserServices;
import io.camunda.zeebe.util.Either;
import io.grpc.Context;
import io.grpc.Status;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/** Used by the {@link AuthenticationInterceptor} to authenticate incoming requests. */
public sealed interface AuthenticationHandler {

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

    public Oidc(final JwtDecoder jwtDecoder) {
      this.jwtDecoder = Objects.requireNonNull(jwtDecoder);
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

      return Either.right(Context.current().withValue(USER_CLAIMS, token.getClaims()));
    }
  }

  final class BasicAuth implements AuthenticationHandler {
    public static final Context.Key<String> USERNAME = Context.key("io.camunda.zeebe:username");
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

      return Either.right(Context.current().withValue(USERNAME, user.username()));
    }

    private Optional<UserEntity> loadUserByUsername(final String username) {
      final var userQuery =
          SearchQueryBuilders.userSearchQuery(
              fn -> fn.filter(f -> f.username(username)).page(p -> p.size(1)));
      return userServices.search(userQuery).items().stream().filter(Objects::nonNull).findFirst();
    }

    private boolean isPasswordValid(final String password, final String userPassword) {
      return passwordEncoder.matches(password, userPassword);
    }
  }
}
