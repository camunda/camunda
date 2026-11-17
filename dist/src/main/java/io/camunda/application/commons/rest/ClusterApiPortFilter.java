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
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Splits the REST surface between the main port and the dedicated cluster API port: the cluster
 * port serves only {@code /cluster/v2/**}, and the main port no longer serves it — except the
 * public status endpoints, which stay reachable there for monitoring that probes the main port.
 *
 * <p>Runs before Spring Security, so a request on the wrong port never reaches an authentication
 * chain and is answered with a uniform 404. The port is the connector's local port, never the
 * client-controlled {@code Host} header.
 */
final class ClusterApiPortFilter extends OncePerRequestFilter {

  private static final Set<String> MAIN_PORT_CLUSTER_PATHS =
      Set.of(
          ClusterApiSettings.CLUSTER_API_PREFIX + "/status",
          ClusterApiSettings.CLUSTER_API_PREFIX + "/status/upgrade");

  private final int clusterPort;

  ClusterApiPortFilter(final int clusterPort) {
    this.clusterPort = clusterPort;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
      throws ServletException, IOException {
    // The decoded, normalized path within the application — what Spring Security and Spring MVC
    // match against — so an encoded variant cannot slip past this gate.
    final String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    final boolean clusterApi = isClusterApi(path);
    final boolean allowed =
        request.getLocalPort() == clusterPort
            ? clusterApi
            : !clusterApi || MAIN_PORT_CLUSTER_PATHS.contains(path);
    if (!allowed) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    chain.doFilter(request, response);
  }

  private static boolean isClusterApi(final String path) {
    return path.equals(ClusterApiSettings.CLUSTER_API_PREFIX)
        || path.startsWith(ClusterApiSettings.CLUSTER_API_PREFIX + "/");
  }
}
