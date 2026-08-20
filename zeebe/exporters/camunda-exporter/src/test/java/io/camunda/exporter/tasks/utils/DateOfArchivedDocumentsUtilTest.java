/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.utils;

import static io.camunda.exporter.tasks.util.DateOfArchivedDocumentsUtil.getBucketStart;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class DateOfArchivedDocumentsUtilTest {

  @ParameterizedTest(name = "getBucketStart(\"{0}\", \"{1}\", \"{2}\") = \"{3}\"")
  @MethodSource("bucketStartCases")
  void shouldReturnCorrectBucketStart(
      final String endDate,
      final String rolloverInterval,
      final String dateFormat,
      final String expectedBucketStart) {
    assertThat(getBucketStart(endDate, rolloverInterval, dateFormat))
        .isEqualTo(expectedBucketStart);
  }

  private static Stream<Arguments> bucketStartCases() {
    return Stream.of(
        // -- DAYS interval --
        // 1d: each day is its own bucket
        Arguments.of("2026-03-16", "1d", "date", "2026-03-16"),
        // 3d: March 16 falls in the March 14 bucket
        Arguments.of("2026-03-16", "3d", "date", "2026-03-14"),
        // 3d: exactly on a bucket boundary
        Arguments.of("2026-03-14", "3d", "date", "2026-03-14"),
        // 7d: weekly-equivalent via days
        Arguments.of("2026-03-18", "7d", "date", "2026-03-12"),

        // -- WEEKS interval (converted to days internally) --
        // 1w = 7 days
        Arguments.of("2026-03-18", "1w", "date", "2026-03-12"),
        // 2w = 14 days
        Arguments.of("2026-03-18", "2w", "date", "2026-03-12"),

        // -- HOURS interval --
        // 1h: sub-day interval, date always maps to start-of-day
        Arguments.of("2026-03-16", "1h", "date", "2026-03-16"),
        // 2h: still maps to start-of-day for date-only input
        Arguments.of("2026-03-16", "2h", "date", "2026-03-16"),
        // 6h: same behavior
        Arguments.of("2026-03-16", "6h", "date", "2026-03-16"),

        // -- MINUTES interval --
        // 1m: maps to start-of-day
        Arguments.of("2026-03-16", "1m", "date", "2026-03-16"),
        // 30m: maps to start-of-day
        Arguments.of("2026-03-16", "30m", "date", "2026-03-16"),

        // -- SECONDS interval --
        // 1s: maps to start-of-day
        Arguments.of("2026-03-16", "1s", "date", "2026-03-16"),
        // 30s: maps to start-of-day
        Arguments.of("2026-03-16", "30s", "date", "2026-03-16"),

        // -- MONTHS interval --
        // 1M: start of the month
        Arguments.of("2026-03-16", "1M", "date", "2026-03-01"),
        // 1M: first day of month is on boundary
        Arguments.of("2026-03-01", "1M", "date", "2026-03-01"),
        // 2M: bi-monthly buckets from epoch (Jan 1970). Mar 2026 => month 674 => bucket 674
        Arguments.of("2026-03-16", "2M", "date", "2026-03-01"),
        // 2M: April falls in the same bi-monthly bucket as March
        Arguments.of("2026-04-15", "2M", "date", "2026-03-01"),
        // 3M: quarterly buckets; March 2026 => month 674 => bucket 672 => Jan 2026
        Arguments.of("2026-03-16", "3M", "date", "2026-01-01"),
        // 3M: April in Q2 bucket
        Arguments.of("2026-04-16", "3M", "date", "2026-04-01"),
        // 6M: semi-annual; March 2026 => month 674 => bucket 672 => Jan 2026
        Arguments.of("2026-03-16", "6M", "date", "2026-01-01"),
        // 6M: July falls in the second half bucket
        Arguments.of("2026-07-15", "6M", "date", "2026-07-01"),

        // -- Year boundary crossing --
        // 1d at year boundary
        Arguments.of("2026-01-01", "1d", "date", "2026-01-01"),
        // 3d: Jan 1 2026 is exactly on a 3-day bucket boundary
        Arguments.of("2026-01-01", "3d", "date", "2026-01-01"),
        // 1M: January
        Arguments.of("2026-01-15", "1M", "date", "2026-01-01"),

        // -- Epoch date --
        Arguments.of("1970-01-01", "1d", "date", "1970-01-01"),
        Arguments.of("1970-01-01", "1M", "date", "1970-01-01"),

        // -- Custom date format: "yyyy-MM-dd" explicit pattern --
        Arguments.of("2026-03-16", "1d", "yyyy-MM-dd", "2026-03-16"),
        Arguments.of("2026-03-16", "3d", "yyyy-MM-dd", "2026-03-14"),
        Arguments.of("2026-03-16", "1M", "yyyy-MM-dd", "2026-03-01"),

        // -- Custom date format with hours: "yyyy-MM-dd-HH" (used in load tests) --
        // 1h: hour 14 is on a 1-hour boundary
        Arguments.of("2026-03-16-14", "1h", "yyyy-MM-dd-HH", "2026-03-16-14"),
        // 2h: hour 15 falls in the hour-14 bucket
        Arguments.of("2026-03-16-15", "2h", "yyyy-MM-dd-HH", "2026-03-16-14"),
        // 6h: hour 14 falls in the hour-12 bucket
        Arguments.of("2026-03-16-14", "6h", "yyyy-MM-dd-HH", "2026-03-16-12"),
        // 1d with hourly format
        Arguments.of("2026-03-16-14", "1d", "yyyy-MM-dd-HH", "2026-03-16-00"),
        // 3d with hourly format: March 16 falls in the March 14 bucket
        Arguments.of("2026-03-16-14", "3d", "yyyy-MM-dd-HH", "2026-03-14-00"),

        // -- Custom date format with minutes: "yyyy-MM-dd-HH-mm" --
        // 1m: minute 45 is on a 1-minute boundary
        Arguments.of("2026-03-16-14-45", "1m", "yyyy-MM-dd-HH-mm", "2026-03-16-14-45"),
        // 30m: minute 45 falls in the :30 bucket
        Arguments.of("2026-03-16-14-45", "30m", "yyyy-MM-dd-HH-mm", "2026-03-16-14-30"),
        // 30m: minute 15 falls in the :00 bucket
        Arguments.of("2026-03-16-14-15", "30m", "yyyy-MM-dd-HH-mm", "2026-03-16-14-00"),
        // 1h with minute format
        Arguments.of("2026-03-16-14-45", "1h", "yyyy-MM-dd-HH-mm", "2026-03-16-14-00"),
        // 1d with minute format
        Arguments.of("2026-03-16-14-45", "1d", "yyyy-MM-dd-HH-mm", "2026-03-16-00-00"),

        // -- Custom date format with seconds: "yyyy-MM-dd-HH-mm-ss" --
        // 1s: second 45 is on a 1-second boundary
        Arguments.of("2026-03-16-14-30-45", "1s", "yyyy-MM-dd-HH-mm-ss", "2026-03-16-14-30-45"),
        // 30s: second 45 falls in the :30 bucket
        Arguments.of("2026-03-16-14-30-45", "30s", "yyyy-MM-dd-HH-mm-ss", "2026-03-16-14-30-30"),
        // 30s: second 15 falls in the :00 bucket
        Arguments.of("2026-03-16-14-30-15", "30s", "yyyy-MM-dd-HH-mm-ss", "2026-03-16-14-30-00"),
        // 1m with second format
        Arguments.of("2026-03-16-14-30-45", "1m", "yyyy-MM-dd-HH-mm-ss", "2026-03-16-14-30-00"),
        // 1h with second format
        Arguments.of("2026-03-16-14-30-45", "1h", "yyyy-MM-dd-HH-mm-ss", "2026-03-16-14-00-00"));
  }
}
