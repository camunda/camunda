/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.ui;

import java.util.List;
import java.util.function.Function;

/**
 * A second, tiny hand-rolled JSON writer for the {@code /process-map} endpoints' structured (not
 * tabular) responses -- {@link LakeUiServer}'s own {@code jsonResult}/{@code jsonError} only know
 * how to shape the {@code {columns, rows}} tabular form {@code /api/query} returns, so this is a
 * deliberately separate, minimal object/array builder rather than a shared abstraction. Same
 * no-JSON-library-dependency rationale as the rest of this module's UI package.
 */
final class JsonSupport {

  private JsonSupport() {}

  /** Builds a JSON object from an ordered list of already-encoded {@code "key":value} members. */
  static String object(final List<String> members) {
    return "{" + String.join(",", members) + "}";
  }

  /** One {@code "key":value} member with a raw (already-JSON) value. */
  static String member(final String key, final String rawValue) {
    return quote(key) + ":" + rawValue;
  }

  static String stringMember(final String key, final String value) {
    return member(key, value == null ? "null" : quote(value));
  }

  static String numberMember(final String key, final Number value) {
    return member(key, numberLiteral(value));
  }

  static String boolMember(final String key, final boolean value) {
    return member(key, Boolean.toString(value));
  }

  static String nullMember(final String key) {
    return member(key, "null");
  }

  /** Builds a JSON array by rendering each element of {@code items} with {@code toJson}. */
  static <T> String arrayOf(final List<T> items, final Function<T, String> toJson) {
    final StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < items.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(toJson.apply(items.get(i)));
    }
    return sb.append(']').toString();
  }

  static String numberLiteral(final Number value) {
    if (value == null) {
      return "null";
    }
    final double asDouble = value.doubleValue();
    if (Double.isNaN(asDouble) || Double.isInfinite(asDouble)) {
      return "null";
    }
    return value.toString();
  }

  static String quote(final String value) {
    final StringBuilder sb = new StringBuilder(value.length() + 2);
    sb.append('"');
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.append('"').toString();
  }
}
