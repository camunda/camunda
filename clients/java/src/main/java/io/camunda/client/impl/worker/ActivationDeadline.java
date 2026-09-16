/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.impl.worker;

import java.time.Duration;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * How long a job the worker has taken on is still the worker's to run. Once it has passed, the
 * broker may have taken the job back and offered it to somebody else, so running it risks doing the
 * same work twice and having the completion rejected.
 *
 * <p>The broker's own deadline, which the job carries, is an instant on the broker's wall clock.
 * Comparing it against the client's wall clock would make every job look expired on a client whose
 * clock runs ahead of the broker's, and none at all on one that lags. So the wait is measured
 * locally instead, on a monotonic clock, from the moment the job reached the client against the
 * timeout it was activated with. That cannot be thrown off by a clock difference. What it does not
 * count is the time the activation spent in flight, which errs towards running a job rather than
 * dropping one.
 */
@FunctionalInterface
interface ActivationDeadline {

  /** The longest timeout a nanosecond clock can measure, which is a little over 292 years. */
  Duration LONGEST_MEASURABLE_TIMEOUT = Duration.ofNanos(Long.MAX_VALUE);

  /** Whether the worker has had the job for longer than the activation it arrived with lasts. */
  boolean hasPassed();

  /**
   * Deadlines for jobs as they arrive, each one starting when it is asked for.
   *
   * @param nanoClock a monotonic clock, in nanoseconds, such as {@link System#nanoTime()}
   * @param jobTimeout the timeout the jobs are activated with. One longer than {@link
   *     #LONGEST_MEASURABLE_TIMEOUT} is capped to it rather than rejected, since such a timeout
   *     opens a worker and is sent to the broker as milliseconds without complaint. Capping it
   *     makes the deadline one that never passes, which is what a timeout that long asks for.
   */
  static Supplier<ActivationDeadline> startingOnReceipt(
      final LongSupplier nanoClock, final Duration jobTimeout) {
    final long timeoutNanos =
        jobTimeout.compareTo(LONGEST_MEASURABLE_TIMEOUT) > 0
            ? Long.MAX_VALUE
            : jobTimeout.toNanos();
    return () -> {
      final long deadlineNanos = nanoClock.getAsLong() + timeoutNanos;
      // compared as a difference rather than directly, since a nanosecond clock is allowed to wrap
      return () -> nanoClock.getAsLong() - deadlineNanos >= 0;
    };
  }
}
