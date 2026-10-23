/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.util.Loggers;
import java.util.concurrent.TimeUnit;
import org.agrona.DeadlineTimerWheel;
import org.agrona.collections.Long2ObjectHashMap;
import org.slf4j.Logger;

public final class ActorTimerQueue extends DeadlineTimerWheel {
  private static final Logger LOG = Loggers.ACTOR_LOGGER;
  private static final int DEFAULT_TICKS_PER_WHEEL = 32;
  private static final int MAX_EXPIRED_TIMERS_PER_CALL = 256;
  private final Long2ObjectHashMap<TimerSubscription> timerJobMap = new Long2ObjectHashMap<>();
  // one poll scans one spoke, so a full revolution is needed to visit every timer that is due.
  // Bounds the work done per call, so a large clock jump cannot starve the actor tasks of the
  // thread
  private final int maxPollsPerCall;
  private long lastPolledMillis = Long.MIN_VALUE;

  private final TimerHandler timerHandler =
      (timeUnit, now, timerId) -> {
        final TimerSubscription timer = timerJobMap.remove(timerId);

        if (timer != null) {
          timer.onTimerExpired(timeUnit, now);
        } else {
          LOG.warn("Timer with id {} expired but is not known in this timer queue.", timerId);
        }

        return true;
      };

  public ActorTimerQueue(final ActorClock clock) {
    this(clock, DEFAULT_TICKS_PER_WHEEL);
  }

  public ActorTimerQueue(final ActorClock clock, final int ticksPerWheel) {
    super(TimeUnit.MILLISECONDS, clock.getTimeMillis(), 1, ticksPerWheel);
    maxPollsPerCall = ticksPerWheel;
  }

  public void processExpiredTimers(final ActorClock clock) {
    final long now = clock.getTimeMillis();
    if (now == lastPolledMillis) {
      // the clock has millisecond resolution, so there is nothing new to expire; polling would only
      // rescan the current spoke, which grows with the number of timers
      return;
    }
    lastPolledMillis = now;

    // poll advances the wheel by at most one tick, so keep polling until it has caught up with now
    int expiredTimers = 0;
    for (int polls = 0; polls < maxPollsPerCall; polls++) {
      final long tickTime = currentTickTime();
      expiredTimers += poll(now, timerHandler, MAX_EXPIRED_TIMERS_PER_CALL - expiredTimers);

      if (expiredTimers >= MAX_EXPIRED_TIMERS_PER_CALL) {
        break;
      } else if (currentTickTime() == tickTime) {
        return;
      }
    }

    if (expiredTimers < MAX_EXPIRED_TIMERS_PER_CALL) {
      // a full revolution visited every spoke, so no timer with a deadline <= now is left and the
      // wheel can skip straight to now instead of catching up over many calls
      currentTickTime(now);
      return;
    }

    // yielding before every due timer fired, so the next call must continue even if the clock is
    // still at the same millisecond
    lastPolledMillis = Long.MIN_VALUE;
  }

  public void schedule(final TimerSubscription timer, final ActorClock now) {
    final long deadline = timer.getDeadline(now);
    if (deadline <= lastPolledMillis) {
      // already due, it must not wait for the clock to change
      lastPolledMillis = Long.MIN_VALUE;
    }

    final long timerId = scheduleTimer(deadline);
    timer.setTimerId(timerId);
    if (timerJobMap.containsKey(timerId)) {
      throw new IllegalStateException(
          "Failed scheduling, timer with id "
              + timerId
              + " already exists: "
              + timerJobMap.get(timerId));
    }

    timerJobMap.put(timerId, timer);
  }

  public void remove(final TimerSubscription timer) {
    final long timerId = timer.getTimerId();

    timerJobMap.remove(timerId);
    cancelTimer(timerId);
  }
}
