/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched;

import io.zeebe.util.sched.clock.ActorClock;
import java.util.concurrent.TimeUnit;
import org.agrona.DeadlineTimerWheel;
import org.agrona.collections.Long2ObjectHashMap;

public class ActorTimerQueue extends DeadlineTimerWheel {
  private static final int DEFAULT_TICKS_PER_WHEEL = 32;
  private final Long2ObjectHashMap<TimerSubscription> timerJobMap = new Long2ObjectHashMap<>();

  private final TimerHandler timerHandler =
      new TimerHandler() {
        @Override
        public boolean onTimerExpiry(TimeUnit timeUnit, long now, long timerId) {
          final TimerSubscription timer = timerJobMap.remove(timerId);

          if (timer != null) {
            timer.onTimerExpired(timeUnit, now);
          }

          return true;
        }
      };

  public ActorTimerQueue(ActorClock clock) {
    this(clock, DEFAULT_TICKS_PER_WHEEL);
  }

  public ActorTimerQueue(ActorClock clock, int ticksPerWheel) {
    super(TimeUnit.MILLISECONDS, clock.getTimeMillis(), 1, ticksPerWheel);
  }

  public void processExpiredTimers(ActorClock clock) {
    int timersProcessed = 0;

    do {
      timersProcessed = poll(clock.getTimeMillis(), timerHandler, Integer.MAX_VALUE);
    } while (timersProcessed > 0);
  }

  public void schedule(TimerSubscription timer, ActorClock now) {
    final long deadline =
        now.getTimeMillis() + timeUnit().convert(timer.getDeadline(), timer.getTimeUnit());

    final long timerId = scheduleTimer(deadline);
    timer.setTimerId(timerId);

    timerJobMap.put(timerId, timer);
  }

  public void remove(TimerSubscription timer) {
    final long timerId = timer.getTimerId();

    timerJobMap.remove(timerId);
    cancelTimer(timerId);
  }
}
