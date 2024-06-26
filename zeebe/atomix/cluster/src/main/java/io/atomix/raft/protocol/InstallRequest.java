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
package io.atomix.raft.protocol;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.cluster.MemberId;
import io.atomix.utils.misc.StringUtils;
import io.camunda.zeebe.snapshots.impl.SnapshotChunkId;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Server snapshot installation request.
 *
 * <p>Snapshot installation requests are sent by the leader to a follower when the follower
 * indicates that its log is further behind than the last snapshot taken by the leader. Snapshots
 * are sent in chunks, with each chunk being sent in a separate install request. As requests are
 * received by the follower, the snapshot is reconstructed based on the provided {@link #chunkId()}
 * and other metadata. The last install request will be sent with {@link #complete()} being {@code
 * true} to indicate that all chunks of the snapshot have been sent.
 */
public class InstallRequest extends AbstractRaftRequest {

  // the term of the node sending the install request
  private final long currentTerm;
  // the current leader (i.e. the node sending the request) at currentTerm
  private final MemberId leader;
  // the index associated to the snapshot
  private final long index;
  // the term associated to the snapshot
  private final long term;
  // the version of the snapshot
  private final int version;
  // the ID of the current chunk (implementation specific)
  private final ByteBuffer chunkId;
  // the next expected ID (or null if none)
  private final ByteBuffer nextChunkId;
  // the data of the chunk
  private final ByteBuffer data;
  // true if this is the first chunk
  private final boolean initial;
  // true if this is the last chunk
  private final boolean complete;

  public InstallRequest(
      final long currentTerm,
      final MemberId leader,
      final long index,
      final long term,
      final int version,
      final ByteBuffer chunkId,
      final ByteBuffer nextChunkId,
      final ByteBuffer data,
      final boolean initial,
      final boolean complete) {
    this.currentTerm = currentTerm;
    this.leader = leader;
    this.index = index;
    this.version = version;
    this.chunkId = chunkId;
    this.nextChunkId = nextChunkId;
    this.data = data;
    this.initial = initial;
    this.complete = complete;
    this.term = term;
  }

  /**
   * Returns a new install request builder.
   *
   * @return A new install request builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the requesting node's current term.
   *
   * @return The requesting node's current term.
   */
  public long currentTerm() {
    return currentTerm;
  }

  /**
   * Returns the term of the last applied entry in the snapshot.
   *
   * @return The snapshot term.
   */
  public long term() {
    return term;
  }

  /**
   * Returns the requesting leader address.
   *
   * @return The leader's address.
   */
  public MemberId leader() {
    return leader;
  }

  /**
   * Returns the snapshot index.
   *
   * @return The snapshot index.
   */
  public long index() {
    return index;
  }

  /**
   * Returns the id of the snapshot chunk.
   *
   * @return The id of the snapshot chunk.
   */
  public ByteBuffer chunkId() {
    return chunkId;
  }

  /**
   * Returns the ID of the next expected chunk; may be null
   *
   * @return the Id of the next expected chunk.
   */
  public ByteBuffer nextChunkId() {
    return nextChunkId;
  }

  /**
   * @return true if this is the first chunk of a snapshot
   */
  public boolean isInitial() {
    return initial;
  }

  /**
   * Returns the snapshot data.
   *
   * @return The snapshot data.
   */
  public ByteBuffer data() {
    return data;
  }

  /**
   * Returns a boolean value indicating whether this is the last chunk of the snapshot.
   *
   * @return Indicates whether this request is the last chunk of the snapshot.
   */
  public boolean complete() {
    return complete;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        currentTerm, leader, index, term, version, chunkId, nextChunkId, data, initial, complete);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final InstallRequest that = (InstallRequest) o;
    return currentTerm == that.currentTerm
        && index == that.index
        && term == that.term
        && version == that.version
        && initial == that.initial
        && complete == that.complete
        && Objects.equals(leader, that.leader)
        && Objects.equals(chunkId, that.chunkId)
        && Objects.equals(nextChunkId, that.nextChunkId)
        && Objects.equals(data, that.data);
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("currentTerm", currentTerm)
        .add("leader", leader)
        .add("index", index)
        .add("term", term)
        .add("version", version)
        .add("chunkId", new SnapshotChunkId(chunkId).toString())
        .add(
            "nextChunkId",
            nextChunkId == null ? "null" : new SnapshotChunkId(nextChunkId).toString())
        .add("data", StringUtils.printShortBuffer(data))
        .add("initial", initial)
        .add("complete", complete)
        .toString();
  }

  @Override
  public MemberId from() {
    return leader;
  }

  /** Snapshot request builder. */
  public static class Builder extends AbstractRaftRequest.Builder<Builder, InstallRequest> {

    private long currentTerm;
    private MemberId leader;
    private long index;
    private int version;
    private ByteBuffer chunkId;
    private ByteBuffer nextChunkId;
    private ByteBuffer data;
    private boolean complete;
    private boolean initial;
    private long term;

    /**
     * Sets the request current term.
     *
     * @param currentTerm The request current term.
     * @return The append request builder.
     * @throws IllegalArgumentException if the {@code currentTerm} is not positive
     */
    public Builder withCurrentTerm(final long currentTerm) {
      checkArgument(currentTerm > 0, "currentTerm must be positive");
      this.currentTerm = currentTerm;
      return this;
    }

    /**
     * Sets the request leader.
     *
     * @param leader The request leader.
     * @return The append request builder.
     * @throws IllegalArgumentException if the {@code leader} is not positive
     */
    public Builder withLeader(final MemberId leader) {
      this.leader = checkNotNull(leader, "leader cannot be null");
      return this;
    }

    public Builder withTerm(final long term) {
      checkArgument(term > 0, "term must be positive");
      this.term = term;
      return this;
    }

    /**
     * Sets the request index.
     *
     * @param index The request index.
     * @return The request builder.
     */
    public Builder withIndex(final long index) {
      checkArgument(index >= 0, "index must be positive");
      this.index = index;
      return this;
    }

    /**
     * Sets the request version.
     *
     * @param version the request version
     * @return the request builder
     */
    public Builder withVersion(final int version) {
      checkArgument(version > 0, "version must be positive");
      this.version = version;
      return this;
    }

    /**
     * Sets the request chunk ID.
     *
     * @param chunkId The request chunk ID.
     * @return The request builder.
     */
    public Builder withChunkId(final ByteBuffer chunkId) {
      checkNotNull(chunkId, "chunkId cannot be null");
      this.chunkId = chunkId;
      return this;
    }

    /**
     * Sets the request offset.
     *
     * @param nextChunkId The request offset.
     * @return The request builder.
     */
    public Builder withNextChunkId(final ByteBuffer nextChunkId) {
      this.nextChunkId = nextChunkId;
      return this;
    }

    /**
     * Sets the request snapshot bytes.
     *
     * @param data The snapshot bytes.
     * @return The request builder.
     */
    public Builder withData(final ByteBuffer data) {
      this.data = checkNotNull(data, "data cannot be null");
      return this;
    }

    /**
     * Sets whether the request is complete.
     *
     * @param complete Whether the snapshot is complete.
     * @return The request builder.
     * @throws NullPointerException if {@code member} is null
     */
    public Builder withComplete(final boolean complete) {
      this.complete = complete;
      return this;
    }

    /**
     * Sets whether this is the first chunk of a snapshot.
     *
     * @param initial whether this is the first chunk of a snapshot
     * @return the request builder
     */
    public Builder withInitial(final boolean initial) {
      this.initial = initial;
      return this;
    }

    /**
     * @throws IllegalStateException if member is null
     */
    @Override
    public InstallRequest build() {
      validate();
      return new InstallRequest(
          currentTerm, leader, index, term, version, chunkId, nextChunkId, data, initial, complete);
    }

    @Override
    protected void validate() {
      super.validate();
      checkArgument(currentTerm > 0, "term must be positive");
      checkNotNull(leader, "leader cannot be null");
      checkArgument(index >= 0, "index must be positive");
      checkArgument(term > 0, "snapshotTerm must be positive");
      checkNotNull(chunkId, "chunkId cannot be null");
      checkNotNull(data, "data cannot be null");
    }
  }
}
