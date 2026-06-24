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
import java.util.function.Consumer;

public class JointConsensusVoteQuorum implements VoteQuorum {
  private final SimpleVoteQuorum oldQuorum;
  private final SimpleVoteQuorum newQuorum;
  private final Consumer<Boolean> callback;
  private State oldState = State.Pending;
  private State newState = State.Pending;

  private boolean completed;

  public JointConsensusVoteQuorum(
      final Consumer<Boolean> callback,
      final Collection<MemberId> oldMembers,
      final Collection<MemberId> newMembers) {
    oldQuorum = new SimpleVoteQuorum(this::finishOldQuorum, oldMembers);
    newQuorum = new SimpleVoteQuorum(this::finishNewQuorum, newMembers);
    this.callback = callback;
  }

  private void finishOldQuorum(final Boolean succeeded) {
    if (succeeded) {
      oldState = State.Succeeded;
    } else {
      oldState = State.Failed;
    }
    checkComplete();
  }

  private void finishNewQuorum(final Boolean succeeded) {
    if (succeeded) {
      newState = State.Succeeded;
    } else {
      newState = State.Failed;
    }
    checkComplete();
  }

  private void checkComplete() {
    if (completed) {
      return;
    }

    if (oldState == State.Succeeded && newState == State.Succeeded) {
      completed = true;
      callback.accept(true);
    } else if (oldState == State.Failed || newState == State.Failed) {
      completed = true;
      callback.accept(false);
    }
  }

  @Override
  public void succeed(final MemberId member) {
    oldQuorum.succeed(member);
    newQuorum.succeed(member);
  }

  @Override
  public void fail(final MemberId member) {
    oldQuorum.fail(member);
    newQuorum.fail(member);
  }

  @Override
  public void cancel() {
    completed = true;
    oldQuorum.cancel();
    newQuorum.cancel();
  }

  enum State {
    Pending,
    Succeeded,
    Failed
  }
}
