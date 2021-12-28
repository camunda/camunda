/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import java.util.Optional;

import static org.camunda.optimize.rest.IngestionRestService.QUERY_PARAMETER_ACCESS_TOKEN;
import static org.camunda.optimize.rest.constants.RestConstants.AUTH_COOKIE_TOKEN_VALUE_PREFIX;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AuthorizationUtil {
  public static void validateAccessToken(final ContainerRequestContext requestContext,
                                         final String expectedAccessToken) {
    final MultivaluedMap<String, String> queryParameters = requestContext.getUriInfo().getQueryParameters();
    final String queryParameterAccessToken = queryParameters.getFirst(QUERY_PARAMETER_ACCESS_TOKEN);

    if (expectedAccessToken == null || (!expectedAccessToken.equals(extractAuthorizationHeaderToken(requestContext))
      && !expectedAccessToken.equals(queryParameterAccessToken))) {
      throw new NotAuthorizedException("Invalid or no secret provided.");
    }
  }

  private static String extractAuthorizationHeaderToken(ContainerRequestContext requestContext) {
    return Optional.ofNullable(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION))
      .map(providedValue -> {
        if (providedValue.startsWith(AUTH_COOKIE_TOKEN_VALUE_PREFIX)) {
          return providedValue.replaceFirst(AUTH_COOKIE_TOKEN_VALUE_PREFIX, "");
        }
        return providedValue;
      }).orElse(null);
  }
}
