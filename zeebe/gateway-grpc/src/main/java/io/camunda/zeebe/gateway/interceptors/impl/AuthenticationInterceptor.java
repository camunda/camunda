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
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

public class AuthenticationInterceptor implements ServerInterceptor {

  public static final Context.Key<Map<String, Object>> USER_CLAIMS =
      Context.key("io.camunda.zeebe:user_claim");
  public static final Context.Key<String> USERNAME = Context.key("io.camunda.zeebe:username");
  private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationInterceptor.class);
  private static final Metadata.Key<String> AUTH_KEY =
      Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

  private final UserServices userServices;
  private final PasswordEncoder passwordEncoder;
  private final JwtDecoder jwtDecoder;

  public AuthenticationInterceptor(
      final UserServices userServices,
      final PasswordEncoder passwordEncoder,
      final JwtDecoder jwtDecoder) {
    this.userServices = userServices;
    this.passwordEncoder = passwordEncoder;
    this.jwtDecoder = jwtDecoder;
  }

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {
    final var methodDescriptor = call.getMethodDescriptor();

    final var authorization = headers.get(AUTH_KEY);
    if (authorization == null) {
      LOGGER.debug(
          "Denying call {} as no authentication information was provided",
          methodDescriptor.getFullMethodName());
      return deny(
          call,
          Status.UNAUTHENTICATED.augmentDescription(
              "Expected authentication information at header with key [%s], but found nothing"
                  .formatted(AUTH_KEY.name())));
    }
    // the authentication can be of 2 types, oidc or basic.
    if (isBasicAuth(authorization)) {
      return handleBasicAuth(call, headers, next, methodDescriptor, authorization);
    }
    return handleOidcAuth(call, headers, next, methodDescriptor, authorization);
  }

  private boolean isBasicAuth(final String authorization) {
    return authorization.startsWith("Basic ");
  }

  private <ReqT, RespT> Listener<ReqT> handleBasicAuth(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next,
      final MethodDescriptor<ReqT, RespT> methodDescriptor,
      final String authorization) {
    try {
      final String basicAuth = authorization.replaceFirst("Basic ", "");
      final var decodedAuth = new String(Base64.getDecoder().decode(basicAuth));
      final String[] authParts = decodedAuth.split(":", 2);
      final String username = authParts[0];
      final String password = authParts[1];

      final var userOpt = loadUserByUsername(username);
      if (userOpt.isEmpty()) {
        LOGGER.debug(
            "Denying call {} as the user {} does not exist",
            methodDescriptor.getFullMethodName(),
            username);
        return deny(
            call,
            Status.UNAUTHENTICATED
                .augmentDescription("Invalid credentials")
                .withCause(new IllegalArgumentException("Invalid credentials")));
      }

      final var user = userOpt.get();
      if (!isPasswordValid(password, user.password())) {
        LOGGER.debug(
            "Denying call {} as the password is not valid for user {}",
            methodDescriptor.getFullMethodName(),
            user.username());
        return deny(
            call,
            Status.UNAUTHENTICATED
                .augmentDescription("Invalid credentials")
                .withCause(new IllegalArgumentException("Invalid credentials")));
      }

      final var context = Context.current().withValue(USERNAME, user.username());
      return Contexts.interceptCall(context, call, headers, next);
    } catch (final RuntimeException e) {
      LOGGER.debug(
          "Denying call {} as the authentication info are not valid. Error message: {}",
          methodDescriptor.getFullMethodName(),
          e.getMessage());
      return deny(
          call,
          Status.UNAUTHENTICATED
              .augmentDescription("Expected valid authentication info, see cause for details")
              .withCause(e));
    }
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

  private <ReqT, RespT> Listener<ReqT> handleOidcAuth(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next,
      final MethodDescriptor<ReqT, RespT> methodDescriptor,
      final String authorization) {
    final String token = authorization.replaceFirst("^Bearer ", "");
    try {
      // get user claims and set them in the context
      final var claims = jwtDecoder.decode(token).getClaims();
      final var context = Context.current().withValue(USER_CLAIMS, claims);
      return Contexts.interceptCall(context, call, headers, next);
    } catch (final RuntimeException e) {
      LOGGER.debug(
          "Denying call {} as the token is not valid. Error message: {}",
          methodDescriptor.getFullMethodName(),
          e.getMessage());
      return deny(
          call,
          Status.UNAUTHENTICATED
              .augmentDescription("Expected a valid token, see cause for details")
              .withCause(e));
    }
  }

  private <ReqT> ServerCall.Listener<ReqT> deny(
      final ServerCall<ReqT, ?> call, final Status status) {
    call.close(status, new Metadata());
    return new ServerCall.Listener<>() {};
  }
}
