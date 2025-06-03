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
import java.util.Map;
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

  private final JsonPath usernamePath;
  private final JsonPath clientIdPath;

  public OidcPrincipalLoader(final String usernameClaim, final String clientIdClaim) {
    usernamePath = usernameClaim != null ? JsonPath.compile(usernameClaim) : null;
    clientIdPath = clientIdClaim != null ? JsonPath.compile(clientIdClaim) : null;
  }

  public OidcPrincipals load(final Map<String, Object> claims) {
    return new OidcPrincipals(
        tryReadJsonPath(claims, usernamePath), tryReadJsonPath(claims, clientIdPath));
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

  public record OidcPrincipals(String username, String clientId) {}
}
