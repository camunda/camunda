/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Compares OIDC URIs as the runtime does. Two URIs that differ only by a trailing slash name one
 * endpoint: discovery settles a configured {@code issuer-uri} on the provider's issuer, so the
 * security library, the gRPC gateway and the membership lookup all treat them as one issuer
 * (#61896).
 */
@NullMarked
final class OidcIssuerUris {

  private OidcIssuerUris() {}

  /** The URI without one trailing slash, or {@code null} for {@code null}. */
  static @Nullable String withoutTrailingSlash(final @Nullable String uri) {
    return uri != null && uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
  }

  /** Whether the two URIs are equal when a trailing slash is ignored. */
  static boolean isSame(final @Nullable String first, final @Nullable String second) {
    final String a = withoutTrailingSlash(first);
    final String b = withoutTrailingSlash(second);
    return a == null ? b == null : a.equals(b);
  }
}
