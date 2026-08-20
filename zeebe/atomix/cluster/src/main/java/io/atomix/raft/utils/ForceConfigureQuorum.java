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

public final class ForceConfigureQuorum {
  private Consumer<Boolean> callback;
  private boolean complete;
  private final Set<MemberId> members;
  private int succeeded;
  private int failed;
  private final int quorum;
  private final int acceptedFailures;

  /**
   * @param callback will be called when all members have acknowledged success to this request or
   *     when atleast one failed.
   * @param members All members excluding the local member
   */
  public ForceConfigureQuorum(
      final Consumer<Boolean> callback, final Collection<MemberId> members) {
    this.callback = callback;
    this.members = new HashSet<>(members);
    // Need to include all members to ensure that the member with up-to-date log is included
    quorum = members.size();
    acceptedFailures = 0;
  }

  public void succeed(final MemberId member) {
    if (members.remove(member)) {
      succeeded++;
      checkComplete();
    }
  }

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
  public void cancel() {
    callback = null;
    complete = true;
  }

  private void checkComplete() {
    if (!complete && callback != null) {
      if (succeeded >= quorum) {
        complete = true;
        callback.accept(true);
      } else if (failed > acceptedFailures) {
        complete = true;
        callback.accept(false);
      }
    }
  }
}
