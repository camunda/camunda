/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class GroupIdPathResolver {
  private GroupIdPathResolver() {}

  // AntPathMatcher (active via spring.mvc.pathmatch.matching-strategy=ant_path_matcher) normalizes
  // double slashes, stripping the leading "/" from group IDs like "%2FmyGroup". Read the raw
  // encoded URI instead and decode it to recover the full group ID.
  public static String resolveGroupId(final HttpServletRequest request, final String fallback) {
    try {
      final String uri = request.getRequestURI();
      final int idx = uri.lastIndexOf("/groups/");
      if (idx < 0) {
        return fallback;
      }
      return URLDecoder.decode(uri.substring(idx + "/groups/".length()), StandardCharsets.UTF_8);
    } catch (final Exception e) {
      return fallback;
    }
  }
}
