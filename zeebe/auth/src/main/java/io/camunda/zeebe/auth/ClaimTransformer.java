/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.auth;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClaimTransformer {
  private static final Logger LOG = LoggerFactory.getLogger(ClaimTransformer.class);

  /**
   * These well-known claims are not forwarded as user claims because they are not useful for
   * mapping rules. Additionally, their type is {@link java.time.Instant} which we don't support
   * anyway. Listing these claims here avoids logging a warning when we drop them.
   */
  private static final Set<String> UNSUPPORTED_CLAIMS = Set.of("exp", "iat", "nbf");

  private ClaimTransformer() {}

  /**
   * Takes a claim from a token and adds them to user claims by prefixing the keys with {@link
   * Authorization#USER_TOKEN_CLAIM_PREFIX}. Some claims will be dropped because they are not useful
   * for mapping rules:
   *
   * <ul>
   *   <li>exp
   *   <li>iat
   *   <li>nbf
   * </ul>
   *
   * Additionally, claims with unsupported value types will be dropped and a warning will be logged.
   * Supported value types are {@link String}, {@link Boolean}, and {@link Number}. We also pass
   * through {@link Collection} values without checking the type of the elements.
   *
   * @param claims A collection of user claims to add to.
   * @param key The key of the claim, without prefix, as it appears in the token. If the key is not
   *     supported the claim will be dropped.
   * @param value The value of the claim. If the type is not supported, the claim will be dropped.
   */
  public static void applyUserClaim(
      final Map<String, Object> claims, final String key, final Object value) {
    if (UNSUPPORTED_CLAIMS.contains(key)) {
      return;
    }

    if (value == null) {
      return;
    }

    if (value instanceof String
        || value instanceof Boolean
        || value instanceof Number
        || value instanceof Collection<?>) {
      claims.put(Authorization.USER_TOKEN_CLAIM_PREFIX + key, value);
    } else {
      LOG.debug("Dropping claim '{}' with unsupported value type '{}'", key, value.getClass());
    }
  }
}
