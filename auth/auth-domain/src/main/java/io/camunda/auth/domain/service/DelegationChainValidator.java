/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.service;

import io.camunda.auth.domain.exception.TokenExchangeException;

/**
 * Validates the delegation chain depth of a token exchange. Parses the JWT {@code act} claim to
 * determine the current chain depth and enforces a configurable maximum.
 *
 * <p>The {@code act} claim is defined in RFC 8693 Section 4.1. Each level of delegation adds a
 * nested {@code act} claim.
 */
public class DelegationChainValidator {

  public static final int DEFAULT_MAX_DEPTH = 2;

  private final int maxDepth;

  public DelegationChainValidator() {
    this(DEFAULT_MAX_DEPTH);
  }

  public DelegationChainValidator(final int maxDepth) {
    if (maxDepth < 1) {
      throw new IllegalArgumentException("maxDepth must be >= 1, was: " + maxDepth);
    }
    this.maxDepth = maxDepth;
  }

  /**
   * Validates that the subject token's delegation chain does not exceed the configured maximum
   * depth.
   *
   * <p>This method parses the JWT payload (without signature verification — that's the IdP's job)
   * to count the nesting depth of {@code act} claims.
   *
   * @param subjectToken the JWT subject token
   * @throws TokenExchangeException.DelegationChainTooDeep if the chain is too deep
   */
  public void validate(final String subjectToken) {
    final int depth = countActClaimDepth(subjectToken);
    if (depth >= maxDepth) {
      throw new TokenExchangeException.DelegationChainTooDeep(maxDepth, depth + 1);
    }
  }

  /**
   * Counts the depth of nested {@code act} claims in the JWT payload. Returns 0 if no {@code act}
   * claim is present (first delegation).
   */
  int countActClaimDepth(final String jwt) {
    final String payload = extractPayload(jwt);
    if (payload == null) {
      return 0;
    }

    int depth = 0;
    String remaining = payload;
    while (remaining.contains("\"act\"")) {
      depth++;
      final int actIndex = remaining.indexOf("\"act\"");
      remaining = remaining.substring(actIndex + 5);
    }
    return depth;
  }

  private String extractPayload(final String jwt) {
    if (jwt == null) {
      return null;
    }
    final String[] parts = jwt.split("\\.");
    if (parts.length < 2) {
      return null;
    }
    try {
      return new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
    } catch (final IllegalArgumentException e) {
      return null;
    }
  }

  public int getMaxDepth() {
    return maxDepth;
  }
}
