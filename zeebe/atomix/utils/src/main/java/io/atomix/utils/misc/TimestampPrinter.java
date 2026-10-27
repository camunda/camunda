/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.utils.misc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Timestamp printer. */
public class TimestampPrinter {

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss,SSS");
  private final long timestamp;

  public TimestampPrinter(final long timestamp) {
    this.timestamp = timestamp;
  }

  /**
   * Returns a new timestamp printer.
   *
   * @param timestamp the timestamp to print
   * @return the timestamp printer
   */
  public static TimestampPrinter of(final long timestamp) {
    return new TimestampPrinter(timestamp);
  }

  @Override
  public String toString() {
    return FORMATTER.format(
        LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()));
  }
}
