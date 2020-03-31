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
package io.zeebe.model.bpmn.util.time;

import java.time.format.DateTimeParseException;
import java.util.Objects;

public class RepeatingInterval implements Timer {
  public static final String INTERVAL_DESGINATOR = "/";
  public static final int INFINITE = -1;

  private final int repetitions;
  private final Interval interval;

  public RepeatingInterval(final int repetitions, final Interval interval) {
    this.repetitions = repetitions;
    this.interval = interval;
  }

  @Override
  public Interval getInterval() {
    return interval;
  }

  @Override
  public int getRepetitions() {
    return repetitions;
  }

  @Override
  public long getDueDate(final long fromEpochMillis) {
    return getInterval().toEpochMilli(fromEpochMillis);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getRepetitions(), getInterval());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof RepeatingInterval)) {
      return false;
    }

    final RepeatingInterval repeatingInterval = (RepeatingInterval) o;

    return getRepetitions() == repeatingInterval.getRepetitions()
        && Objects.equals(getInterval(), repeatingInterval.getInterval());
  }

  public static RepeatingInterval parse(final String text) {
    return parse(text, INTERVAL_DESGINATOR);
  }

  /**
   * Parses a repeating interval as two parts, separated by a given interval designator.
   *
   * <p>The first part describes how often the interval should be repeated, and the second part is
   * the interval itself; see {@link Interval#parse(String)} for more on parsing the interval.
   *
   * <p>The repeating part is conform to the following format: R[0-9]*
   *
   * <p>If given an interval with, e.g. the interval designator is not present in the text, it is
   * assumed implicitly that the interval is meant to be repeated infinitely.
   *
   * @param text text to parse
   * @param intervalDesignator the separator between the repeating and interval texts
   * @return a RepeatingInterval based on the given text
   */
  public static RepeatingInterval parse(final String text, final String intervalDesignator) {
    if (!text.startsWith("R")) {
      throw new DateTimeParseException("Repetition spec must start with R", text, 0);
    }

    final int intervalDesignatorOffset = text.indexOf(intervalDesignator);
    if (intervalDesignatorOffset == -1) {
      throw new DateTimeParseException("No interval given", text, intervalDesignatorOffset);
    }

    if (intervalDesignatorOffset == 1) { // startsWith("R/")
      return new RepeatingInterval(INFINITE, Interval.parse(text.substring(2)));
    }

    try {
      return new RepeatingInterval(
          Integer.parseInt(text.substring(1, intervalDesignatorOffset)),
          Interval.parse(text.substring(intervalDesignatorOffset + 1)));
    } catch (final NumberFormatException e) {
      throw new DateTimeParseException("Cannot parse repetitions count", text, 1, e);
    }
  }
}
