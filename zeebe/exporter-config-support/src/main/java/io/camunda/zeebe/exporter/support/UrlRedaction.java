/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.support;

import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Keeps credentials embedded in exporter URLs out of logs and exception messages. */
@NullMarked
public final class UrlRedaction {

  // Matches the userinfo of every URL in a comma separated list. Greedy up to the last '@' of a
  // segment so that a password containing an unescaped '/' or whitespace is still removed. Invalid
  // URLs are where this is needed most, as they end up in error messages.
  private static final Pattern USER_INFO = Pattern.compile("(?<=://)[^,]*@");

  private UrlRedaction() {}

  /** Replaces the {@code user:password@} part of each URL in {@code url} with {@code ***@}. */
  public static @Nullable String redactUserInfo(final @Nullable String url) {
    return url == null ? null : USER_INFO.matcher(url).replaceAll("***@");
  }
}
