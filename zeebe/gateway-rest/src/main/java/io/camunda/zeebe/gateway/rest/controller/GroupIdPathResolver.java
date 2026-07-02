/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.web.util.UriUtils;

public final class GroupIdPathResolver {
  private GroupIdPathResolver() {}

  // AntPathMatcher (active via spring.mvc.pathmatch.matching-strategy=ant_path_matcher) normalizes
  // double slashes, stripping the leading "/" from group IDs like "%2FmyGroup". Build an exact
  // prefix from the known, correctly-bound path segments before the group ID so the search cannot
  // match inside the group ID itself (fixes lastIndexOf ambiguity). UriUtils.decode is used instead
  // of URLDecoder so that '+' is not mistranslated as space.
  public static String resolveGroupId(
      final HttpServletRequest request, final String groupsPathPrefix, final String fallback) {
    try {
      final String uri = request.getRequestURI();
      final String prefix = request.getContextPath() + groupsPathPrefix;
      final int idx = uri.indexOf(prefix);
      if (idx < 0) {
        return fallback;
      }
      return UriUtils.decode(uri.substring(idx + prefix.length()), StandardCharsets.UTF_8);
    } catch (final Exception e) {
      return fallback;
    }
  }
}
