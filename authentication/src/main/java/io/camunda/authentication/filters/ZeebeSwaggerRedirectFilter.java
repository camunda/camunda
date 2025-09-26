/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Filter that handles Swagger UI redirects for zeebe subdomains. Since zeebe domains don't have UI
 * sessions for CSRF tokens, we redirect to the corresponding operate domain where users can
 * authenticate.
 */
@Component
@Order(1) // Execute early in the filter chain
public class ZeebeSwaggerRedirectFilter implements Filter {

  private static final Logger LOG = LoggerFactory.getLogger(ZeebeSwaggerRedirectFilter.class);

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {

    if (request instanceof final HttpServletRequest httpRequest
        && response instanceof final HttpServletResponse httpResponse) {

      final String requestUri = httpRequest.getRequestURI();
      final String serverName = httpRequest.getServerName();

      // Check if this is on a zeebe subdomain
      if (serverName != null && serverName.contains(".zeebe.")) {

        // Handle Swagger UI requests - redirect to operate domain
        if (requestUri != null && requestUri.startsWith("/swagger-ui")) {
          final String redirectUrl = buildOperateSwaggerRedirectUrl(httpRequest);
          if (redirectUrl != null) {
            LOG.info(
                "Redirecting zeebe subdomain Swagger request from {} to {}",
                serverName,
                redirectUrl);
            httpResponse.setStatus(HttpServletResponse.SC_FOUND);
            httpResponse.setHeader("Location", redirectUrl);
            return; // Don't continue the filter chain
          }
        }

        // Handle API requests without CSRF token - redirect to operate login
        if (isApiRequestWithoutCsrfToken(httpRequest)) {
          final String redirectUrl = buildOperateLoginRedirectUrl(httpRequest);
          if (redirectUrl != null) {
            LOG.info(
                "Redirecting zeebe subdomain API request without CSRF token from {} to Operate login: {}",
                serverName,
                redirectUrl);
            httpResponse.setStatus(HttpServletResponse.SC_FOUND);
            httpResponse.setHeader("Location", redirectUrl);
            return; // Don't continue the filter chain
          }
        }
      }
    }

    // Continue with the normal filter chain for all other requests
    chain.doFilter(request, response);
  }

  /** Checks if this is an API request (POST, PUT, DELETE, PATCH) without a CSRF token */
  private boolean isApiRequestWithoutCsrfToken(final HttpServletRequest request) {
    final String method = request.getMethod();
    final String requestUri = request.getRequestURI();
    final String csrfToken = request.getHeader("X-CSRF-TOKEN");

    // Check if it's a state-changing HTTP method on API endpoints
    final boolean isStateChangingMethod =
        "POST".equalsIgnoreCase(method)
            || "PUT".equalsIgnoreCase(method)
            || "DELETE".equalsIgnoreCase(method)
            || "PATCH".equalsIgnoreCase(method);

    // Check if it's an API endpoint
    final boolean isApiEndpoint =
        requestUri != null
            && (requestUri.startsWith("/api/")
                || requestUri.startsWith("/v1/")
                || requestUri.startsWith("/v2/"));

    // Check if CSRF token is missing
    final boolean isMissingCsrfToken = csrfToken == null || csrfToken.trim().isEmpty();

    return isStateChangingMethod && isApiEndpoint && isMissingCsrfToken;
  }

  private String buildOperateSwaggerRedirectUrl(final HttpServletRequest request) {
    try {
      final URI requestUri = new URI(request.getRequestURL().toString());
      final String serverName = requestUri.getHost();

      // Replace .zeebe. with .operate. in the hostname
      final String operateHost = serverName.replace(".zeebe.", ".operate.");

      // Construct the redirect URL with the same path and query parameters
      final StringBuilder redirectUrl =
          new StringBuilder().append(requestUri.getScheme()).append("://").append(operateHost);

      if (requestUri.getPort() != -1) {
        redirectUrl.append(":").append(requestUri.getPort());
      }

      redirectUrl.append(requestUri.getPath());

      if (request.getQueryString() != null) {
        redirectUrl.append("?").append(request.getQueryString());
      }

      return redirectUrl.toString();

    } catch (final URISyntaxException e) {
      LOG.error("Failed to construct Swagger redirect URL", e);
      return null;
    }
  }

  private String buildOperateLoginRedirectUrl(final HttpServletRequest request) {
    try {
      final URI requestUri = new URI(request.getRequestURL().toString());
      final String serverName = requestUri.getHost();

      // Replace .zeebe. with .operate. in the hostname
      final String operateHost = serverName.replace(".zeebe.", ".operate.");

      // Extract tenant ID from the path (assuming format like
      // /90504bcd-c724-4b05-9e89-a4a46063af47/...)
      final String path = requestUri.getPath();
      String tenantId = "";
      if (path != null && path.startsWith("/")) {
        final String[] pathSegments = path.substring(1).split("/");
        if (pathSegments.length > 0 && pathSegments[0].matches("[a-f0-9-]{36}")) {
          tenantId = pathSegments[0];
        }
      }

      // Construct the redirect URL to operate login
      final StringBuilder redirectUrl =
          new StringBuilder().append(requestUri.getScheme()).append("://").append(operateHost);

      if (requestUri.getPort() != -1) {
        redirectUrl.append(":").append(requestUri.getPort());
      }

      // Add tenant ID if found, otherwise just go to root operate
      if (!tenantId.isEmpty()) {
        redirectUrl.append("/").append(tenantId).append("/operate");
      } else {
        redirectUrl.append("/operate");
      }

      return redirectUrl.toString();

    } catch (final URISyntaxException e) {
      LOG.error("Failed to construct Operate login redirect URL", e);
      return null;
    }
  }
}
