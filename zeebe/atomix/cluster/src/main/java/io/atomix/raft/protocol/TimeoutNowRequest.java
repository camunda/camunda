/*
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
package io.atomix.raft.protocol;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.cluster.MemberId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * TimeoutNow request.
 *
 * <p>This instructs the recipient to immediately start an election (bypassing its election
 * timeout), to enable deliberate leadership transfers. Should only be sent by the current leader -
 * carries the leader's {@code term} and {@code leader} id so the recipient can reject a stale
 * request. It also carries the leader's exporter positions, which the recipient hands to its own
 * broker before starting the election.
 */
@NullMarked
public final class TimeoutNowRequest extends AbstractRaftRequest {

  private final long term;
  private final MemberId leader;
  // null rather than empty when there are none, which is also what a node without this field reads
  private final @Nullable List<ExporterPosition> exporterPositions;

  private TimeoutNowRequest(
      final long term, final MemberId leader, final List<ExporterPosition> exporterPositions) {
    this.term = term;
    this.leader = leader;
    // an ArrayList, as that's what the Raft protocol namespace registers
    this.exporterPositions =
        exporterPositions.isEmpty() ? null : new ArrayList<>(exporterPositions);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The term of the leader that requested the transfer. */
  public long term() {
    return term;
  }

  /** The leader that requested the transfer. */
  public MemberId leader() {
    return leader;
  }

  /** The exporter positions the leader hands over to the recipient; empty if there are none. */
  public List<ExporterPosition> exporterPositions() {
    return exporterPositions == null ? List.of() : exporterPositions;
  }

  @Override
  public MemberId from() {
    return leader;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), term, leader, exporterPositions);
  }

  @Override
  public boolean equals(final @Nullable Object object) {
    if (this == object) {
      return true;
    }
    if (object == null || getClass() != object.getClass()) {
      return false;
    }
    final TimeoutNowRequest other = (TimeoutNowRequest) object;
    return term == other.term
        && leader.equals(other.leader)
        && Objects.equals(exporterPositions, other.exporterPositions);
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("term", term)
        .add("leader", leader)
        .add("exporterPositions", exporterPositions)
        .toString();
  }

  /** TimeoutNow request builder. */
  public static class Builder extends AbstractRaftRequest.Builder<Builder, TimeoutNowRequest> {

    private long term;
    private @Nullable MemberId leader;
    private List<ExporterPosition> exporterPositions = List.of();

    public Builder withTerm(final long term) {
      this.term = term;
      return this;
    }

    public Builder withLeader(final MemberId leader) {
      this.leader = checkNotNull(leader, "leader cannot be null");
      return this;
    }

    public Builder withExporterPositions(final List<ExporterPosition> exporterPositions) {
      this.exporterPositions = checkNotNull(exporterPositions, "exporterPositions cannot be null");
      return this;
    }

    @Override
    protected void validate() {
      super.validate();
      checkNotNull(leader, "leader cannot be null");
    }

    @Override
    public TimeoutNowRequest build() {
      validate();
      return new TimeoutNowRequest(term, checkNotNull(leader), exporterPositions);
    }
  }
}
