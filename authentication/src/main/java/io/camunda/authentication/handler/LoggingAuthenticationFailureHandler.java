/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.handler;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

public class LoggingAuthenticationFailureHandler implements AuthenticationFailureHandler {

  private static final Logger LOG =
      LoggerFactory.getLogger(LoggingAuthenticationFailureHandler.class);

  private final AuthenticationFailureHandler delegate;

  public LoggingAuthenticationFailureHandler(final AuthenticationFailureHandler delegate) {
    this.delegate = delegate;
  }

  @Override
  public void onAuthenticationFailure(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException exception)
      throws IOException, ServletException {
    // Only AuthenticationServiceException will not be handled by default failure handlers,
    // instead it will be rethrown and bubble up to tomcat, causing an ERROR log.
    if (!AuthenticationServiceException.class.isAssignableFrom(exception.getClass())) {
      final String callerInfo = extractCallerInfo(request);
      LOG.warn(
          "Bearer token authentication failed: method={}, uri={}, error={}, caller=[{}]",
          request.getMethod(),
          request.getRequestURI(),
          exception.getMessage(),
          callerInfo);
      delegate.onAuthenticationFailure(request, response, exception);
      return;
    }

    LOG.warn("A technical authentication problem occurred", exception);

    // Setting this attributes will cause Tomcat to initiate
    // rendering an error response, ultimately calling our GlobalErrorController.
    request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, exception);
    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
  }

  /**
   * Extracts caller identity from the JWT token in the Authorization header without performing full
   * validation. Decodes only the payload section (Base64) to read the {@code sub}, {@code iss}, and
   * {@code exp} claims. This is safe because we are just logging diagnostics — the token has
   * already failed authentication.
   */
  private static String extractCallerInfo(final HttpServletRequest request) {
    final String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return "no-bearer-token";
    }

    final String token = authHeader.substring(7).trim();
    try {
      // JWT structure: header.payload.signature — we only need the payload
      final String[] parts = token.split("\\.");
      if (parts.length < 2) {
        return "malformed-token";
      }

      final String payloadJson =
          new String(
              Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);

      // Lightweight extraction without a JSON parser dependency — look for sub, iss, exp
      final String sub = extractJsonStringField(payloadJson, "sub");
      final String iss = extractJsonStringField(payloadJson, "iss");
      final String clientId = extractJsonStringField(payloadJson, "client_id");
      final String azp = extractJsonStringField(payloadJson, "azp");
      final Long exp = extractJsonLongField(payloadJson, "exp");

      final StringBuilder sb = new StringBuilder();
      if (sub != null) {
        sb.append("sub=").append(sub);
      }
      if (clientId != null) {
        if (sb.length() > 0) {
          sb.append(", ");
        }
        sb.append("client_id=").append(clientId);
      }
      if (azp != null) {
        if (sb.length() > 0) {
          sb.append(", ");
        }
        sb.append("azp=").append(azp);
      }
      if (iss != null) {
        if (sb.length() > 0) {
          sb.append(", ");
        }
        sb.append("iss=").append(iss);
      }
      if (exp != null) {
        final Instant expiry = Instant.ofEpochSecond(exp);
        final long ageSeconds = Instant.now().getEpochSecond() - exp;
        if (sb.length() > 0) {
          sb.append(", ");
        }
        sb.append("exp=").append(expiry);
        if (ageSeconds > 0) {
          sb.append(" (expired ").append(ageSeconds).append("s ago)");
        }
      }

      return sb.length() > 0 ? sb.toString() : "no-identity-claims";
    } catch (final Exception e) {
      return "token-parse-error: " + e.getMessage();
    }
  }

  /** Extracts a string field value from a JSON string without a full JSON parser. */
  private static String extractJsonStringField(final String json, final String field) {
    final String pattern = "\"" + field + "\"";
    final int idx = json.indexOf(pattern);
    if (idx < 0) {
      return null;
    }
    // Skip past "field": and any whitespace
    int start = idx + pattern.length();
    while (start < json.length() && (json.charAt(start) == ':' || json.charAt(start) == ' ')) {
      start++;
    }
    if (start >= json.length() || json.charAt(start) != '"') {
      return null;
    }
    start++; // skip opening quote
    final int end = json.indexOf('"', start);
    if (end < 0) {
      return null;
    }
    return json.substring(start, end);
  }

  /** Extracts a numeric field value from a JSON string without a full JSON parser. */
  private static Long extractJsonLongField(final String json, final String field) {
    final String pattern = "\"" + field + "\"";
    final int idx = json.indexOf(pattern);
    if (idx < 0) {
      return null;
    }
    int start = idx + pattern.length();
    while (start < json.length() && (json.charAt(start) == ':' || json.charAt(start) == ' ')) {
      start++;
    }
    if (start >= json.length()) {
      return null;
    }
    int end = start;
    while (end < json.length() && Character.isDigit(json.charAt(end))) {
      end++;
    }
    if (end == start) {
      return null;
    }
    try {
      return Long.parseLong(json.substring(start, end));
    } catch (final NumberFormatException e) {
      return null;
    }
  }
}
