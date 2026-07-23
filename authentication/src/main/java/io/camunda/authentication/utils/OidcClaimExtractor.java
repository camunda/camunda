/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.utils;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/**
 * Wraps an OIDC claim extraction (e.g. {@code OidcGroupsExtractor#extract} or {@code
 * OidcPrincipalLoader#load}), treating any failure (typically a claim whose resolved value has an
 * unexpected shape) as a safe fallback instead of letting the exception propagate.
 */
public final class OidcClaimExtractor {

  private static final Logger LOG = LoggerFactory.getLogger(OidcClaimExtractor.class);

  // Contexts already logged at WARN. Bounded by the small, fixed set of caller-supplied context
  // strings, so it never grows unbounded.
  private static final Set<String> WARN_ONCE_CONTEXTS = ConcurrentHashMap.newKeySet();

  private OidcClaimExtractor() {}

  public static <T> T extractOrFallback(
      final Supplier<T> extraction, final T fallback, final String context) {
    try {
      final T result = extraction.get();
      return result != null ? result : fallback;
    } catch (final RuntimeException e) {
      logFailure(context, e);
      return fallback;
    }
  }

  private static void logFailure(final String context, final RuntimeException e) {
    // First failure per context logs at WARN with a stack trace; later ones drop to DEBUG so a
    // persistent misconfiguration doesn't spam the logs on every request.
    final Level level = WARN_ONCE_CONTEXTS.add(context) ? Level.WARN : Level.DEBUG;
    LOG.atLevel(level)
        .setCause(e)
        .log("OIDC claim extraction failed ({}); using fallback", context);
  }
}
