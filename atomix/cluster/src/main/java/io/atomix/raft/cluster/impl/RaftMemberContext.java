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

import io.atomix.raft.storage.log.RaftLog;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.storage.journal.JournalReader.Mode;
import java.nio.ByteBuffer;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.slf4j.LoggerFactory;

/** Cluster member state. */
public final class RaftMemberContext {

  private static final int MAX_APPENDS = 2;
  private static final int APPEND_WINDOW_SIZE = 8;
  private final DefaultRaftMember member;
  private final DescriptiveStatistics timeStats = new DescriptiveStatistics(APPEND_WINDOW_SIZE);
  private long term;
  private long configIndex;
  private long snapshotIndex;
  private long nextSnapshotIndex;
  private ByteBuffer nextSnapshotChunk;
  private long matchIndex;
  private long heartbeatTime;
  private long responseTime;
  private int appending;
  private boolean appendSucceeded;
  private long appendTime;
  private boolean configuring;
  private boolean installing;
  private int failures;
  private long failureTime;
  private volatile RaftLogReader reader;

  RaftMemberContext(final DefaultRaftMember member, final RaftClusterContext cluster) {
    this.member = checkNotNull(member, "member cannot be null").setCluster(cluster);
  }

  /** Resets the member state. */
  public void resetState(final RaftLog log) {
    snapshotIndex = 0;
    nextSnapshotIndex = 0;
    nextSnapshotChunk = null;
    matchIndex = 0;
    heartbeatTime = 0;
    responseTime = 0;
    appending = 0;
    timeStats.clear();
    configuring = false;
    installing = false;
    appendSucceeded = false;
    failures = 0;
    failureTime = 0;

    switch (member.getType()) {
      case PASSIVE:
        reader = log.openReader(log.writer().getLastIndex() + 1, Mode.COMMITS);
        break;
      case PROMOTABLE:
      case ACTIVE:
        reader = log.openReader(log.writer().getLastIndex() + 1, Mode.ALL);
        break;
      default:
        LoggerFactory.getLogger(RaftMemberContext.class)
            .error("ResetState: No case for Member type {}", member.getType());
        break;
    }
  }

  /**
   * Returns a boolean indicating whether an append request can be sent to the member.
   *
   * @return Indicates whether an append request can be sent to the member.
   */
  public boolean canAppend() {
    return appending == 0
        || (appendSucceeded
            && appending < MAX_APPENDS
            && System.currentTimeMillis() - (timeStats.getMean() / MAX_APPENDS) >= appendTime);
  }

  /**
   * Returns whether a heartbeat can be sent to the member.
   *
   * @return Indicates whether a heartbeat can be sent to the member.
   */
  public boolean canHeartbeat() {
    return appending == 0;
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
    this.appendSucceeded = succeeded;
  }

  /** Flags the last append to the member is failed. */
  public void appendFailed() {
    appendSucceeded(false);
  }

  /** Starts an append request to the member. */
  public void startAppend() {
    appending++;
    appendTime = System.currentTimeMillis();
  }

  /** Completes an append request to the member. */
  public void completeAppend() {
    appending--;
  }

  /**
   * Completes an append request to the member.
   *
   * @param time The time in milliseconds for the append.
   */
  public void completeAppend(final long time) {
    appending--;
    timeStats.addValue(time);
  }

  /**
   * Returns a boolean indicating whether a configure request can be sent to the member.
   *
   * @return Indicates whether a configure request can be sent to the member.
   */
  public boolean canConfigure() {
    return !configuring;
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
    return !installing;
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
    final RaftLogReader reader = this.reader;
    return toStringHelper(this)
        .add("member", member.memberId())
        .add("term", term)
        .add("configIndex", configIndex)
        .add("snapshotIndex", snapshotIndex)
        .add("nextSnapshotIndex", nextSnapshotIndex)
        .add("nextSnapshotChunk", nextSnapshotChunk)
        .add("matchIndex", matchIndex)
        .add("nextIndex", reader != null ? reader.getNextIndex() : matchIndex + 1)
        .add("heartbeatTime", heartbeatTime)
        .add("appending", appending)
        .add("appendSucceeded", appendSucceeded)
        .add("appendTime", appendTime)
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
   * Returns the member log reader.
   *
   * @return The member log reader.
   */
  public RaftLogReader getLogReader() {
    return reader;
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
  public void setNextSnapshotChunk(final ByteBuffer nextSnapshotChunk) {
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
}
