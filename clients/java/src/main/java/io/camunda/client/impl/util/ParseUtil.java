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
package io.camunda.client.impl.util;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParseUtil {
  private static final Logger LOG = LoggerFactory.getLogger(ParseUtil.class);

  public static Long parseLongOrNull(final String input) {
    return input == null ? null : Long.parseLong(input);
  }

  public static long parseLongOrEmpty(final String input) {
    return input == null ? -1L : Long.parseLong(input);
  }

  public static String keyToString(final Long input) {
    return input == null ? null : String.valueOf(input);
  }

  public static OffsetDateTime parseOffsetDateTimeOrNull(final String dateTime) {
    if (dateTime == null) {
      return null;
    }
    try {
      try {
        return OffsetDateTime.parse(dateTime);
      } catch (final DateTimeParseException e) {
        LOG.debug(
            "Failed parsing '{}' as ISO-8601 date-time, trying to parse as zoned ISO-8601 date-time.",
            dateTime);
        return ZonedDateTime.parse(dateTime).toOffsetDateTime();
      }
    } catch (final Exception e) {
      throw new IllegalArgumentException("Failed to parse date: " + dateTime, e);
    }
  }
}
