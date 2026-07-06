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
  // double slashes, stripping the leading "/" from group IDs like "%2FmyGroup". Read the raw
  // encoded URI instead and decode it to recover the full group ID.
  //
  // lastIndexOf("/groups/") is safe here because in the raw (percent-encoded) URI, slashes within
  // path variable values are encoded as %2F — only structural path separators are literal "/".
  // A group ID like "/foo/groups/bar" appears as "%2Ffoo%2Fgroups%2Fbar", so the literal
  // "/groups/" always identifies the structural boundary, not a value embedded in another segment.
  // UriUtils.decode is used instead of URLDecoder so that '+' is not mistranslated as space.
  public static String resolveGroupId(final HttpServletRequest request, final String fallback) {
    try {
      final String uri = request.getRequestURI();
      final int idx = uri.lastIndexOf("/groups/");
      if (idx < 0) {
        return fallback;
      }
      return UriUtils.decode(uri.substring(idx + "/groups/".length()), StandardCharsets.UTF_8);
    } catch (final Exception e) {
      return fallback;
    }
  }
}
