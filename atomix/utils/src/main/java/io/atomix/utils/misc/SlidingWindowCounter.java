/*
 * Copyright 2015-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.utils.misc;

import static com.google.common.base.Preconditions.checkArgument;

import io.atomix.utils.concurrent.Scheduled;
import io.atomix.utils.concurrent.SingleThreadContext;
import io.atomix.utils.concurrent.ThreadContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maintains a sliding window of value counts. The sliding window counter is initialized with a
 * number of window slots. Calls to #incrementCount() will increment the value in the current window
 * slot. Periodically the window slides and the oldest value count is dropped. Calls to #get() will
 * get the total count for the last N window slots.
 */
public final class SlidingWindowCounter {
  private static final int SLIDE_WINDOW_PERIOD_SECONDS = 1;
  private final Logger log = LoggerFactory.getLogger(getClass());
  private volatile int headSlot;
  private final int windowSlots;
  private final List<AtomicLong> counters;
  private final Scheduled schedule;

  public SlidingWindowCounter(final int windowSlots) {
    this(windowSlots, new SingleThreadContext("sliding-window-counter-%d"));
  }

  /**
   * Creates a new sliding window counter with the given total number of window slots.
   *
   * @param windowSlots total number of window slots
   */
  public SlidingWindowCounter(final int windowSlots, final ThreadContext context) {
    checkArgument(windowSlots > 0, "Window size must be a positive integer");

    this.windowSlots = windowSlots;
    this.headSlot = 0;

    // Initialize each item in the list to an AtomicLong of 0
    this.counters =
        Collections.nCopies(windowSlots, 0).stream()
            .map(AtomicLong::new)
            .collect(Collectors.toCollection(ArrayList::new));
    this.schedule =
        context.schedule(0, SLIDE_WINDOW_PERIOD_SECONDS, TimeUnit.SECONDS, this::advanceHead);
  }

  /** Releases resources used by the SlidingWindowCounter. */
  public void destroy() {
    schedule.cancel();
  }

  /** Increments the count of the current window slot by 1. */
  public void incrementCount() {
    incrementCount(headSlot, 1);
  }

  /**
   * Increments the count of the current window slot by the given value.
   *
   * @param value value to increment by
   */
  public void incrementCount(final long value) {
    incrementCount(headSlot, value);
  }

  private void incrementCount(final int slot, final long value) {
    counters.get(slot).addAndGet(value);
  }

  /**
   * Gets the total count for the last N window slots.
   *
   * @param slots number of slots to include in the count
   * @return total count for last N slots
   */
  public long get(final int slots) {
    checkArgument(
        slots <= windowSlots, "Requested window must be less than the total window slots");

    long sum = 0;

    for (int i = 0; i < slots; i++) {
      int currentIndex = headSlot - i;
      if (currentIndex < 0) {
        currentIndex = counters.size() + currentIndex;
      }
      sum += counters.get(currentIndex).get();
    }

    return sum;
  }

  void advanceHead() {
    counters.get(slotAfter(headSlot)).set(0);
    headSlot = slotAfter(headSlot);
  }

  private int slotAfter(final int slot) {
    return (slot + 1) % windowSlots;
  }
}
