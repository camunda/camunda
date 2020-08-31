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
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalUnit;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Combines {@link java.time.Period}, and {@link java.time.Duration} */
public class Interval implements TemporalAmount {
  private static final Duration ACCURATE_DURATION_UPPER_BOUND = Duration.ofDays(1);

  private final List<TemporalUnit> units;
  private final Period period;
  private final Duration duration;

  public Interval(final Period period, final Duration duration) {
    this.period = period;
    this.duration = duration;
    units = new ArrayList<>();

    units.addAll(period.getUnits());
    units.addAll(duration.getUnits());
  }

  public Interval(final Duration duration) {
    this(Period.ZERO, duration);
  }

  public Interval(final Period period) {
    this(period, Duration.ZERO);
  }

  public Period getPeriod() {
    return period;
  }

  public Duration getDuration() {
    return duration;
  }

  public long toEpochMilli(final long fromEpochMilli) {
    if (!isCalendarBased()) {
      return fromEpochMilli + getDuration().toMillis();
    }

    final Instant start = Instant.ofEpochMilli(fromEpochMilli);
    final ZonedDateTime zoneAwareStart = ZonedDateTime.ofInstant(start, ZoneId.systemDefault());
    return zoneAwareStart.plus(this).toInstant().toEpochMilli();
  }

  /**
   * {@link Duration#get(TemporalUnit)} only accepts {@link ChronoUnit#SECONDS} and {@link
   * ChronoUnit#NANOS}, so for any other units, this call is delegated to {@link
   * Period#get(TemporalUnit)}, though it could easily be the other way around.
   *
   * @param unit the {@code TemporalUnit} for which to return the value
   * @return the long value of the unit
   * @throws UnsupportedTemporalTypeException if the unit is not supported
   */
  @Override
  public long get(final TemporalUnit unit) {
    if (unit == ChronoUnit.SECONDS || unit == ChronoUnit.NANOS) {
      return duration.get(unit);
    }

    return period.get(unit);
  }

  @Override
  public List<TemporalUnit> getUnits() {
    return units;
  }

  @Override
  public Temporal addTo(final Temporal temporal) {
    return temporal.plus(period).plus(duration);
  }

  @Override
  public Temporal subtractFrom(final Temporal temporal) {
    return temporal.minus(period).minus(duration);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getPeriod(), getDuration());
  }

  @Override
  public boolean equals(final Object o) {
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
  public String toString() {
    if (period.isZero()) {
      return duration.toString();
    }

    if (duration.isZero()) {
      return period.toString();
    }

    return period.toString() + duration.toString().substring(1);
  }

  private boolean isCalendarBased() {
    return !getPeriod().isZero() || getDuration().compareTo(ACCURATE_DURATION_UPPER_BOUND) >= 0;
  }

  /**
   * Only supports a subset of ISO8601, combining both period and duration.
   *
   * @param text ISO8601 conforming interval expression
   * @return parsed interval
   */
  public static Interval parse(final String text) {
    String sign = "";
    int startOffset = 0;

    if (text.startsWith("-")) {
      startOffset = 1;
      sign = "-";
    } else if (text.startsWith("+")) {
      startOffset = 1;
    }

    final int durationOffset = text.indexOf('T');
    if (durationOffset == -1) {
      return new Interval(Period.parse(text), Duration.ZERO);
    } else if (durationOffset == startOffset + 1) {
      return new Interval(Period.ZERO, Duration.parse(text));
    }

    return new Interval(
        Period.parse(text.substring(0, durationOffset)),
        Duration.parse(sign + "P" + text.substring(durationOffset)));
  }
}
