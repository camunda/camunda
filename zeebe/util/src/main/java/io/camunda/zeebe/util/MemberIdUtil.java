/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
/*
 Utility functions to create MemberId from zone & node-id. Because of module dependencies, it
 cannot live in `atomix/cluster`.
*/
public final class MemberIdUtil {

  private MemberIdUtil() {}

  /**
   * Returns the canonical string representation of a zone-aware member identifier.
   *
   * <p>When {@code zone} is {@code null} the result is the bare form {@code "$nodeId"}; otherwise
   * it is {@code "$zone/$nodeId"}. Leading/trailing whitespace is stripped from {@code zone}.
   *
   * @throws IllegalArgumentException if {@code zone} is blank
   */
  public static String memberIdString(final @Nullable String zone, final int nodeId) {
    if (zone == null) {
      return Integer.toString(nodeId);
    }
    final var stripped = zone.strip();
    if (stripped.isEmpty()) {
      throw new IllegalArgumentException("Expected zone to be non-empty, but was empty");
    }
    return stripped + "/" + nodeId;
  }
}
