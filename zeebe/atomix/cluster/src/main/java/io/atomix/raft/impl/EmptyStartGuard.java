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
package io.atomix.raft.impl;

/**
 * Keeps a member that joined with an empty log from voting until it has caught up with a current
 * leader.
 *
 * <p>Such a member may still be a voter in the configuration, but it lost its log and its record of
 * its last vote: if it voted, a leader without the committed entries could be elected. While the
 * guard is active, the member grants no polls or votes and does not start an election. The guard is
 * released once the local commit index reaches the commit index of an append from a leader whose
 * term is at least {@code probeTerm}, the highest term that the voters reported before the member
 * joined. A leader with a lower term may be a deposed leader whose commit index is too low.
 *
 * <p>Not thread safe; used on the Raft thread only.
 */
final class EmptyStartGuard {

  private static final long UNKNOWN = -1;

  private final long probeTerm;
  private long targetCommitIndex = UNKNOWN;
  private boolean active = true;

  EmptyStartGuard(final long probeTerm) {
    this.probeTerm = probeTerm;
  }

  boolean isActive() {
    return active;
  }

  long probeTerm() {
    return probeTerm;
  }

  /**
   * Observes an append from the leader of {@code leaderTerm}; the first one from a leader with a
   * term of at least {@code probeTerm} sets the commit index to reach.
   *
   * @return true if the guard was released by this call
   */
  boolean onAppendFromLeader(
      final long leaderTerm, final long leaderCommitIndex, final long localCommitIndex) {
    if (active && targetCommitIndex == UNKNOWN && leaderTerm >= probeTerm) {
      targetCommitIndex = leaderCommitIndex;
    }
    return onCommit(localCommitIndex);
  }

  /**
   * @return true if the guard was released by this call
   */
  boolean onCommit(final long localCommitIndex) {
    if (active && targetCommitIndex != UNKNOWN && localCommitIndex >= targetCommitIndex) {
      active = false;
      return true;
    }
    return false;
  }
}
