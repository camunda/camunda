/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The parameters of a request's query. */
final class Query {

  private Query() {}

  /**
   * The percent-decoded values of the parameters with this name, in order. A parameter without an
   * {@code =}, or with an empty name, is skipped.
   *
   * @throws InvalidRequestException if the query isn't in percent-encoding
   */
  static List<String> values(@Nullable final String rawQuery, final String name) {
    final var values = new ArrayList<String>();
    if (rawQuery != null) {
      for (final var parameter : rawQuery.split("&")) {
        final int equals = parameter.indexOf('=');
        if (equals > 0 && decode(parameter.substring(0, equals)).equals(name)) {
          values.add(decode(parameter.substring(equals + 1)));
        }
      }
    }
    return values;
  }

  private static String decode(final String encoded) {
    try {
      return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    } catch (final IllegalArgumentException e) {
      throw new InvalidRequestException("a query in percent-encoding", "'" + encoded + "'");
    }
  }
}
