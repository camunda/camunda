/*
 * Copyright 2016-present Open Networking Foundation
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
package io.atomix.raft.protocol;

import io.atomix.raft.RaftError;
import io.atomix.raft.cluster.FenceEntry;
import io.atomix.raft.cluster.RaftMember;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Server configuration change response. */
public class ReconfigureResponse extends ConfigurationResponse {

  // Absent (null) in responses of members that predate these fields.
  private final @Nullable Collection<RaftMember> oldMembers;
  private final boolean committed;
  private final @Nullable String leader;

  public ReconfigureResponse(
      final Status status,
      final RaftError error,
      final long index,
      final long term,
      final long timestamp,
      final Collection<RaftMember> members) {
    this(status, error, index, term, timestamp, members, null, 0, null, false, null);
  }

  public ReconfigureResponse(
      final Status status,
      final RaftError error,
      final long index,
      final long term,
      final long timestamp,
      final Collection<RaftMember> members,
      final Collection<FenceEntry> fenced,
      final long membershipVersion) {
    this(
        status,
        error,
        index,
        term,
        timestamp,
        members,
        fenced,
        membershipVersion,
        null,
        false,
        null);
  }

  public ReconfigureResponse(
      final Status status,
      final RaftError error,
      final long index,
      final long term,
      final long timestamp,
      final Collection<RaftMember> members,
      final @Nullable Collection<FenceEntry> fenced,
      final long membershipVersion,
      final @Nullable Collection<RaftMember> oldMembers,
      final boolean committed,
      final @Nullable String leader) {
    super(status, error, index, term, timestamp, members, fenced, membershipVersion);
    this.oldMembers = oldMembers;
    this.committed = committed;
    this.leader = leader;
  }

  /**
   * @return the id of the leader that answered a read of its configuration, or null
   */
  public @Nullable String leader() {
    return leader;
  }

  /**
   * @return the old members while a joint change is in progress, else empty
   */
  public Collection<RaftMember> oldMembers() {
    return oldMembers == null ? List.of() : oldMembers;
  }

  /**
   * @return true if the leader has committed the configuration of this response
   */
  public boolean committed() {
    return committed;
  }

  /**
   * Returns a new reconfigure response builder.
   *
   * @return A new reconfigure response builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Reconfigure response builder. */
  public static class Builder extends ConfigurationResponse.Builder<Builder, ReconfigureResponse> {

    private @Nullable Collection<RaftMember> oldMembers;
    private boolean committed;
    private @Nullable String leader;

    public Builder withLeader(final String leader) {
      this.leader = leader;
      return this;
    }

    public Builder withOldMembers(final Collection<RaftMember> oldMembers) {
      // Beware that immutable collections cannot be serialized by Kryo
      this.oldMembers = new ArrayList<>(oldMembers);
      return this;
    }

    public Builder withCommitted(final boolean committed) {
      this.committed = committed;
      return this;
    }

    @Override
    public ReconfigureResponse build() {
      validate();
      return new ReconfigureResponse(
          status,
          error,
          index,
          term,
          timestamp,
          members,
          fenced,
          membershipVersion,
          oldMembers,
          committed,
          leader);
    }
  }
}
