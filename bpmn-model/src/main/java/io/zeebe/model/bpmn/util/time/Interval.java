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

import java.time.Duration;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/** Combines {@link java.time.Period}, and {@link java.time.Duration} */
public class Interval {
  private final Period period;
  private final Duration duration;

  public Interval(Period period, Duration duration) {
    this.period = period;
    this.duration = duration;
  }

  public Period getPeriod() {
    return period;
  }

  public Duration getDuration() {
    return duration;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof Interval)) {
      return false;
    }

    final Interval interval = (Interval) o;
    return Objects.equals(getPeriod(), interval.getPeriod())
        && Objects.equals(getDuration(), interval.getDuration());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getPeriod(), getDuration());
  }

  @Override
  public String toString() {
    if (period.isZero()) {
      return duration.toString();
    }

    if (duration.isZero()) {
      return period.toString();
    }

    return period.toString() + duration.toString().substring(1);
  }

  /**
   * Only supports a subset of ISO8601, combining both period and duration.
   *
   * @param text ISO8601 conforming interval expression
   * @return parsed interval
   */
  public static Interval parse(String text) {
    final int timeOffset = text.lastIndexOf("T");
    final Period period;
    final Duration duration;

    // to remain consistent with normal duration parsing which requires a duration to start with P
    if (text.charAt(0) != 'P') {
      throw new DateTimeParseException("Must start with P", text, 0);
    }

    if (timeOffset > 0) {
      duration = Duration.parse(String.format("P%S", text.substring(timeOffset)));
    } else {
      duration = Duration.ZERO;
    }

    if (timeOffset == -1) {
      period = Period.parse(text);
    } else if (timeOffset > 1) {
      period = Period.parse(text.substring(0, timeOffset));
    } else {
      period = Period.ZERO;
    }

    return new Interval(period, duration);
  }
}
