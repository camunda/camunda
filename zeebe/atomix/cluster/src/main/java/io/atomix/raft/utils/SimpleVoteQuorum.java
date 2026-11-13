/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.utils;

import io.atomix.cluster.MemberId;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public class SimpleVoteQuorum implements VoteQuorum {

  private static final Logger LOG = LoggerFactory.getLogger(SimpleVoteQuorum.class);
  private Consumer<Boolean> callback;
  private boolean complete;
  private final Set<MemberId> members;
  private int succeeded;
  private final int totalMembers;
  private final int quorum;
  private final Map<MemberId, VoteErrorStatus> failedStatuses;
  private final String name;

  /**
   * @param callback will be called with the result of the vote, either true or false.
   * @param members All members participating, including the local member.
   */
  public SimpleVoteQuorum(final Consumer<Boolean> callback, final Collection<MemberId> members) {
    this(callback, members, "Quorum");
  }

  /**
   * @param name identifies this quorum in failure reports, e.g. to tell the old and new
   *     configuration apart during joint consensus.
   */
  public SimpleVoteQuorum(
      final Consumer<Boolean> callback, final Collection<MemberId> members, final String name) {
    this.callback = callback;
    this.members = new HashSet<>(members);
    failedStatuses = new HashMap<>();
    totalMembers = members.size();
    quorum = members.size() / 2 + 1;
    this.name = name;
  }

  @Override
  public void succeed(final MemberId member) {
    if (members.remove(member)) {
      succeeded++;
      checkComplete();
    }
  }

  @Override
  public void fail(final MemberId member, final VoteErrorStatus status) {
    if (members.remove(member)) {
      failedStatuses.put(member, status);
      checkComplete();
    }
  }

  /**
   * Cancels the quorum. Once this method has been called, the quorum will be marked complete and
   * the handler will never be called.
   */
  @Override
  public void cancel() {
    close(reportLevel());
  }

  /**
   * Cancels the quorum whose result is no longer needed because the election is already decided,
   * e.g. the sibling configuration failed a joint election. Unlike {@link #cancel()}, the collected
   * member states are only logged at debug so a single election produces a single default-level
   * report.
   */
  void abandon() {
    close(Level.DEBUG);
  }

  private void close(final Level level) {
    if (!complete && !failedStatuses.isEmpty()) {
      LOG.atLevel(level)
          .log(
              "{} cancelled before completion with {}/{} failed members: {}, pending members: {}",
              name,
              failedStatuses.size(),
              totalMembers,
              failedStatuses,
              members);
    }
    callback = null;
    complete = true;
  }

  private void checkComplete() {
    if (!complete && callback != null) {
      if (succeeded >= quorum) {
        complete = true;
        callback.accept(true);
      } else if (failedStatuses.size() >= quorum) {
        complete = true;
        LOG.atLevel(reportLevel())
            .log(
                "{} failed with {}/{} failed members: {}",
                name,
                failedStatuses.size(),
                totalMembers,
                failedStatuses);
        callback.accept(false);
      }
    }
  }

  private Level reportLevel() {
    return failedStatuses.values().stream().allMatch(VoteErrorStatus::isProtocolOutcome)
        ? Level.DEBUG
        : Level.WARN;
  }
}
