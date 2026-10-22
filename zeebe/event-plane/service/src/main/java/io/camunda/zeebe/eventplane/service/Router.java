/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends each request to its endpoint: 404 for a path that isn't one, 405 for a method that the
 * endpoint doesn't take, 400 for a request that is wrong, and 500 for what the service gets wrong.
 */
final class Router implements HttpHandler {

  private static final Logger LOG = LoggerFactory.getLogger(Router.class);

  private static final String UPLOADS = "/v1/uploads";
  private static final String NOTIFICATIONS = "/v1/notifications";
  private static final String CLUSTERS = "/v1/clusters";
  private static final String CREDENTIALS = "/v1/credentials";
  private static final String GROUPS = "/v1/groups/";

  private final UploadHandler uploads;
  private final NotificationHandler notifications;
  private final GroupHandler groups;
  private final ClusterHandler clusters;

  Router(
      final UploadHandler uploads,
      final NotificationHandler notifications,
      final GroupHandler groups,
      final ClusterHandler clusters) {
    this.uploads = uploads;
    this.notifications = notifications;
    this.groups = groups;
    this.clusters = clusters;
  }

  @Override
  public void handle(final HttpExchange exchange) {
    try {
      route(exchange);
    } catch (final InvalidRequestException e) {
      answer(exchange, 400, "Expected %s, but got %s".formatted(e.expected(), e.actual()));
    } catch (final IOException | RuntimeException e) {
      LOG.error(
          "Expected to answer {} {}, but got", exchange.getRequestMethod(), path(exchange), e);
      answer(exchange, 500, "Expected to answer the request, but got " + e);
    } finally {
      exchange.close();
    }
  }

  private void route(final HttpExchange exchange) throws IOException {
    final var path = path(exchange);
    if (path.equals(UPLOADS)) {
      if (allows(exchange, "POST")) {
        uploads.handle(exchange);
      }
    } else if (path.equals(NOTIFICATIONS)) {
      if (allows(exchange, "GET")) {
        notifications.handle(exchange);
      }
    } else if (path.equals(CLUSTERS)) {
      if (allows(exchange, "GET")) {
        clusters.clusters(exchange);
      }
    } else if (path.equals(CREDENTIALS)) {
      if (allows(exchange, "GET")) {
        clusters.credentials(exchange);
      }
    } else if (path.startsWith(GROUPS) && isOneSegment(path.substring(GROUPS.length()))) {
      if (allows(exchange, "GET")) {
        groups.handle(exchange, decode(path.substring(GROUPS.length())));
      }
    } else {
      Responses.error(
          exchange,
          404,
          "Expected a path of %s, %s, %s{group}, %s or %s, but got %s"
              .formatted(UPLOADS, NOTIFICATIONS, GROUPS, CLUSTERS, CREDENTIALS, path));
    }
  }

  /** Whether the request has the method. If not, the response says so with a 405. */
  private static boolean allows(final HttpExchange exchange, final String method)
      throws IOException {
    if (exchange.getRequestMethod().equals(method)) {
      return true;
    }
    exchange.getResponseHeaders().set("Allow", method);
    Responses.error(
        exchange,
        405,
        "Expected the method %s for %s, but got %s"
            .formatted(method, path(exchange), exchange.getRequestMethod()));
    return false;
  }

  private static boolean isOneSegment(final String rest) {
    return !rest.isEmpty() && rest.indexOf('/') < 0;
  }

  private static String path(final HttpExchange exchange) {
    return exchange.getRequestURI().getRawPath();
  }

  /** A path segment in percent-encoding, where a plus is a plus. */
  private static String decode(final String segment) {
    try {
      return URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8);
    } catch (final IllegalArgumentException e) {
      throw new InvalidRequestException("a path in percent-encoding", "'" + segment + "'");
    }
  }

  /** Answers with an error, unless the client is gone or the response has started. */
  private static void answer(final HttpExchange exchange, final int status, final String message) {
    try {
      Responses.error(exchange, status, message);
    } catch (final IOException | RuntimeException e) {
      LOG.debug("Expected to answer {} with {}, but got {}", status, message, e.toString());
    }
  }
}
