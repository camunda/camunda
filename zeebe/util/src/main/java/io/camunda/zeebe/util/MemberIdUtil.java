/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Utility functions to create MemberId from zone and node-id. Cannot live in {@code atomix/cluster}
 * due to module dependencies.
 */
@NullMarked
public final class MemberIdUtil {

  private static final Pattern ZONE_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9-]*$");
  private static final int MAX_ZONE_LENGTH = 63;

  private MemberIdUtil() {}

  /**
   * Validates that {@code zone} is a legal zone name: at most 63 characters, starting with an
   * alphanumeric character, and containing only alphanumeric characters and hyphens {@code
   * [A-Za-z0-9-]}. The zone must start with an alphanumeric character.
   *
   * <p>Underscores are intentionally excluded because {@code _} is used as the separator in
   * composite member IDs (see {@link #memberIdString}).
   *
   * @throws IllegalArgumentException if {@code zone} is non-null and violates any constraint
   */
  public static @Nullable String validateZone(final @Nullable String zone) {
    if (zone == null) {
      return null;
    }
    if (zone.length() > MAX_ZONE_LENGTH) {
      throw new IllegalArgumentException(
          "Expected zone length to be <= "
              + MAX_ZONE_LENGTH
              + ", but got "
              + zone.length()
              + " for zone: "
              + zone);
    }
    if (!ZONE_PATTERN.matcher(zone).matches()) {
      throw new IllegalArgumentException(
          "Expected zone to start with an alphanumeric character and contain only alphanumeric characters and hyphens [A-Za-z0-9-], but got: "
              + zone);
    }
    return zone;
  }

  /**
   * Returns the canonical string representation of a zone-aware member identifier.
   *
   * <p>When {@code zone} is {@code null} the result is the bare form {@code "$nodeId"}; otherwise
   * it is {@code "$zone_$nodeId"}. The underscore is the separator because {@link #validateZone}
   * forbids underscores in zone names, making it unambiguous. The zone must already be validated
   * (see {@link #validateZone}).
   */
  public static String memberIdString(final @Nullable String zone, final int nodeId) {
    if (zone == null) {
      return Integer.toString(nodeId);
    }
    return zone + "_" + nodeId;
  }
}
