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
package io.camunda.client.impl.backpressure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.impl.backpressure.BackpressureSignals.Failure;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

final class BackpressureCallTest {

  private final ManualTime time = new ManualTime(1_000);
  private double random = 0.999;

  private BackpressureController controller(final BackpressureSettings settings) {
    return new BackpressureController(settings, time, () -> random, time);
  }

  private BackpressureController controller() {
    return controller(BackpressureSettings.builder().build());
  }

  /** Runs the failures a request would suffer, and collects the delay chosen after each. */
  private static List<Long> delaysAfter(
      final BackpressureCall call, final int failures, final Failure kind) {
    final List<Long> delays = new ArrayList<>();
    for (int i = 0; i < failures; i++) {
      delays.add(call.onFailure(kind, 0));
    }
    return delays;
  }

  @Test
  void shouldTryThreeTimesInTotalByDefault() {
    // given
    final BackpressureCall call = controller().newCall(BackpressureScope.MANAGED, 0);

    // when
    final List<Long> delays = delaysAfter(call, 4, Failure.BACKPRESSURE);

    // then: two retries, then the failure stands
    assertThat(delays.subList(0, 2)).allSatisfy(d -> assertThat(d).isNotNegative());
    assertThat(delays.subList(2, 4)).containsExactly(-1L, -1L);
  }

  @Test
  void shouldDoubleTheBoundOfTheDelayOnEveryAttemptAndCapIt() {
    // given: more attempts than there are doublings before the cap
    final BackpressureCall call =
        controller(BackpressureSettings.builder().maxAttempts(9).build())
            .newCall(BackpressureScope.MANAGED, 0);
    random = 0.999999;

    // when
    final List<Long> delays = delaysAfter(call, 8, Failure.BACKPRESSURE);

    // then: just below 100, 200, 400, 800, 1600 and then 2000, the cap
    assertThat(delays).containsExactly(99L, 199L, 399L, 799L, 1599L, 1999L, 1999L, 1999L);
  }

  @Test
  void shouldRandomizeTheDelayOverTheWholeRangeUpToTheBound() {
    // given
    final BackpressureController controller = controller();

    // when / then
    random = 0;
    assertThat(controller.newCall(BackpressureScope.MANAGED, 0).onFailure(Failure.BACKPRESSURE, 0))
        .isZero();
    random = 0.5;
    assertThat(controller.newCall(BackpressureScope.MANAGED, 0).onFailure(Failure.BACKPRESSURE, 0))
        .isEqualTo(50L);
    random = 0.9999;
    assertThat(controller.newCall(BackpressureScope.MANAGED, 0).onFailure(Failure.BACKPRESSURE, 0))
        .isEqualTo(99L);
  }

  @Test
  void shouldKeepWithinTheBoundsForAnyRandomValue() {
    // given
    final BackpressureController controller = controller();

    // when / then
    for (double r = 0; r < 1; r += 0.01) {
      random = r;
      final BackpressureCall call = controller.newCall(BackpressureScope.MANAGED, 0);
      assertThat(call.onFailure(Failure.BACKPRESSURE, 0)).isBetween(0L, 99L);
      assertThat(call.onFailure(Failure.BACKPRESSURE, 0)).isBetween(0L, 199L);
    }
  }

  @Test
  void shouldNotRetryAPermanentFailureAndNotCountItAsASignal() {
    // given
    final BackpressureController controller = controller();
    final BackpressureCall call = controller.newCall(BackpressureScope.MANAGED, 0);

    // when
    final long delay = call.onFailure(Failure.PERMANENT, 0);

    // then
    assertThat(delay).isEqualTo(-1);
    assertThat(controller.manager().state().consecutive()).isZero();
  }

  @Test
  void shouldCountEverySignalEvenTheOneThatEndsTheRetries() {
    // given
    final BackpressureController controller = controller();
    final BackpressureCall call = controller.newCall(BackpressureScope.MANAGED, 0);

    // when: three rejections, the last one is not retried anymore
    delaysAfter(call, 3, Failure.BACKPRESSURE);

    // then
    assertThat(controller.manager().state().consecutive()).isEqualTo(3);
  }

  @Test
  void shouldWaitAtLeastAsLongAsTheClusterAsked() {
    // given
    final BackpressureCall call = controller().newCall(BackpressureScope.MANAGED, 0);
    random = 0;

    // when
    final long delay = call.onFailure(Failure.BACKPRESSURE, 1_500);

    // then
    assertThat(delay).isEqualTo(1_500);
  }

  @Test
  void shouldNotRetryWhenTheDelayDoesNotFitIntoTheRequestTimeout() {
    // given: a request timeout of 500 ms, of which 100 ms are used up
    final BackpressureCall call = controller().newCall(BackpressureScope.MANAGED, 500);
    time.advance(100);
    random = 0;

    // when / then: a delay of 400 ms leaves no time for the attempt; one of 399 ms does
    assertThat(call.remainingMillis()).isEqualTo(400);
    assertThat(call.onFailure(Failure.BACKPRESSURE, 400)).isEqualTo(-1);
    assertThat(call.onFailure(Failure.BACKPRESSURE, 399)).isEqualTo(399);
  }

  @Test
  void shouldNotRetryAfterTheRequestTimeoutElapsed() {
    // given
    final BackpressureCall call = controller().newCall(BackpressureScope.MANAGED, 300);
    time.advance(300);
    random = 0;

    // when
    final long delay = call.onFailure(Failure.BACKPRESSURE, 0);

    // then
    assertThat(delay).isEqualTo(-1);
  }

