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

import static io.camunda.zeebe.util.MemberIdUtil.validateZone;

import io.camunda.zeebe.util.MemberIdUtil;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Controller cluster identity. */
@NullMarked
public class MemberId extends NodeId {

  /**
   * Comparator that orders {@link MemberId} instances numerically by {@link #nodeIdx}, using {@link
   * #zone} as a secondary key and the raw id string as a final stable tie-breaker. This avoids the
   * lexicographic pitfalls of the inherited {@link NodeId#compareTo} (e.g. "10" < "2") and should
   * be preferred wherever a deterministic ordering of members is required.
   *
   * <p>Members without a {@code nodeIdx} (e.g. anonymous members) sort after all indexed members.
   * Members without a {@code zone} sort before zoned members that share the same {@code nodeIdx}.
   */
  public static final Comparator<MemberId> ID_COMPARATOR =
      Comparator.<MemberId, Integer>comparing(
              m -> m.nodeIdx, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(m -> m.zone, Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(MemberId::id);

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
    // The underscore separator is safe because validateZone forbids underscores in zone names.
    final int sep = id.lastIndexOf('_');
    final Integer suffixIdx = sep > 0 ? tryParseInt(id.substring(sep + 1)) : null;
    if (suffixIdx != null) {
      zone = id.substring(0, sep);
      nodeIdx = suffixIdx;
    } else {
      zone = null;
      nodeIdx = tryParseInt(id);
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
   * it is {@code "$zone_$nodeId"}.
   */
  public static MemberId from(final @Nullable String zone, final int nodeId) {
    return new MemberId(zone, nodeId, buildMemberIdString(zone, nodeId));
  }

  @VisibleForTesting
  public static MemberId from(final int nodeId) {
    return from(null, nodeId);
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

  private static String buildMemberIdString(final @Nullable String zone, final int nodeIdx) {
    return MemberIdUtil.memberIdString(zone, nodeIdx);
  }

  private static @Nullable Integer tryParseInt(final String s) {
    try {
      return Integer.parseInt(s);
    } catch (final NumberFormatException e) {
      return null;
    }
  }
}
