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
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class SimpleVoteQuorum implements VoteQuorum {
  private Consumer<Boolean> callback;
  private boolean complete;
  private final Set<MemberId> members;
  private int succeeded;
  private int failed;
  private final int quorum;

  /**
   * @param callback will be called with the result of the vote, either true or false.
   * @param members All members participating, including the local member.
   */
  public SimpleVoteQuorum(final Consumer<Boolean> callback, final Collection<MemberId> members) {
    this.callback = callback;
    this.members = new HashSet<>(members);
    quorum = members.size() / 2 + 1;
  }

  @Override
  public void succeed(final MemberId member) {
    if (members.remove(member)) {
      succeeded++;
      checkComplete();
    }
  }

  @Override
  public void fail(final MemberId member) {
    if (members.remove(member)) {
      failed++;
      checkComplete();
    }
  }

  /**
   * Cancels the quorum. Once this method has been called, the quorum will be marked complete and
   * the handler will never be called.
   */
  @Override
  public void cancel() {
    callback = null;
    complete = true;
  }

  private void checkComplete() {
    if (!complete && callback != null) {
      if (succeeded >= quorum) {
        complete = true;
        callback.accept(true);
      } else if (failed >= quorum) {
        complete = true;
        callback.accept(false);
      }
    }
  }
}
