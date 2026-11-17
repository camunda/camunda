/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import io.camunda.authentication.clusteradmin.ClusterApiSettings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriUtils;

/**
 * Splits the REST surface between the main port and the dedicated cluster API port: the cluster
 * port serves only {@code /cluster/v2/**}, and the main port no longer serves it — except {@link
 * ClusterApiSettings#PUBLIC_CLUSTER_API_PATHS}, which stay reachable there for monitoring.
 *
 * <p>Runs before Spring Security, so a request on the wrong port never reaches an authentication
 * chain and is answered with a uniform 404. The port is the connector's local port, never the
 * client-controlled {@code Host} header.
 *
 * <p>The decision is made on the container-normalised path (servlet path and path info: decoded,
 * path parameters stripped, dot-segments resolved), which is what Tomcat routes on. A request whose
 * raw path contains dot-segments is rejected outright, so the gate never decides on a different
 * path than Spring MVC or Spring Security, which match the unresolved path.
 */
@NullMarked
final class ClusterApiPortFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(ClusterApiPortFilter.class);

  private final int clusterPort;

  ClusterApiPortFilter(final int clusterPort) {
    this.clusterPort = clusterPort;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
      throws ServletException, IOException {
    final String path = normalizedPath(request);
    if (hasDotSegment(request)) {
      LOG.debug("Rejecting request with a dot-segment path on port {}", request.getLocalPort());
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    final boolean onClusterPort = request.getLocalPort() == clusterPort;
    final boolean clusterApi = isClusterApi(path);
    final boolean allowed =
        onClusterPort
            ? clusterApi
            : !clusterApi || ClusterApiSettings.PUBLIC_CLUSTER_API_PATHS.contains(path);
    if (!allowed) {
      LOG.debug(
          "Rejecting {} on port {}: {} is served on port {} only",
          path,
          request.getLocalPort(),
          onClusterPort ? "the regular API" : ClusterApiSettings.CLUSTER_API_PATTERN,
          onClusterPort ? "the main" : Integer.toString(clusterPort));
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    chain.doFilter(request, response);
  }

  private static String normalizedPath(final HttpServletRequest request) {
    final String pathInfo = request.getPathInfo();
    return pathInfo == null ? request.getServletPath() : request.getServletPath() + pathInfo;
  }

  /**
   * Whether the raw request path contains a {@code .} or {@code ..} segment, also when
   * percent-encoded or followed by path parameters, or is not decodable. Tomcat resolves such
   * segments (after decoding {@code %2F}, see TomcatEncodedSlashConfig) before routing, while
   * Spring MVC and Spring Security match the unresolved path; rejecting them keeps both views
   * identical.
   */
  private static boolean hasDotSegment(final HttpServletRequest request) {
    final String rawPath = request.getRequestURI(); // undecoded; decoded exactly once below
    final String decoded;
    try {
      decoded = UriUtils.decode(rawPath, StandardCharsets.UTF_8);
    } catch (final IllegalArgumentException e) {
      return true; // malformed escape: reject rather than guess what a later component decodes
    }
    for (final String segment : decoded.split("/")) {
      final int parameters = segment.indexOf(';');
      final String name = parameters < 0 ? segment : segment.substring(0, parameters);
      if (".".equals(name) || "..".equals(name)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isClusterApi(final String path) {
    return path.equals(ClusterApiSettings.CLUSTER_API_PREFIX)
        || path.startsWith(ClusterApiSettings.CLUSTER_API_PREFIX + "/");
  }
}
