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
 * limitations under the License.
 */
package io.atomix.raft.cluster.impl;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.raft.storage.log.RaftLogReader;
import io.camunda.zeebe.snapshots.SnapshotChunkReader;
import java.nio.ByteBuffer;
import org.slf4j.LoggerFactory;

/** Cluster member state. */
public final class RaftMemberContext {

  private static final int APPEND_WINDOW_SIZE = 8;
  private final DefaultRaftMember member;
  private final int maxAppendsPerMember;
  private boolean open = true;
  private long term;
  private long configIndex;
  private long snapshotIndex;
  private long nextSnapshotIndex;
  private ByteBuffer nextSnapshotChunk;
  private long matchIndex;
  private long heartbeatTime;
  private long responseTime;
  private int inFlightAppendCount;
  private boolean appendSucceeded;
  private boolean configuring;
  private boolean installing;
  private int failures;
  private long failureTime;
  private volatile RaftLogReader reader;
  private SnapshotChunkReader snapshotChunkReader;
  private IndexedRaftLogEntry currentEntry;

  RaftMemberContext(
      final DefaultRaftMember member,
      final RaftClusterContext cluster,
      final int maxAppendsPerMember) {
    this.member = checkNotNull(member, "member cannot be null").setCluster(cluster);
    this.maxAppendsPerMember = maxAppendsPerMember;
  }

  /** Resets the member state. */
  public void resetState(final RaftLog log) {
    snapshotIndex = 0;
    nextSnapshotIndex = 0;
    nextSnapshotChunk = null;
    matchIndex = 0;
    heartbeatTime = 0;
    responseTime = 0;
    inFlightAppendCount = 0;
    configuring = false;
    installing = false;
    appendSucceeded = false;
    failures = 0;
    failureTime = 0;

    if (reader != null) {
      closeReader();
      openReader(log);
    }
  }

  public void close() {
    open = false;
    member.close();
    closeReader();
  }

  public boolean isOpen() {
    return open;
  }

  private void closeReader() {
    if (reader != null) {
      reader.close();
      reader = null;
    }
  }

  public boolean hasReplicationContext() {
    return reader != null;
  }

  public void openReplicationContext(final RaftLog log) {
    if (!open) {
      throw new IllegalStateException("Member context already closed");
    }
    resetState(log);
    openReader(log);
  }

  public void closeReplicationContext() {
    closeReader();
  }

  private void openReader(final RaftLog log) {
    switch (member.getType()) {
      case PASSIVE:
        reader = log.openCommittedReader();
        resetReaderAtEndOfLog(reader);
        break;
      case PROMOTABLE:
      case ACTIVE:
        reader = log.openUncommittedReader();
        resetReaderAtEndOfLog(reader);
        break;
      default:
        LoggerFactory.getLogger(RaftMemberContext.class)
            .error("ResetState: No case for Member type {}", member.getType());
        break;
    }
  }

  private void resetReaderAtEndOfLog(final RaftLogReader reader) {
    reader.seekToLast();
    if (reader.hasNext()) {
      currentEntry = reader.next();
    }
  }

  /**
   * Returns a boolean indicating whether an append request can be sent to the member.
   *
   * @return Indicates whether an append request can be sent to the member.
   */
  public boolean canAppend() {
    return open
        && (inFlightAppendCount == 0
            || (appendSucceeded && inFlightAppendCount < maxAppendsPerMember));
  }

  /**
   * Returns whether a heartbeat can be sent to the member.
   *
   * @return Indicates whether a heartbeat can be sent to the member.
   */
  public boolean canHeartbeat() {
    return open && inFlightAppendCount == 0;
  }

  /** Flags the last append to the member as successful. */
  public void appendSucceeded() {
    appendSucceeded(true);
  }

  /**
   * Sets whether the last append to the member succeeded.
   *
   * @param succeeded Whether the last append to the member succeeded.
   */
  private void appendSucceeded(final boolean succeeded) {
    appendSucceeded = succeeded;
  }

  /** Flags the last append to the member is failed. */
  public void appendFailed() {
    appendSucceeded(false);
  }

  /** Starts an append request to the member. */
  public void startAppend() {
    inFlightAppendCount++;
  }

  /** Completes an append request to the member. */
  public void completeAppend() {
    inFlightAppendCount--;
  }

  /**
   * Returns a boolean indicating whether a configure request can be sent to the member.
   *
   * @return Indicates whether a configure request can be sent to the member.
   */
  public boolean canConfigure() {
    return open && !configuring;
  }

  /** Starts a configure request to the member. */
  public void startConfigure() {
    configuring = true;
  }

  /** Completes a configure request to the member. */
  public void completeConfigure() {
    configuring = false;
  }

  /**
   * Returns a boolean indicating whether an install request can be sent to the member.
   *
   * @return Indicates whether an install request can be sent to the member.
   */
  public boolean canInstall() {
    return open && !installing;
  }

  /** Starts an install request to the member. */
  public void startInstall() {
    installing = true;
  }

  /** Completes an install request to the member. */
  public void completeInstall() {
    installing = false;
  }

  /**
   * Increments the member failure count.
   *
   * @return The member state.
   */
  public int incrementFailureCount() {
    if (failures++ == 0) {
      failureTime = System.currentTimeMillis();
    }
    return failures;
  }

