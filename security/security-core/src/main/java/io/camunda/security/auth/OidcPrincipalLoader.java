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
  public static final Configuration CONFIGURATION =
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
    usernamePath = JsonPath.compile(usernameClaim);
    clientIdPath = JsonPath.compile(clientIdClaim);
  }

  public OidcPrincipals load(final Map<String, Object> claims) {
    return new OidcPrincipals(
        tryReadJsonPath(claims, usernamePath), tryReadJsonPath(claims, clientIdPath));
  }

  private static String tryReadJsonPath(final Map<String, Object> claims, final JsonPath path) {
    try {
      return path.read(claims, CONFIGURATION);
    } catch (final JsonPathException e) {
      LOG.debug("Failed to evaluate expression {} on claims {}", path, claims, e);
      return null;
    }
  }

  public record OidcPrincipals(String username, String clientId) {}
}
