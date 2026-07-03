/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset.store;

import java.util.regex.Pattern;

/**
 * The shared identifier-safety boundary for every serving backend. A declared dimension/meter name
 * becomes a physical column (RDBMS) or field (ES/OS) identifier, which — unlike a value — cannot be
 * parameter-bound, so it is the one place declared (and, for {@code var.*} dimensions,
 * user-influenced) text reaches a query. This <b>validates</b> rather than rewrites: the only
 * transformation is folding the {@code var.region} namespace dot to an underscore; the result must
 * then be a plain identifier within a length cap, or the declaration is rejected. Anything carrying
 * a quote, semicolon, whitespace, or other metacharacter fails the allowlist and throws — it is
 * never coerced into a "valid" identifier.
 */
public final class Identifiers {

  /** Postgres caps identifiers at 63 bytes; stay under it for every target backend. */
  private static final int MAX_LENGTH = 60;

  private static final Pattern SAFE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

  private Identifiers() {}

  /** Maps a declared name to a safe physical identifier, or throws if it is not one. */
  public static String safeColumn(final String name) {
    final String identifier = name.replace('.', '_');
    if (!SAFE.matcher(identifier).matches() || identifier.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "unsafe identifier derived from declared name '"
              + name
              + "'; names must be [A-Za-z_][A-Za-z0-9_]* (dots allowed for var.* namespaces) and at"
              + " most "
              + MAX_LENGTH
              + " chars");
    }
    return identifier;
  }
}
