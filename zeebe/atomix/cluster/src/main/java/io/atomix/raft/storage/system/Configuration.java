/*
 * Copyright 2015-present Open Networking Foundation
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
 * limitations under the License
 */
package io.atomix.raft.storage.system;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a persisted server configuration.
 *
 * @param index The index is the index of the {@link
 *     io.atomix.raft.storage.log.entry.ConfigurationEntry ConfigurationEntry} which resulted in
 *     this configuration.
 * @param term The term is the term of the leader at the time the configuration change was
 *     committed.
 * @param time The time at which the configuration was committed.
 * @param newMembers The cluster membership for this configuration.
 * @param oldMembers The cluster membership for the previous configuration.
 * @param force True indicates, we are skipping joint consensus and force the configuration change
 *     without a quorum.
 */
public record Configuration(
    long index,
    long term,
    long time,
    Collection<RaftMember> newMembers,
    Collection<RaftMember> oldMembers,
    boolean force,
    long compactionBound) {

  public Configuration(
      final long index,
      final long term,
      final long time,
      final Collection<RaftMember> newMembers,
      final Collection<RaftMember> oldMembers,
      final boolean force,
      final long compactionBound) {
    checkArgument(time > 0, "time must be positive");
    checkNotNull(newMembers, "newMembers cannot be null");
    checkNotNull(oldMembers, "oldMembers cannot be null");

    this.index = index;
    this.term = term;
    this.time = time;
    this.newMembers = copyMembers(newMembers);
    this.oldMembers = copyMembers(oldMembers);
    this.force = force;
    this.compactionBound = compactionBound;

    if (force) {
      checkArgument(oldMembers.isEmpty(), "oldMembers must be empty when force is true");
      checkArgument(!newMembers.isEmpty(), "newMembers must not be empty when force is true");
    }
  }

  public Configuration(
      final long index,
      final long term,
      final long time,
      final Collection<RaftMember> newMembers,
      final Collection<RaftMember> oldMembers) {
    this(index, term, time, newMembers, oldMembers, false, -1);
  }

  public Configuration(
      final long index, final long term, final long time, final Collection<RaftMember> members) {
    this(index, term, time, members, Collections.emptyList(), false, -1);
  }

  public boolean requiresJointConsensus() {
    return !oldMembers.isEmpty();
  }

  /**
   * @return a set of all members in the configuration. During joint consensus where a member can be
   *     in both the old and new configuration, the member with the higher {@link RaftMember.Type
   *     type} is used.
   */
  public Set<RaftMember> allMembers() {
    return new HashSet<>(
        Stream.concat(newMembers.stream(), oldMembers.stream())
            .collect(
                Collectors.toMap(
                    RaftMember::memberId,
                    Function.identity(),
                    BinaryOperator.maxBy(Comparator.comparing(RaftMember::getType))))
            .values());
  }

  private static Collection<RaftMember> copyMembers(final Collection<RaftMember> members) {
    final var copied = ImmutableList.<RaftMember>builderWithExpectedSize(members.size());
    for (final var member : members) {
      copied.add(
          new DefaultRaftMember(member.memberId(), member.getType(), member.getLastUpdated()));
    }
    return copied.build();
  }

  public boolean hasMember(final MemberId member) {
    return allMembers().stream().anyMatch(m -> m.memberId().equals(member));
  }
}
