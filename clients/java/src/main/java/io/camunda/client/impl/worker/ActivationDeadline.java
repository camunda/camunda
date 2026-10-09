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
 *
 * <p>A deadline takes the clock and the timeout rather than a pre-baked verdict, so that a test can
 * lie about <em>time</em> — by moving the clock it injects — but not about the answer, which the
 * arithmetic here alone decides.
 */
final class ActivationDeadline {

  /** The longest timeout a nanosecond clock can measure, which is a little over 292 years. */
  static final Duration LONGEST_MEASURABLE_TIMEOUT = Duration.ofNanos(Long.MAX_VALUE);

  private final LongSupplier nanoClock;
  private final long startNanos;
  private final long deadlineNanos;
  private final boolean measurable;

  private ActivationDeadline(
      final LongSupplier nanoClock,
      final long startNanos,
      final long timeoutNanos,
      final boolean measurable) {
    this.nanoClock = nanoClock;
    this.startNanos = startNanos;
    deadlineNanos = startNanos + timeoutNanos;
    this.measurable = measurable;
  }

  /**
   * A deadline for a job that arrives now, from the current reading of the clock against the given
   * timeout.
   *
   * @param nanoClock a monotonic clock, in nanoseconds, such as {@link System#nanoTime()}
   * @param jobTimeout the timeout the job was activated with. One longer than {@link
   *     #LONGEST_MEASURABLE_TIMEOUT} is treated as a deadline that never passes rather than being
   *     rejected, since such a timeout opens a worker and is sent to the broker as milliseconds
   *     without complaint. A timeout no nanosecond clock can express asks for a job that stays the
   *     worker's for as long as it runs.
   */
  static ActivationDeadline startingNow(final LongSupplier nanoClock, final Duration jobTimeout) {
    final boolean measurable = jobTimeout.compareTo(LONGEST_MEASURABLE_TIMEOUT) <= 0;
    final long timeoutNanos = measurable ? jobTimeout.toNanos() : 0L;
    return new ActivationDeadline(nanoClock, nanoClock.getAsLong(), timeoutNanos, measurable);
  }

  /** Whether the worker has had the job for longer than the activation it arrived with lasts. */
  boolean hasPassed() {
    // A timeout too long for a nanosecond clock never passes: it would otherwise wrap round after
    // Long.MAX_VALUE nanoseconds — about 292 years — and drop a job that its timeout says is still
    // the worker's, rather than leaving it be as the timeout asks.
    // The comparison is a difference rather than a direct one, since a nanosecond clock is allowed
    // to wrap.
    return measurable && nanoClock.getAsLong() - deadlineNanos >= 0;
  }

  /** How long the worker has had the job, for the record when it is dropped for having waited. */
  Duration elapsed() {
    return Duration.ofNanos(nanoClock.getAsLong() - startNanos);
  }
}
