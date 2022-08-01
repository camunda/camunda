/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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
  private final Long2ObjectHashMap<TimerSubscription> timerJobMap = new Long2ObjectHashMap<>();

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
  }

  public void processExpiredTimers(final ActorClock clock) {
    int timersProcessed = 0;

    do {
      timersProcessed = poll(clock.getTimeMillis(), timerHandler, Integer.MAX_VALUE);
    } while (timersProcessed > 0);
  }

  public void schedule(final TimerSubscription timer, final ActorClock now) {
    final long deadline =
        now.getTimeMillis() + timeUnit().convert(timer.getDeadline(), timer.getTimeUnit());

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
