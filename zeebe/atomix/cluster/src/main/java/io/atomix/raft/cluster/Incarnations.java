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
package io.atomix.raft.cluster;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/** Incarnations of members, see {@link RaftMember#getIncarnation()}. */
public final class Incarnations {

  private Incarnations() {}

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
}
