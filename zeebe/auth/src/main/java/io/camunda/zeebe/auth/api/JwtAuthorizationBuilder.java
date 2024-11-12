/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.auth.api;

/**
 * A builder interface for implementing token authorization builders.
 *
 * @param <T> - The implementation class for the builder
 * @param <A> - The algorithm class that will be used for signing or validating the token.
 * @param <U> - The output of the builder
 */
public interface JwtAuthorizationBuilder<T extends JwtAuthorizationBuilder<T, A, U>, A, U> {

  public static String DEFAULT_ISSUER = "zeebe-gateway";
  public static String DEFAULT_AUDIENCE = "zeebe-broker";
  public static String DEFAULT_SUBJECT = "zeebe-client";
  public static final String USER_TOKEN_CLAIM_PREFIX = "user_token_";

  /**
   * Sets the token subject.
   *
   * @param subject - the subject String
   * @return the builder instance
   */
  T withSubject(String subject);

  /**
   * Sets the token issuer.
   *
   * @param issuer - the issuer String
   * @return the builder instance
   */
  T withIssuer(String issuer);

  /**
   * Sets the token audience.
   *
   * @param audience - the audience String
   * @return the builder instance
   */
  T withAudience(String audience);

  /**
   * Sets the signing algorithm for the token. The algorithm may depend on the token library.
   *
   * @param signingAlgorithm - the signing algorithm instance
   * @return the builder instance
   */
  T withSigningAlgorithm(A signingAlgorithm);

  U build();
}
