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
package io.camunda.zeebe.model.bpmn.util.time;

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
import java.util.Optional;

/** Combines {@link java.time.Period}, and {@link java.time.Duration} */
public class Interval implements TemporalAmount {
  private static final Duration ACCURATE_DURATION_UPPER_BOUND = Duration.ofDays(1);

  private final List<TemporalUnit> units;
  private final Period period;
  private final Duration duration;
  private final Optional<ZonedDateTime> start;

  public Interval(final Period period, final Duration duration) {
    this(Optional.empty(), period, duration);
  }

  public Interval(
      final Optional<ZonedDateTime> start, final Period period, final Duration duration) {
    this.period = period;
    this.duration = duration;
    this.start = start;
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

  public Optional<ZonedDateTime> getStart() {
    return start;
  }

  public long toEpochMilli(final long fromEpochMilli) {
    if (!start.isPresent()) {
      if (!isCalendarBased()) {
        return fromEpochMilli + getDuration().toMillis();
      }

      return ZonedDateTime.ofInstant(Instant.ofEpochMilli(fromEpochMilli), ZoneId.systemDefault())
          .plus(this)
          .toInstant()
          .toEpochMilli();
    }

    return start.get().plus(this).toInstant().toEpochMilli();
  }

  /**
   * Creates a new interval with the specified start instant.
   *
   * @param start the start instant for the new interval
   * @return a new interval from this interval and the specified start
   */
  public Interval withStart(final Instant start) {
    final ZoneId zoneId = getStart().map(ZonedDateTime::getZone).orElse(ZoneId.systemDefault());
    return new Interval(
        Optional.of(ZonedDateTime.ofInstant(start, zoneId)), getPeriod(), getDuration());
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
        && Objects.equals(getDuration(), interval.getDuration())
        && Objects.equals(getStart(), interval.getStart());
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
   * Only supports a subset of ISO8601, combining start, period and duration.
   *
   * @param text ISO8601 conforming interval expression
   * @return parsed interval
   */
  public static Interval parse(final String text) {
    String sign = "";
    int startOffset = 0;
    final int index = text.lastIndexOf("/");
    Optional<ZonedDateTime> start = Optional.empty();
    if (index > 0) {
      start = Optional.ofNullable(ZonedDateTime.parse(text.substring(0, index)));
    }

    final String intervalExp = text.substring(index + 1);
    if (intervalExp.startsWith("-")) {
      startOffset = 1;
      sign = "-";
    } else if (intervalExp.startsWith("+")) {
      startOffset = 1;
    }

    final int durationOffset = intervalExp.indexOf('T');
    if (durationOffset == -1) {
      return new Interval(start, Period.parse(intervalExp), Duration.ZERO);
    } else if (durationOffset == startOffset + 1) {
      return new Interval(start, Period.ZERO, Duration.parse(intervalExp));
    }

    return new Interval(
        start,
        Period.parse(intervalExp.substring(0, durationOffset)),
        Duration.parse(sign + "P" + intervalExp.substring(durationOffset)));
  }
}