  @Test
  void shouldNotBoundTheRetriesWithoutRequestTimeout() {
    // given
    final BackpressureCall call = controller().newCall(BackpressureScope.MANAGED, 0);

    // then
    assertThat(call.remainingMillis()).isEqualTo(Long.MAX_VALUE);
    assertThat(call.onFailure(Failure.BACKPRESSURE, 60_000)).isEqualTo(60_000);
  }

  @Test
  void shouldTryOnceWhenOnlyOneAttemptIsAllowed() {
    // given: e.g. the channel retries on its own
    final BackpressureCall call =
        controller().newCall(BackpressureScope.MANAGED, 0, /* maxAttempts= */ 1);

    // then
    assertThat(call.onFailure(Failure.BACKPRESSURE, 0)).isEqualTo(-1);
  }

  @Test
  void shouldNotRetryARequestThatWasGivenUp() {
    // given
    final BackpressureCall call = controller().newCall(BackpressureScope.MANAGED, 0);
    call.release();

    // then
    assertThat(call.onFailure(Failure.BACKPRESSURE, 0)).isEqualTo(-1);
  }

  @Test
  void shouldHoldThePermitUntilTheRequestIsDone() {
    // given: a cap of one
    final BackpressureController controller = controller(cappedAtOne());
    controller.manager().recordBackpressure();
    final BackpressureCall first = controller.newCall(BackpressureScope.MANAGED, 0);
    final BackpressureCall second = controller.newCall(BackpressureScope.MANAGED, 0);

    // when
    final CompletableFuture<Void> firstTurn = first.acquire();
    final CompletableFuture<Void> secondTurn = second.acquire();

    // then: the second request waits for the first, including its retries
    assertThat(firstTurn).isCompleted();
    assertThat(secondTurn).isNotDone();
    first.onFailure(Failure.BACKPRESSURE, 0);
    assertThat(secondTurn).isNotDone();
    first.release();
    assertThat(secondTurn).isCompleted();
    second.release();
    assertThat(controller.manager().state().permitsCurrent()).isZero();
  }

  @Test
  void shouldWithdrawFromTheQueueWhenReleasedBeforeItWaitedForItsTurn() {
    // given: a cap of one that is used up
    final BackpressureController controller = controller(cappedAtOne());
    controller.manager().recordBackpressure();
    final BackpressureCall first = controller.newCall(BackpressureScope.MANAGED, 0);
    first.acquire();
    final BackpressureCall abandoned = controller.newCall(BackpressureScope.MANAGED, 0);

    // when: the request is given up before it asked for its turn
    abandoned.release();
    final CompletableFuture<Void> turn = abandoned.acquire();

    // then
    assertThat(turn).isCompletedExceptionally();
    assertThat(controller.manager().state().waiters()).isZero();
    first.release();
    assertThat(controller.manager().state().permitsCurrent()).isZero();
  }

  @Test
  void shouldNeverWaitForTheLimitWhenUngated() {
    // given: a cap of one that is used up
    final BackpressureController controller = controller(cappedAtOne());
    controller.manager().recordBackpressure();
    controller.newCall(BackpressureScope.MANAGED, 0).acquire();

    // when
    final BackpressureCall ungated = controller.newCall(BackpressureScope.UNGATED, 0);

    // then
    assertThat(ungated.acquire()).isCompleted();
    assertThat(controller.manager().state().permitsCurrent()).isEqualTo(1);
  }

  @Test
  void shouldFailARequestThatWaitedLongerThanItsTimeout() {
    // given
    final BackpressureController controller = controller(cappedAtOne());
    controller.manager().recordBackpressure();
    controller.newCall(BackpressureScope.MANAGED, 0).acquire();
    final BackpressureCall waiting = controller.newCall(BackpressureScope.MANAGED, 1_000);

    // when
    final CompletableFuture<Void> turn = waiting.acquire();
    time.advance(999);
    assertThat(turn).isNotDone();
    time.advance(1);

    // then
    assertThat(turn).isCompletedExceptionally();
    assertThatThrownBy(turn::get)
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(BackpressureTimeoutException.class);
    assertThat(controller.manager().state().waiters()).isZero();
  }

  @Test
  void shouldWithdrawFromTheQueueWhenReleasedWhileWaiting() {
    // given
    final BackpressureController controller = controller(cappedAtOne());
    controller.manager().recordBackpressure();
    final BackpressureCall first = controller.newCall(BackpressureScope.MANAGED, 0);
    first.acquire();
    final BackpressureCall waiting = controller.newCall(BackpressureScope.MANAGED, 0);
    final CompletableFuture<Void> turn = waiting.acquire();
    assertThat(controller.manager().state().waiters()).isEqualTo(1);

    // when
    waiting.release();
    first.release();

    // then
    assertThat(turn).isCompletedExceptionally();
    assertThat(controller.manager().state().waiters()).isZero();
    assertThat(controller.manager().state().permitsCurrent()).isZero();
  }

  @Test
  void shouldNotTouchUnmanagedRequestsOrRequestsOfADisabledClient() {
    // given
    final BackpressureController enabled = controller();
    final BackpressureController disabled =
        controller(BackpressureSettings.builder().enabled(false).gating(false).build());

    // then
    assertThat(enabled.newCall(BackpressureScope.UNMANAGED, 0)).isNull();
    assertThat(disabled.newCall(BackpressureScope.MANAGED, 0)).isNull();
    assertThat(enabled.newCall(BackpressureScope.MANAGED, 0)).isNotNull();
    assertThat(enabled.newCall(BackpressureScope.UNGATED, 0)).isNotNull();
  }

  private static BackpressureSettings cappedAtOne() {
    return BackpressureSettings.builder().initialMax(2).softFactor(0.5).build();
  }
}