  /** Resets the member failure count. */
  public void resetFailureCount() {
    failures = 0;
    failureTime = 0;
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("member", member.memberId())
        .add("term", term)
        .add("configIndex", configIndex)
        .add("snapshotIndex", snapshotIndex)
        .add("nextSnapshotIndex", nextSnapshotIndex)
        .add("nextSnapshotChunk", nextSnapshotChunk)
        .add("matchIndex", matchIndex)
        .add("heartbeatTime", heartbeatTime)
        .add("appending", inFlightAppendCount)
        .add("appendSucceeded", appendSucceeded)
        .add("configuring", configuring)
        .add("installing", installing)
        .add("failures", failures)
        .toString();
  }

  /**
   * Returns the member configuration index.
   *
   * @return The member configuration index.
   */
  public long getConfigIndex() {
    return configIndex;
  }

  /**
   * Sets the member configuration index.
   *
   * @param configIndex The member configuration index.
   */
  public void setConfigIndex(final long configIndex) {
    this.configIndex = configIndex;
  }

  /**
   * Returns the member term.
   *
   * @return The member term.
   */
  public long getConfigTerm() {
    return term;
  }

  /**
   * Sets the member term.
   *
   * @param term The member term.
   */
  public void setConfigTerm(final long term) {
    this.term = term;
  }

  /**
   * Returns the member failure count.
   *
   * @return The member failure count.
   */
  public int getFailureCount() {
    return failures;
  }

  /**
   * Returns the member failure time.
   *
   * @return the member failure time
   */
  public long getFailureTime() {
    return failureTime;
  }

  /**
   * Returns the member heartbeat time.
   *
   * @return The member heartbeat time.
   */
  public long getHeartbeatTime() {
    return heartbeatTime;
  }

  /**
   * Sets the member heartbeat time.
   *
   * @param heartbeatTime The member heartbeat time.
   */
  public void setHeartbeatTime(final long heartbeatTime) {
    this.heartbeatTime = Math.max(this.heartbeatTime, heartbeatTime);
  }

  /**
   * Returns the member's match index.
   *
   * @return The member's match index.
   */
  public long getMatchIndex() {
    return matchIndex;
  }

  /**
   * Sets the member's match index.
   *
   * @param matchIndex The member's match index.
   */
  public void setMatchIndex(final long matchIndex) {
    checkArgument(matchIndex >= 0, "matchIndex must be positive");
    this.matchIndex = matchIndex;
  }

  /**
   * Returns the member.
   *
   * @return The member.
   */
  public DefaultRaftMember getMember() {
    return member;
  }

  /**
   * Returns the member's next snapshot index.
   *
   * @return The member's next snapshot index.
   */
  public long getNextSnapshotIndex() {
    return nextSnapshotIndex;
  }

  /**
   * Sets the member's next snapshot index.
   *
   * @param nextSnapshotIndex The member's next snapshot index.
   */
  public void setNextSnapshotIndex(final long nextSnapshotIndex) {
    this.nextSnapshotIndex = nextSnapshotIndex;
  }

  /**
   * Returns the member's next expected snapshot chunk ID.
   *
   * @return The member's next expected chunk ID.
   */
  public ByteBuffer getNextSnapshotChunk() {
    return nextSnapshotChunk;
  }

  /**
   * Sets the member's next expected snapshot chunk ID.
   *
   * @param nextSnapshotChunk The member's next expected snapshot chunk ID.
   */
  public void setNextSnapshotChunkId(final ByteBuffer nextSnapshotChunk) {
    this.nextSnapshotChunk = nextSnapshotChunk;
  }

  /**
   * Returns the member response time.
   *
   * @return The member response time.
   */
  public long getResponseTime() {
    return responseTime;
  }

  /**
   * Sets the member response time.
   *
   * @param responseTime The member response time.
   */
  public void setResponseTime(final long responseTime) {
    this.responseTime = Math.max(this.responseTime, responseTime);
  }

  /**
   * Returns the member's current snapshot index.
   *
   * @return The member's current snapshot index.
   */
  public long getSnapshotIndex() {
    return snapshotIndex;
  }

  /**
   * Sets the member's current snapshot index.
   *
   * @param snapshotIndex The member's current snapshot index.
   */
  public void setSnapshotIndex(final long snapshotIndex) {
    this.snapshotIndex = snapshotIndex;
  }

  public SnapshotChunkReader getSnapshotChunkReader() {
    return snapshotChunkReader;
  }

  public void setSnapshotChunkReader(final SnapshotChunkReader snapshotChunkReader) {
    this.snapshotChunkReader = snapshotChunkReader;
  }

  public boolean hasNextEntry() {
    return reader.hasNext();
  }

  public IndexedRaftLogEntry nextEntry() {
    currentEntry = reader.next();
    return currentEntry;
  }

  public IndexedRaftLogEntry getCurrentEntry() {
    return currentEntry;
  }

  public long getCurrentIndex() {
    return currentEntry != null ? currentEntry.index() : 0;
  }

  public void reset(final long index) {
    final var nextIndex = reader.seek(index - 1);
    if (nextIndex == index - 1) {
      currentEntry = reader.next();
    } else {
      currentEntry = null;
    }
  }
}
