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
package io.atomix.raft;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/**
 * Options of a join, see {@link RaftServer#join(io.atomix.raft.cluster.RaftMember.Type,
 * java.util.Collection, JoinOptions)}.
 *
 * @param incarnation the incarnation of the local member. A member that starts without data uses a
 *     new incarnation, so that a fence entry of its previous incarnation does not apply to it.
 * @param configurationVersion an opaque version that the leader passes to its {@link
 *     JoinAdmission}, for example the version of the joiner's view of the cluster membership; 0 if
 *     unknown
 * @param emptyStartProbeTerm if not negative, the member joins with an empty log and keeps the
 *     empty-start guard until it caught up with a leader of at least this term: until then it
 *     grants no votes and starts no election. It is the highest term that the voters reported
 *     before the join.
 */
public record JoinOptions(long incarnation, long configurationVersion, long emptyStartProbeTerm) {

  public JoinOptions {
    if (incarnation == Long.MIN_VALUE) {
      throw new IllegalArgumentException(
          "incarnation must not be Long.MIN_VALUE, the null value of the log encoding");
    }
  }

  /**
   * @return a new random incarnation for a member that starts without data. It is never 0, the
   *     incarnation of a member of the initial configuration, and never {@link Long#MIN_VALUE}, the
   *     null value of the encoding of the log and the meta store, which reads as 0.
   */
  public static long newIncarnation() {
    return newIncarnation(ThreadLocalRandom.current()::nextLong);
  }

  static long newIncarnation(final LongSupplier random) {
    long incarnation;
    do {
      incarnation = random.getAsLong();
    } while (incarnation == 0 || incarnation == Long.MIN_VALUE);
    return incarnation;
  }

  public static JoinOptions withIncarnation(final long incarnation) {
    return new JoinOptions(incarnation, 0, -1);
  }

  public JoinOptions withConfigurationVersion(final long configurationVersion) {
    return new JoinOptions(incarnation, configurationVersion, emptyStartProbeTerm);
  }

  public JoinOptions withEmptyStartGuard(final long probeTerm) {
    if (probeTerm < 0) {
      throw new IllegalArgumentException("probeTerm must not be negative");
    }
    return new JoinOptions(incarnation, configurationVersion, probeTerm);
  }
}
