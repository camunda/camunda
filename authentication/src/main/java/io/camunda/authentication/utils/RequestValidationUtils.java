/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.UrlUtils;
import org.springframework.web.util.UriComponentsBuilder;

public class RequestValidationUtils {

  public static boolean isAllowedRedirect(final HttpServletRequest request, final String url) {
    if (url == null || url.isBlank()) {
      return false;
    }

    if (url.contains("\r") || url.contains("\n")) {
      return false;
    }

    final String baseUrl =
        UriComponentsBuilder.fromUriString(UrlUtils.buildFullRequestUrl(request))
            .replacePath(null)
            .replaceQuery(null)
            .fragment(null)
            .build()
            .toUriString();
    return url.startsWith(baseUrl);
  }
}
