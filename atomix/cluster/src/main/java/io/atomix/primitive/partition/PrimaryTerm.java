/*
 * Copyright 2017-present Open Networking Foundation
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
package io.atomix.primitive.partition;

import static com.google.common.base.MoreObjects.toStringHelper;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Partition primary term.
 *
 * <p>The primary term represents a single instance of a unique primary for a partition. Every term
 * must have a unique {@link #term()} number, and term numbers must be monotonically increasing,
 * though not necessarily sequential. The {@link #candidates()} should either list the set of
 * non-{@link #primary() primary} members in order of priority such that the default {@link
 * #backups(int)} implementation can properly select backups or else {@link #backups(int)} should be
 * overridden.
 */
public class PrimaryTerm {
  private final long term;
  private final GroupMember primary;
  private final List<GroupMember> candidates;

  public PrimaryTerm(
      final long term, final GroupMember primary, final List<GroupMember> candidates) {
    this.term = term;
    this.primary = primary;
    this.candidates = candidates;
  }

  /**
   * Returns the primary term number.
   *
   * <p>The term number is monotonically increasing and guaranteed to be unique for a given {@link
   * #primary()}. No two primaries may ever have the same term.
   *
   * @return the primary term number
   */
  public long term() {
    return term;
  }

  /**
   * Returns the primary member.
   *
   * <p>The primary is the node through which writes are replicated in the primary-backup protocol.
   *
   * @return the primary member
   */
  public GroupMember primary() {
    return primary;
  }

  /**
   * Returns the list of members.
   *
   * <p>The candidate list represents the list of members that are participating in the election but
   * not necessarily in replication. This list is used to select a set of {@link #backups(int)
   * backups} based on a primitive configuration.
   *
   * @return the list of members
   */
  public List<GroupMember> candidates() {
    return candidates;
  }

  /**
   * Returns an ordered list of backup members.
   *
   * <p>The backups are populated from the set of {@link #candidates()} based on order and group
   * information. The list of backups is guaranteed not to contain any duplicate {@link
   * MemberGroup}s unless not enough groups exist to satisfy the number of backups.
   *
   * @param numBackups the number of backups to return
   * @return an ordered list of backup members
   */
  public List<GroupMember> backups(final int numBackups) {
    if (primary == null || candidates.isEmpty()) {
      return Collections.emptyList();
    }
    return candidates.subList(1, Math.min(candidates.size(), numBackups + 1));
  }

  @Override
  public int hashCode() {
    return Objects.hash(term, primary, candidates);
  }

  @Override
  public boolean equals(final Object object) {
    if (object instanceof PrimaryTerm) {
      final PrimaryTerm term = (PrimaryTerm) object;
      return term.term == this.term
          && Objects.equals(term.primary, primary)
          && Objects.equals(term.candidates, candidates);
    }
    return false;
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("term", term)
        .add("primary", primary)
        .add("candidates", candidates)
        .toString();
  }
}
