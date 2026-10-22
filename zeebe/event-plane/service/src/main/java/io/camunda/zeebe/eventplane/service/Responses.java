/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Map;

/** Writes the service's responses: JSON, or no body at all. */
final class Responses {

  private static final String CONTENT_TYPE = "Content-Type";
  private static final String JSON = "application/json";

  private Responses() {}

  /** A response with a JSON body. */
  static void json(final HttpExchange exchange, final int status, final byte[] body)
      throws IOException {
    exchange.getResponseHeaders().set(CONTENT_TYPE, JSON);
    if ("HEAD".equals(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(status, -1);
      return;
    }
    exchange.sendResponseHeaders(status, body.length);
    try (final var out = exchange.getResponseBody()) {
      out.write(body);
    }
  }

  /** A response with a JSON body of the value. */
  static void json(final HttpExchange exchange, final int status, final Object value)
      throws IOException {
    json(exchange, status, Json.write(value));
  }

  /** A response of a status and an {@code {"error": …}} body that says what went wrong. */
  static void error(final HttpExchange exchange, final int status, final String message)
      throws IOException {
    json(exchange, status, Map.of("error", message));
  }

  /** A response without a body. */
  static void empty(final HttpExchange exchange, final int status) throws IOException {
    exchange.sendResponseHeaders(status, -1);
  }
}
