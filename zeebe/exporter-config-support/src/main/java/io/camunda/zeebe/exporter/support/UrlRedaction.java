/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.support;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.StringJoiner;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Keeps credentials embedded in exporter URLs out of logs and exception messages. */
@NullMarked
public final class UrlRedaction {

  private static final String OMITTED = "***";

  private UrlRedaction() {}

  /**
   * Describes a comma separated list of URLs without secrets, as {@code
   * scheme://****:****@host:port}. The user info is masked only when present, and the path, query
   * and fragment are dropped. If any URL cannot be parsed into a scheme and host, the whole list is
   * replaced by {@code ***}, as a comma inside a password makes the split ambiguous.
   */
  public static @Nullable String describe(final @Nullable String urls) {
    if (urls == null) {
      return null;
    }
    final var described = new StringJoiner(",");
    for (final var url : urls.split(",", -1)) {
      final var one = describeOne(url);
      if (one == null) {
        return OMITTED;
      }
      described.add(one);
    }
    return described.toString();
  }

  private static @Nullable String describeOne(final String url) {
    final URI uri;
    try {
      uri = new URI(url);
    } catch (final URISyntaxException e) {
      return null;
    }
    if (uri.getScheme() == null || uri.getHost() == null) {
      return null;
    }
    final var result = new StringBuilder(uri.getScheme()).append("://");
    if (uri.getRawUserInfo() != null) {
      result.append("****:****@");
    }
    result.append(uri.getHost());
    if (uri.getPort() >= 0) {
      result.append(':').append(uri.getPort());
    }
    return result.toString();
  }
}
