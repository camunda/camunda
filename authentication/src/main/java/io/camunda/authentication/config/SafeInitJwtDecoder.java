/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Wrapper around JwtDecoder that ensures decoder is created rather at a startup or during later
 * retries.
 */
public class SafeInitJwtDecoder implements JwtDecoder {
  private static final Logger LOG = LoggerFactory.getLogger(SafeInitJwtDecoder.class);

  private final SafeInitProxy<JwtDecoder> proxy;

  public SafeInitJwtDecoder(final Supplier<JwtDecoder> decoderSupplier) {
    // Try to create a decoder. In case of failure - schedule async retries of creation.
    proxy =
        new SafeInitProxy<>(
            decoderSupplier, e -> LOG.debug("Failed to initialize JWT Decoder. Retrying.", e));
  }

  @Override
  public Jwt decode(final String token) throws JwtException {
    // if JWT Decoder is initialized, then delegate method call to it. If not, throw an exception
    // that will be propagated to client.
    return proxy
        .orElseThrow(
            () ->
                // we have to throw AuthenticationCredentialsNotFoundException or any other
                // Exception that extend AuthenticationException in order for Spring to pass it
                // to our error handler.
                // Please see `BearerTokenAuthenticationFilter` `authenticationFailureHandler`
                // part and `doFilterInternal` method.
                new AuthenticationCredentialsNotFoundException(
                    "Authentication service unavailable: Unable to connect to the configured Identity Provider (OIDC). "
                        + "Please try again later or contact your administrator."))
        .decode(token);
  }
}
