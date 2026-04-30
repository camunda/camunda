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
  private final @Nullable Integer nodeIdx;
  private final @Nullable String zone;

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
      nodeIdx = Integer.parseInt(parts[1]);
    } else if (parts.length == 1) {
      zone = null;
      nodeIdx = Integer.parseInt(parts[0]);
    } else {
      throw new IllegalArgumentException(
          "Expected id to be of the form $zone/$id or $id, but got " + id);
    }
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
    final String stripped = zone == null ? null : zone.strip();
    return new MemberId(stripped, nodeId, buildMemberIdString(stripped, nodeId));
  }

  public int nodeIdx() {
    if (nodeIdx == null) {
      throw new IllegalStateException("No nodeIdx in this memberId: " + this);
    }
    return nodeIdx;
  }

  /**
   * @return {@code true} if this member id belongs to the given zone.
   */
  public boolean isInZone(final @Nullable String zone) {
    return Objects.equals(this.zone, zone);
  }

  private @Nullable Integer validateNodeIdx(final @Nullable Integer nodeIdx) {
    if (nodeIdx != null && nodeIdx < 0) {
      throw new IllegalArgumentException("Expected nodeIdx to be >= 0, but got " + nodeIdx);
    }
    return nodeIdx;
  }

  private @Nullable String validateZone(final @Nullable String zone) {
    if (zone != null && zone.isBlank()) {
      throw new IllegalArgumentException("Expected zone to be non-empty, but got " + zone);
    }
    return zone != null ? zone.strip() : null;
  }

  private static String buildMemberIdString(final @Nullable String zone, final int nodeIdx) {
    if (zone == null) {
      return Integer.toString(nodeIdx);
    } else {
      return zone + "/" + nodeIdx;
    }
  }
}
