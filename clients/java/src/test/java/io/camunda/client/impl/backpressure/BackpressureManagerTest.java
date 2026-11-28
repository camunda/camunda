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

import io.camunda.client.api.BackpressureProfile;
import io.camunda.client.impl.backpressure.BackpressureManager.Permit;
import io.camunda.client.impl.backpressure.BackpressureManager.Severity;
import io.camunda.client.impl.backpressure.BackpressureManager.State;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class BackpressureManagerTest {

  private final ManualTime time = new ManualTime(0);

  private BackpressureManager manager(final BackpressureProfile profile) {
    return manager(BackpressureSettings.forProfile(profile));
  }

  private BackpressureManager manager(final BackpressureSettings settings) {
    return new BackpressureManager(settings, time, time);
  }

  /** A limiter that is capped at one request, soft and without a delay between requests. */
  private BackpressureManager cappedAtOne() {
    final BackpressureManager manager =
        manager(BackpressureSettings.builder().initialMax(2).softFactor(0.5).maxWaiters(2).build());
    manager.recordBackpressure();
    assertThat(manager.state().permitsMax()).isEqualTo(1);
    assertThat(manager.state().backoffMs()).isZero();
    return manager;
  }

  private static void signal(final BackpressureManager manager, final int times) {
    for (int i = 0; i < times; i++) {
      manager.recordBackpressure();
    }
  }

  @Test
  void shouldNotLimitBeforeTheFirstSignal() {
    // given
    final BackpressureManager manager = manager(BackpressureProfile.BALANCED);

    // when
    final List<CompletableFuture<Permit>> acquired = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      acquired.add(manager.acquire());
    }

    // then
    assertThat(acquired).allSatisfy(f -> assertThat(f).isCompleted());
    final State state = manager.state();
    assertThat(state.permitsMax()).isNull();
    assertThat(state.severity()).isEqualTo(Severity.HEALTHY);
  }

  @Test
  void shouldCapConcurrencyAtTwelveOnFirstSignal() {
    // given
    final BackpressureManager manager = manager(BackpressureProfile.BALANCED);

    // when: the cap starts at 16 and is scaled by 0.7 right away
    manager.recordBackpressure();

    // then
    final State state = manager.state();
    assertThat(state.permitsMax()).isEqualTo(12);
    assertThat(state.severity()).isEqualTo(Severity.SOFT);
    assertThat(state.consecutive()).isEqualTo(1);
  }

  @Test
  void shouldScaleDownBySoftFactorThenSevereFactorDownToTheFloor() {
    // given
    final BackpressureManager manager = manager(BackpressureProfile.BALANCED);
    final List<Integer> caps = new ArrayList<>();
    final List<Severity> severities = new ArrayList<>();

    // when
    for (int i = 0; i < 7; i++) {
      manager.recordBackpressure();
      caps.add(manager.state().permitsMax());
      severities.add(manager.state().severity());
    }

    // then: ceil(16*0.7)=12, ceil(12*0.7)=9, then severe from the 3rd consecutive signal on with
    // ceil(9*0.5)=5, ceil(5*0.5)=3, ceil(3*0.5)=2, ceil(2*0.5)=1 and the floor holds
    assertThat(caps).containsExactly(12, 9, 5, 3, 2, 1, 1);
    assertThat(severities)
        .containsExactly(
            Severity.SOFT,
            Severity.SOFT,
            Severity.SEVERE,
            Severity.SEVERE,
            Severity.SEVERE,
            Severity.SEVERE,
            Severity.SEVERE);
  }

  @Test
  void shouldNotScaleBelowTheFloorOfTheProfile() {
    // given
    final BackpressureManager manager = manager(BackpressureProfile.AGGRESSIVE);

    // when
    signal(manager, 20);

    // then
    assertThat(manager.state().permitsMax()).isEqualTo(2);
  }

  @Test
  void shouldDelayEveryRequestAtTheFloorOfTheSevereState() {
    // given
    final BackpressureManager manager = manager(BackpressureProfile.BALANCED);
    signal(manager, 6); // the cap reaches the floor of 1 and the state is severe
    assertThat(manager.state().backoffMs()).isEqualTo(25);

    // when
    final CompletableFuture<Permit> acquired = manager.acquire();

    // then: the request does not compete before the delay is over
    assertThat(acquired).isNotDone();
    assertThat(time.pendingDelays()).containsExactly(25L);
    time.advance(24);
    assertThat(acquired).isNotDone();
    time.advance(1);
    assertThat(acquired).isCompleted();
  }

  @Test
  void shouldDoubleTheDelayOnEverySignalAtTheFloorUpToTheCap() {
    // given
    final BackpressureManager manager = manager(BackpressureProfile.BALANCED);
    signal(manager, 6);
    final List<Long> delays = new ArrayList<>();
    delays.add(manager.state().backoffMs());

    // when
    for (int i = 0; i < 9; i++) {
      manager.recordBackpressure();
      delays.add(manager.state().backoffMs());
    }

    // then
    assertThat(delays)
        .containsExactly(25L, 50L, 100L, 200L, 400L, 800L, 1600L, 2000L, 2000L, 2000L);
  }

  @Test
  void shouldClearTheDelayOnTheNextSuccess() {
    // given
    final BackpressureManager manager = manager(BackpressureProfile.BALANCED);
    signal(manager, 8);
    assertThat(manager.state().backoffMs()).isPositive();

    // when
    manager.recordSuccess();

    // then
    assertThat(manager.state().backoffMs()).isZero();
  }

  @Test
  void shouldQueueRequestsBeyondTheCapAndAdmitThemInOrder() {
    // given: a cap of 1
    final BackpressureManager manager = cappedAtOne();
    final Permit first = manager.acquire().join();

    // when
    final CompletableFuture<Permit> second = manager.acquire();
    final CompletableFuture<Permit> third = manager.acquire();

    // then
    assertThat(second).isNotDone();
    assertThat(third).isNotDone();
    assertThat(manager.state().waiters()).isEqualTo(2);

    first.release();
    assertThat(second).isCompleted();
    assertThat(third).isNotDone();

    second.join().release();
    assertThat(third).isCompleted();
    assertThat(manager.state().waiters()).isZero();
  }

  @Test
  void shouldRejectRequestsWhenTheQueueIsFull() {
    // given: a cap of 1 and room for two waiting requests
    final BackpressureManager manager = cappedAtOne();
    manager.acquire().join(); // takes the only permit
    final CompletableFuture<Permit> waiting1 = manager.acquire();
    final CompletableFuture<Permit> waiting2 = manager.acquire();

    // when
    final CompletableFuture<Permit> rejected = manager.acquire();

    // then
    assertThat(waiting1).isNotDone();
    assertThat(waiting2).isNotDone();
    assertThat(rejected).isCompletedExceptionally();
    assertThatThrownBy(rejected::get)
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(BackpressureQueueFullException.class)
        .hasMessageContaining("2 requests are already waiting");
  }

  @Test
  void shouldWithdrawACancelledRequestFromTheQueue() {
    // given
    final BackpressureManager manager = cappedAtOne();
    final Permit held = manager.acquire().join();
    final CompletableFuture<Permit> cancelled = manager.acquire();
    final CompletableFuture<Permit> waiting = manager.acquire();
    assertThat(manager.state().waiters()).isEqualTo(2);

    // when
    cancelled.cancel(false);

    // then
    assertThat(manager.state().waiters()).isEqualTo(1);
    held.release();
    assertThat(waiting).isCompleted();
  }

  @Test
  void shouldIgnoreADuplicateRelease() {
    // given: a cap of 1 with one permit held
    final BackpressureManager manager = cappedAtOne();
    final Permit held = manager.acquire().join();
    assertThat(manager.state().permitsCurrent()).isEqualTo(1);

    // when
    held.release();
    held.release();

    // then
    assertThat(manager.state().permitsCurrent()).isZero();
  }

  @Test
  void shouldNotCountARequestThatStartedWithoutLimitAgainstTheLimit() {
    // given: a request that started while there was no limit
    final BackpressureManager manager = manager(BackpressureProfile.BALANCED);
    final Permit unlimited = manager.acquire().join();
    manager.recordBackpressure();
    final Permit limited = manager.acquire().join();
    assertThat(manager.state().permitsCurrent()).isEqualTo(1);

    // when
    unlimited.release();

    // then: only the request that was counted gives a permit back
    assertThat(manager.state().permitsCurrent()).isEqualTo(1);
    limited.release();
    assertThat(manager.state().permitsCurrent()).isZero();
  }

  @Test
  void shouldRecoverAdditivelyThenMultiplicativelyThenLiftTheLimit() {
    // given
    final BackpressureManager manager = manager(BackpressureProfile.BALANCED);
    manager.recordBackpressure(); // t=0, cap 12, soft

    // when: the first check is made right away and finds the cluster not yet quiet
    manager.recordSuccess();
    // then
    assertThat(manager.state().permitsMax()).isEqualTo(13);

    // when: a check less than a recovery interval later does nothing
    time.advance(500);
    manager.recordSuccess();
    // then
    assertThat(manager.state().permitsMax()).isEqualTo(13);

    // when: one more step per interval up to the initial cap of 16
    time.advance(500); // t=1000
    manager.recordSuccess();
    time.advance(1000); // t=2000: quiet for 2000 ms, which is not yet more than the quiet period
    manager.recordSuccess();
    // then
    assertThat(manager.state().permitsMax()).isEqualTo(15);
    assertThat(manager.state().severity()).isEqualTo(Severity.SOFT);

    // when: quiet for more than the quiet period, the state is healthy again and the cap grows by
    // a factor of 1.5
    time.advance(1000); // t=3000
    manager.recordSuccess();
    // then
    assertThat(manager.state().severity()).isEqualTo(Severity.HEALTHY);
    assertThat(manager.state().consecutive()).isZero();
    assertThat(manager.state().permitsMax()).isEqualTo(23); // ceil(15 * 1.5)

    time.advance(1000);
    manager.recordSuccess();
    assertThat(manager.state().permitsMax()).isEqualTo(35); // ceil(23 * 1.5)

    // when: 30 s after the state became healthy at t=3000
    time.advance(28_000); // t=32000, still 1000 short
    manager.recordSuccess();
    assertThat(manager.state().permitsMax()).isNotNull();
    time.advance(1000); // t=33000
    manager.recordSuccess();

    // then
    assertThat(manager.state().permitsMax()).isNull();
  }

  @Test
  void shouldAdmitWaitingRequestsWhenTheLimitIsLifted() {
    // given: two requests hold the cap of two, a third one waits
    final BackpressureManager manager =
        manager(
            BackpressureSettings.builder()
                .initialMax(2)
                .softFactor(1.0)
                .healthyRecoveryMultiplier(1.0)
                .build());
    manager.recordBackpressure();
    manager.acquire().join();
    manager.acquire().join();
    final CompletableFuture<Permit> waiting = manager.acquire();
    assertThat(manager.state().permitsMax()).isEqualTo(2);
    assertThat(waiting).isNotDone();

    // when: the cluster stays quiet until the limit is lifted
    for (int i = 0; i < 40 && manager.state().permitsMax() != null; i++) {
      time.advance(1000);
      manager.recordSuccess();
    }

    // then
    assertThat(manager.state().permitsMax()).isNull();
    assertThat(waiting).isCompleted();
  }

  @Test
  void shouldDecayTheSevereStateInSteps() {
    // given
    final BackpressureManager manager = manager(BackpressureProfile.BALANCED);
    signal(manager, 4);
    assertThat(manager.state().severity()).isEqualTo(Severity.SEVERE);

    // when / then: severe turns soft, then healthy, one recovery interval apart
    time.advance(3000);
    manager.recordSuccess();
    assertThat(manager.state().severity()).isEqualTo(Severity.SOFT);
    assertThat(manager.state().consecutive()).isEqualTo(4);
    time.advance(1000);
    manager.recordSuccess();
    assertThat(manager.state().severity()).isEqualTo(Severity.HEALTHY);
    assertThat(manager.state().consecutive()).isZero();
  }

  @Test
  void shouldStartOverFromTheInitialCapAfterTheLimitWasLifted() {
    // given: a limit that was lifted again
    final BackpressureManager manager = manager(BackpressureProfile.BALANCED);
    manager.recordBackpressure();
    for (int i = 0; i < 40 && manager.state().permitsMax() != null; i++) {
      time.advance(1000);
      manager.recordSuccess();
    }
    assertThat(manager.state().permitsMax()).isNull();

    // when
    manager.recordBackpressure();

    // then
    assertThat(manager.state().permitsMax()).isEqualTo(12);
  }

  @Test
  void shouldObserveButNeverLimitWithTheLegacyProfile() {
    // given
    final BackpressureManager manager = manager(BackpressureProfile.LEGACY);

    // when
    signal(manager, 10);
    final List<CompletableFuture<Permit>> acquired = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      acquired.add(manager.acquire());
    }

    // then
    assertThat(acquired).allSatisfy(f -> assertThat(f).isCompleted());
    assertThat(manager.state().permitsMax()).isNull();
    assertThat(manager.state().severity()).isEqualTo(Severity.SEVERE);
    assertThat(manager.state().backoffMs()).isZero();
  }

  @Test
  void shouldIgnoreSignalsWhenDisabled() {
    // given
    final BackpressureManager manager = manager(BackpressureProfile.DISABLED);

    // when
    signal(manager, 10);

    // then
    assertThat(manager.state().severity()).isEqualTo(Severity.HEALTHY);
    assertThat(manager.state().permitsMax()).isNull();
    assertThat(manager.acquire()).isCompleted();
  }

  @Test
  void shouldFailWaitingRequestsWhenClosed() {
    // given
    final BackpressureManager manager = cappedAtOne();
    manager.acquire().join();
    final CompletableFuture<Permit> waiting = manager.acquire();

    // when
    manager.close();

    // then
    assertThat(waiting).isCompletedExceptionally();
    assertThat(manager.acquire()).isCompletedExceptionally();
  }

  @Test
  void shouldDropTheDelayOfACancelledRequest() {
    // given: a request that is cancelled while it waits for the delay at the floor
    final BackpressureManager manager = manager(BackpressureProfile.BALANCED);
    signal(manager, 6);
    final CompletableFuture<Permit> abandoned = manager.acquire();
    abandoned.cancel(false);

    // when
    time.advance(100);

    // then: nothing is held
    assertThat(manager.state().permitsCurrent()).isZero();
    assertThat(time.pendingDelays()).isEmpty();
  }

  @Test
  void shouldNotExceedTheCapUnderConcurrentUse() throws Exception {
    // given: a cap of 3, and many threads that take a permit, work and give it back
    final BackpressureManager manager =
        manager(BackpressureSettings.builder().initialMax(4).softFactor(0.7).build());
    manager.recordBackpressure(); // 4 -> 3
    assertThat(manager.state().permitsMax()).isEqualTo(3);
    final AtomicInteger running = new AtomicInteger();
    final AtomicInteger maxRunning = new AtomicInteger();
    final CountDownLatch done = new CountDownLatch(200);

    // when
    for (int i = 0; i < 200; i++) {
      manager
          .acquire()
          .thenAccept(
              permit -> {
                final int now = running.incrementAndGet();
                maxRunning.accumulateAndGet(now, Math::max);
                CompletableFuture.runAsync(
                    () -> {
                      running.decrementAndGet();
                      permit.release();
                      done.countDown();
                    });
              });
    }

    // then
    assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(maxRunning.get()).isLessThanOrEqualTo(3);
    assertThat(manager.state().permitsCurrent()).isZero();
    assertThat(manager.state().waiters()).isZero();
  }
}
