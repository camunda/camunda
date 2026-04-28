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

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Controller cluster identity. */
public class MemberId extends NodeId {

  public MemberId(final String id) {
    super(id);
  }

  /**
   * Creates a new cluster node identifier from the specified string.
   *
   * @return node id
   */
  public static MemberId anonymous() {
    return new MemberId(UUID.randomUUID().toString());
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
   * <p>When {@code zone} is {@code null} or blank the result is the bare form {@code "$nodeId"};
   * otherwise it is {@code "$zone/$nodeId"}.
   */
  public static MemberId from(final @Nullable String zone, final int nodeId) {
    final var normalizedZone = zone == null ? null : zone.strip();
    if (normalizedZone == null || normalizedZone.isEmpty()) {
      return new MemberId(Integer.toString(nodeId));
    }
    return new MemberId(normalizedZone + "/" + nodeId);
  }

  /**
   * Extracts the numeric node id from a {@link MemberId}.
   *
   * <p>Accepts both the bare form ({@code "0"}) and the zone-prefixed form ({@code "us-east/0"}).
   * The numeric portion is always the trailing path segment.
   *
   * @throws NumberFormatException if the trailing segment is not a parseable int
   */
  public static int extractNodeId(final MemberId memberId) {
    final var id = memberId.id();
    final var slash = id.lastIndexOf('/');
    return Integer.parseInt(slash >= 0 ? id.substring(slash + 1) : id);
  }

  /**
   * Returns {@code true} if this member id belongs to the given zone, i.e. the id starts with
   * {@code "$zone/"}. Always returns {@code true} when {@code zone} is {@code null}.
   */
  public boolean isInZone(final @Nullable String zone) {
    if (zone == null) {
      return !id().contains("/");
    }
    return id().startsWith(zone + "/");
  }
}
