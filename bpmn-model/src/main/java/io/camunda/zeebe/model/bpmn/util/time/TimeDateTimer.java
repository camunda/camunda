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
import java.time.ZonedDateTime;

public class TimeDateTimer implements Timer {

  private final Interval interval;

  public TimeDateTimer(final Interval interval) {
    this.interval = interval;
  }

  public TimeDateTimer(final ZonedDateTime dateTime) {
    this(new Interval(Period.ZERO, Duration.ofMillis(dateTime.toInstant().toEpochMilli())));
  }

  public static TimeDateTimer parse(final String timeDate) {
    return new TimeDateTimer(ZonedDateTime.parse(timeDate));
  }

  @Override
  public Interval getInterval() {
    return interval;
  }

  @Override
  public int getRepetitions() {
    return 1;
  }

  /**
   * Returns the instant represented by this timeDate in milliseconds.
   *
   * @param fromEpochMillis this parameter is ignored since base of this date is always the standard
   *     epoch (1970-01-01T00:00:00Z).
   * @return the timeDate as milliseconds since the epoch
   */
  @Override
  public long getDueDate(final long fromEpochMillis) {
    return getInterval().toEpochMilli(0);
  }
}
