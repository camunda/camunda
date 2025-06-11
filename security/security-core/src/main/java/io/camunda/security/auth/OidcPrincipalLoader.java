/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.Option;
import io.camunda.zeebe.util.LockUtil;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import net.jcip.annotations.GuardedBy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OidcPrincipalLoader {
  private static final Configuration CONFIGURATION =
      Configuration.builder()
          // Ignore the common case that the last path element is not set
          .options(Option.DEFAULT_PATH_LEAF_TO_NULL)
          .jsonProvider(null)
          .mappingProvider(null)
          .build();

  private static final Logger LOG = LoggerFactory.getLogger(OidcPrincipalLoader.class);

  @GuardedBy("evaluationLock")
  private final JsonPath usernamePath;

  @GuardedBy("evaluationLock")
  private final JsonPath clientIdPath;

  // Lock to prevent concurrent evaluation of compiled JSONPath expressions. Necessary due to
  // https://github.com/json-path/JsonPath/issues/975
  private final ReentrantLock evaluationLock = new ReentrantLock();

  public OidcPrincipalLoader(final String usernameClaim, final String clientIdClaim) {
    usernamePath =
        usernameClaim != null ? JsonPath.compile(sanitizeClaimPath(usernameClaim)) : null;
    clientIdPath =
        clientIdClaim != null ? JsonPath.compile(sanitizeClaimPath(clientIdClaim)) : null;
  }

  public OidcPrincipals load(final Map<String, Object> claims) {
    return LockUtil.withLock(
        evaluationLock,
        () ->
            new OidcPrincipals(
                tryReadJsonPath(claims, usernamePath), tryReadJsonPath(claims, clientIdPath)));
  }

  private static String tryReadJsonPath(final Map<String, Object> claims, final JsonPath path) {
    if (path == null) {
      return null;
    }
    try {
      return switch (path.read(claims, CONFIGURATION)) {
        case final String stringValue -> stringValue;
        case null -> null;
        default ->
            throw new IllegalArgumentException(
                "Value for %s is not a string. Please check your OIDC configuration."
                    .formatted(path.getPath()));
      };
    } catch (final JsonPathException e) {
      LOG.debug("Failed to evaluate expression {} on claims {}", path, claims, e);
      return null;
    }
  }

  private String sanitizeClaimPath(final String claim) {
    // If the claim starts with a dollar sign, it is already a JSONPath expression.
    // Otherwise, we wrap it with the dollar sign to denote a JSONPath.
    // We also ensure that the claim is wrapped in single quotes to handle cases where the claim
    // name contains special characters.
    return claim.startsWith("$") ? claim : "$['" + claim + "']";
  }

  public record OidcPrincipals(String username, String clientId) {}
}
