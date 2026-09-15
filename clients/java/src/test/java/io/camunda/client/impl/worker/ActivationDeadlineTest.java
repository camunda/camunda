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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

final class ActivationDeadlineTest {

  private static final Duration JOB_TIMEOUT = Duration.ofSeconds(30);

  private final TestNanoClock clock = new TestNanoClock();

  @Test
  void shouldNotHavePassedRightAway() {
    // given
    final ActivationDeadline deadline = ActivationDeadline.startingNow(clock, JOB_TIMEOUT);

    // when no time passes at all

    // then
    assertThat(deadline.hasPassed()).isFalse();
  }

  @Test
  void shouldNotHavePassedJustBeforeTheTimeoutElapses() {
    // given
    final ActivationDeadline deadline = ActivationDeadline.startingNow(clock, JOB_TIMEOUT);

    // when
    clock.advance(JOB_TIMEOUT.minusNanos(1));

    // then
    assertThat(deadline.hasPassed()).isFalse();
  }

  @Test
  void shouldHavePassedOnceTheTimeoutElapses() {
    // given
    final ActivationDeadline deadline = ActivationDeadline.startingNow(clock, JOB_TIMEOUT);

    // when the clock reaches the deadline exactly, which is the first moment the broker may hand
    // the job to somebody else
    clock.advance(JOB_TIMEOUT);

    // then
    assertThat(deadline.hasPassed()).isTrue();
  }

  @Test
  void shouldStayPassedOnceTheTimeoutElapsed() {
    // given
    final ActivationDeadline deadline = ActivationDeadline.startingNow(clock, JOB_TIMEOUT);

    // when
    clock.advance(JOB_TIMEOUT.multipliedBy(100));

    // then
    assertThat(deadline.hasPassed()).isTrue();
  }

  @Test
  void shouldMeasureEachJobFromItsOwnArrival() {
    // given a job that arrived when the worker started
    final ActivationDeadline first = ActivationDeadline.startingNow(clock, JOB_TIMEOUT);

    // when the next job arrives one whole timeout later
    clock.advance(JOB_TIMEOUT);
    final ActivationDeadline second = ActivationDeadline.startingNow(clock, JOB_TIMEOUT);

    // then only the one that has been waiting that long is out of time
    assertThat(first.hasPassed()).isTrue();
    assertThat(second.hasPassed()).isFalse();
  }

  @Test
  void shouldHavePassedWhenTheClockWrapsAround() {
    // given a clock that is about to run past Long.MAX_VALUE, which System.nanoTime() is allowed
    // to do: a deadline taken here wraps around to a negative value
    clock.set(Long.MAX_VALUE - JOB_TIMEOUT.toNanos() / 2);
    final ActivationDeadline deadline = ActivationDeadline.startingNow(clock, JOB_TIMEOUT);

    // when the timeout elapses across the wrap
    clock.advance(JOB_TIMEOUT);

    // then the wrap does not make an expired job look fresh
    assertThat(deadline.hasPassed()).isTrue();
  }

  @Test
  void shouldNotHavePassedBeforeTheClockWrapsAround() {
    // given
    clock.set(Long.MAX_VALUE - JOB_TIMEOUT.toNanos() / 2);
    final ActivationDeadline deadline = ActivationDeadline.startingNow(clock, JOB_TIMEOUT);

    // when the clock wraps but the timeout has not elapsed yet
    clock.advance(JOB_TIMEOUT.minusNanos(1));

    // then the wrap does not make a fresh job look expired either
    assertThat(deadline.hasPassed()).isFalse();
  }

  @Test
  void shouldAcceptATimeoutTooLongToMeasureInNanoseconds() {
    // given a timeout beyond what a nanosecond clock can express, which the client accepts
    // everywhere else because it is sent to the broker as milliseconds
    final Duration beyondNanos = ActivationDeadline.LONGEST_MEASURABLE_TIMEOUT.plusDays(1);

    // when taking a deadline, which converting the timeout to nanoseconds would make throw

    // then
    assertThat(ActivationDeadline.startingNow(clock, beyondNanos).hasPassed()).isFalse();
  }

  @Test
  void shouldTreatATimeoutTooLongToMeasureAsOneThatNeverPasses() {
    // given a timeout beyond what a nanosecond clock can express
    final Duration beyondNanos = ActivationDeadline.LONGEST_MEASURABLE_TIMEOUT.plusDays(1);
    final ActivationDeadline deadline = ActivationDeadline.startingNow(clock, beyondNanos);

    // when the clock runs on past the point a capped timeout would have wrapped round into a
    // deadline that has already passed, which is a little over 292 years
    clock.advance(ActivationDeadline.LONGEST_MEASURABLE_TIMEOUT);
    clock.advance(Duration.ofDays(3_650));

    // then the job is still the worker's to run, as a timeout that long asks for
    assertThat(deadline.hasPassed()).isFalse();
  }

  @Test
  void shouldReportHowLongTheJobHasBeenWaiting() {
    // given a job that arrived when the worker started
    final ActivationDeadline deadline = ActivationDeadline.startingNow(clock, JOB_TIMEOUT);

    // when some time passes
    clock.advance(Duration.ofSeconds(7));

    // then the wait it reports is that time, measured from its own arrival
    assertThat(deadline.elapsed()).isEqualTo(Duration.ofSeconds(7));
  }

  /** A monotonic clock the test moves by hand, so that no test has to wait for real time. */
  private static final class TestNanoClock implements LongSupplier {
    private long nanos;

    @Override
    public long getAsLong() {
      return nanos;
    }

    private void set(final long nanos) {
      this.nanos = nanos;
    }

    private void advance(final Duration duration) {
      nanos += duration.toNanos();
    }
  }
}
