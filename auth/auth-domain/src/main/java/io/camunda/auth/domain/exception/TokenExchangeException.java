/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.exception;

/** Base exception for token exchange failures. */
public sealed class TokenExchangeException extends RuntimeException
    permits TokenExchangeException.InvalidGrant,
        TokenExchangeException.InvalidTarget,
        TokenExchangeException.UnsupportedTokenType,
        TokenExchangeException.IdpUnavailable,
        TokenExchangeException.DelegationChainTooDeep {

  public TokenExchangeException(final String message) {
    super(message);
  }

  public TokenExchangeException(final String message, final Throwable cause) {
    super(message, cause);
  }

  /** The subject token or grant is invalid (e.g., expired, revoked, wrong type). */
  public static final class InvalidGrant extends TokenExchangeException {
    public InvalidGrant(final String message) {
      super(message);
    }

    public InvalidGrant(final String message, final Throwable cause) {
      super(message, cause);
    }
  }

  /** The requested target audience or resource is not valid or not allowed. */
  public static final class InvalidTarget extends TokenExchangeException {
    public InvalidTarget(final String message) {
      super(message);
    }
  }

  /** The requested or provided token type is not supported. */
  public static final class UnsupportedTokenType extends TokenExchangeException {
    public UnsupportedTokenType(final String message) {
      super(message);
    }
  }

  /** The identity provider is not reachable or returned an unexpected error. */
  public static final class IdpUnavailable extends TokenExchangeException {
    public IdpUnavailable(final String message, final Throwable cause) {
      super(message, cause);
    }
  }

  /** The delegation chain exceeds the configured maximum depth. */
  public static final class DelegationChainTooDeep extends TokenExchangeException {
    private final int maxDepth;
    private final int actualDepth;

    public DelegationChainTooDeep(final int maxDepth, final int actualDepth) {
      super("Delegation chain depth " + actualDepth + " exceeds maximum allowed depth " + maxDepth);
      this.maxDepth = maxDepth;
      this.actualDepth = actualDepth;
    }

    public int maxDepth() {
      return maxDepth;
    }

    public int actualDepth() {
      return actualDepth;
    }
  }
}
