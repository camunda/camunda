/*
 * Copyright 2014-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.cluster;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Controller cluster identity. */
@NullMarked
public class MemberId extends NodeId {
  /**
   * Null when the member is anonymous When a zone is present, this is the node index in the local
   * cluster (e.g. 0, 1, 2, 3...) If a zone is not present, it's equal to the id
   */
  private final @Nullable Integer nodeIdx;

  /** Null when the member is not zone aware */
  private final @Nullable String zone;

  // id must be created in the factory method as it's a required argument of the super constructor
  private MemberId(final @Nullable String zone, final @Nullable Integer nodeIdx, final String id) {
    super(id);
    this.nodeIdx = validateNodeIdx(nodeIdx);
    this.zone = validateZone(zone);
  }

  public MemberId(final String id) {
    super(id);
    final var parts = id.split("/");
    if (parts.length > 2) {
      throw new IllegalArgumentException("Expected id to be of the form $zone/$id, but got " + id);
    } else if (parts.length == 2) {
      zone = parts[0];
      nodeIdx = tryParseInt(parts[1]);
    } else if (parts.length == 1) {
      zone = null;
      nodeIdx = tryParseInt(parts[0]);
    } else {
      throw new IllegalArgumentException(
          "Expected id to be of the form $zone/$id or $id, but got " + id);
    }
    validateZone(zone);
    validateNodeIdx(nodeIdx);
  }

  /**
   * Creates a new cluster node identifier from the specified string.
   *
   * @return node id
   */
  public static MemberId anonymous() {
    return new MemberId(null, null, UUID.randomUUID().toString());
  }

  /**
   * Creates a new cluster node identifier from the specified string.
   *
   * @param id string identifier
   * @return node id
   */
  public static MemberId from(final String id) {
    return new MemberId(id);
  }

  /**
   * Creates a zone-aware member identifier.
   *
   * <p>When {@code zone} is {@code null} the result is the bare form {@code "$nodeId"}; otherwise
   * it is {@code "$zone/$nodeId"}. Leading/trailing whitespace is stripped from {@code zone}.
   */
  public static MemberId from(final @Nullable String zone, final int nodeId) {
    return new MemberId(zone, nodeId, buildMemberIdString(zone, nodeId));
  }

  public int nodeIdx() {
    if (nodeIdx == null) {
      throw new IllegalStateException("No nodeIdx in this memberId: " + this);
    }
    return nodeIdx;
  }

  public @Nullable String zone() {
    return zone;
  }

  /**
   * @return {@code true} if this member id belongs to the given zone.
   */
  public boolean isInZone(final @Nullable String zone) {
    return Objects.equals(this.zone, zone);
  }

  private static @Nullable Integer validateNodeIdx(final @Nullable Integer nodeIdx) {
    if (nodeIdx != null && nodeIdx < 0) {
      throw new IllegalArgumentException("Expected nodeIdx to be >= 0, but got " + nodeIdx);
    }
    return nodeIdx;
  }

  private static @Nullable String validateZone(final @Nullable String zone) {
    if (zone != null && zone.isBlank()) {
      throw new IllegalArgumentException("Expected zone to be non-empty, but was empty");
    }
    return zone != null ? zone.strip() : null;
  }

  private static String buildMemberIdString(final @Nullable String zone, final int nodeIdx) {
    if (zone == null) {
      return Integer.toString(nodeIdx);
    } else {
      return validateZone(zone) + "/" + nodeIdx;
    }
  }

  private static @Nullable Integer tryParseInt(final String s) {
    try {
      return Integer.parseInt(s);
    } catch (final NumberFormatException e) {
      return null;
    }
  }
}
