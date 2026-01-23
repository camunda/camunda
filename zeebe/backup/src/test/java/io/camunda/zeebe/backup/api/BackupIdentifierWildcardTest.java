/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern.Any;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern.Exact;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern.Prefix;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern.TimeRange;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.common.CheckpointIdGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class BackupIdentifierWildcardTest {
  @Test
  void shouldBuildPrefixWithPartitionId() {
    // given
    final var wildcard =
        new BackupIdentifierWildcardImpl(Optional.empty(), Optional.of(1), new Any());

    // when
    final var prefix = BackupIdentifierWildcard.asPrefix(wildcard);

    // then
    assertThat(prefix).isEqualTo("1/");
  }

  @Test
  void shouldBuildPrefixWithPartitionIdAndExactCheckpoint() {
    // given
    final var wildcard =
        new BackupIdentifierWildcardImpl(Optional.empty(), Optional.of(1), new Exact(100));

    // when
    final var prefix = BackupIdentifierWildcard.asPrefix(wildcard);

    // then
    assertThat(prefix).isEqualTo("1/100/");
  }

  @Test
  void shouldBuildPrefixWithPartitionIdAndExactCheckpointAndNodeId() {
    // given
    final var wildcard =
        new BackupIdentifierWildcardImpl(Optional.of(1), Optional.of(2), new Exact(100));

    // when
    final var prefix = BackupIdentifierWildcard.asPrefix(wildcard);

    // then
    assertThat(prefix).isEqualTo("2/100/1");
  }

  @Test
  void shouldBuildPrefixWithPartitionIdAndCheckpointPrefix() {
    // given
    final var wildcard =
        new BackupIdentifierWildcardImpl(Optional.of(1), Optional.of(2), new Prefix("10"));

    // when
    final var prefix = BackupIdentifierWildcard.asPrefix(wildcard);

    // then
    assertThat(prefix).isEqualTo("2/10");
  }

  @Test
  void shouldReturnEmptyPrefixWhenNoPartitionId() {
    // given
    final var wildcard =
        new BackupIdentifierWildcardImpl(Optional.of(1), Optional.empty(), new Prefix("10"));

    // when
    final var prefix = BackupIdentifierWildcard.asPrefix(wildcard);

    // then
    assertThat(prefix).isEmpty();
  }

  @Test
  void shouldBuildPrefixWithAnyCheckpoint() {
    // given
    final var wildcard =
        new BackupIdentifierWildcardImpl(Optional.of(1), Optional.of(2), new Any());

    // when
    final var prefix = BackupIdentifierWildcard.asPrefix(wildcard);

    // then
    assertThat(prefix).isEqualTo("2/");
  }

  @Test
  void checkpointPatternOfShouldReturnAny() {
    assertThat(CheckpointPattern.of(null)).isInstanceOf(Any.class);
    assertThat(CheckpointPattern.of("")).isInstanceOf(Any.class);
    assertThat(CheckpointPattern.of("*")).isInstanceOf(Any.class);
  }

  @Test
  void checkpointPatternOfShouldReturnExact() {
    assertThat(CheckpointPattern.of("123")).isEqualTo(new Exact(123));
  }

  @Test
  void checkpointPatternOfShouldReturnPrefix() {
    assertThat(CheckpointPattern.of("123*")).isEqualTo(new Prefix("123"));
  }

  @Test
  void checkpointPatternOfShouldThrowExceptionForInvalidArgument() {
    assertThatThrownBy(() -> CheckpointPattern.of("invalid"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CheckpointPattern.of("123a*"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anyCheckpointPatternShouldMatchAnyId() {
    // given
    final var pattern = new Any();
    // then
    assertThat(pattern.matches(1L)).isTrue();
    assertThat(pattern.matches(100L)).isTrue();
    assertThat(pattern.matches(Long.MAX_VALUE)).isTrue();
  }

  @Test
  void anyCheckpointPatternShouldReturnRegex() {
    assertThat(new Any().asRegex()).isEqualTo("\\d+");
  }

  @Test
  void exactCheckpointPatternShouldMatchOnlyExactId() {
    // given
    final var pattern = new Exact(100);
    // then
    assertThat(pattern.matches(100L)).isTrue();
    assertThat(pattern.matches(1L)).isFalse();
    assertThat(pattern.matches(101L)).isFalse();
    assertThat(pattern.matches(1000L)).isFalse();
  }

  @Test
  void exactCheckpointPatternShouldReturnRegex() {
    assertThat(new Exact(123).asRegex()).isEqualTo("123");
  }

  @Test
  void exactCheckpointPatternShouldThrowExceptionForNegativeId() {
    assertThatThrownBy(() -> new Exact(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void prefixCheckpointPatternShouldMatchPrefix() {
    // given
    final var pattern = new Prefix("10");
    // then
    assertThat(pattern.matches(10L)).isTrue();
    assertThat(pattern.matches(100L)).isTrue();
    assertThat(pattern.matches(101L)).isTrue();
    assertThat(pattern.matches(1L)).isFalse();
    assertThat(pattern.matches(20L)).isFalse();
    assertThat(pattern.matches(110L)).isFalse();
  }

  @Test
  void prefixCheckpointPatternShouldReturnRegex() {
    assertThat(new Prefix("123").asRegex()).isEqualTo("123\\d*");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  "})
  void prefixCheckpointPatternShouldThrowExceptionForEmptyPrefix(final String prefix) {
    assertThatThrownBy(() -> new Prefix(prefix)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void timeRangeCheckpointPatternShouldReturnRegex() {
    final var generator = new CheckpointIdGenerator(0L);
    final Instant fromTimestamp = Instant.ofEpochMilli(1700000000000L); // Nov 14, 2023
    final Instant toTimestamp = Instant.ofEpochMilli(1700999999999L); // Nov 26, 2023
    final var pattern = CheckpointPattern.ofTimeRange(fromTimestamp, toTimestamp, generator);
    assertThat(pattern.asRegex()).isEqualTo("1700\\d*");
  }

  @Test
  void ofTimeRangeShouldCreatePrefixPatternWithoutOffset() {
    // given
    final var generator = new CheckpointIdGenerator(0L);
    final Instant fromTimestamp = Instant.ofEpochMilli(1700000000000L); // Nov 14, 2023
    final Instant toTimestamp = Instant.ofEpochMilli(1700999999999L); // Nov 26, 2023

    // when
    final var pattern = CheckpointPattern.ofTimeRange(fromTimestamp, toTimestamp, generator);

    // then
    assertThat(pattern).isInstanceOf(TimeRange.class);
    assertThat(pattern.pattern()).isEqualTo(new Prefix("1700"));
    assertThat(pattern.matches(1700000000000L)).isTrue();
    assertThat(pattern.matches(1700500000000L)).isTrue();
    assertThat(pattern.matches(1700999999999L)).isTrue();
    assertThat(pattern.matches(1699999999999L)).isFalse();
    assertThat(pattern.matches(1701000000000L)).isFalse();
  }

  @Test
  void ofTimeRangeShouldCreatePrefixPatternWithOffset() {
    // given
    // Offset is used to avoid generating new backup IDs in the legacy range.
    // Legacy backup IDs were encoded as datetime strings (e.g., 20251011095523 for 2025-10-11
    // 09:55:23).
    // The offset is set to the last legacy backup ID to ensure new backups have higher IDs.
    final long offset = 20251011095523L; // Last legacy backup ID
    final var generator = new CheckpointIdGenerator(offset);

    // Typical use case: 1 day range (86400000 milliseconds = 24 hours)
    final Instant fromTimestamp = Instant.ofEpochMilli(1700000000000L); // Nov 14, 2023 22:13:20 UTC
    final Instant toTimestamp =
        Instant.ofEpochMilli(1700086400000L); // Nov 15, 2023 22:13:20 UTC (1 day later)
    final long fromCheckpoint = generator.fromTimestamp(fromTimestamp.toEpochMilli());
    final long toCheckpoint = generator.fromTimestamp(toTimestamp.toEpochMilli());
    final var dayInMillis = Duration.ofDays(1).toMillis();

    // when
    final var pattern = CheckpointPattern.ofTimeRange(fromTimestamp, toTimestamp, generator);

    // then - should create prefix pattern based on common prefix of adjusted timestamps
    assertThat(pattern).isInstanceOf(TimeRange.class);
    assertThat(pattern.pattern()).isEqualTo(CheckpointPattern.of("219510*"));
    assertThat(pattern.matches(fromCheckpoint)).isTrue();
    assertThat(pattern.matches(toCheckpoint)).isTrue();
    assertThat(pattern.matches(fromCheckpoint + 10 * dayInMillis)).isFalse();
    assertThat(pattern.matches(toCheckpoint + 10 * dayInMillis)).isFalse();
  }

  @Test
  void ofTimeRangeShouldCreateAnyPatternWhenNoCommonPrefix() {
    // given
    final var generator = new CheckpointIdGenerator(0L);
    final Instant fromTimestamp = Instant.ofEpochMilli(1000000000000L); // Sept 8, 2001
    final Instant toTimestamp = Instant.ofEpochMilli(9000000000000L); // Nov 20, 2255

    // when
    final var pattern = CheckpointPattern.ofTimeRange(fromTimestamp, toTimestamp, generator);

    // then
    assertThat(pattern.pattern()).isInstanceOf(Any.class);
    assertThat(pattern.matches(1000000000000L)).isTrue();
    assertThat(pattern.matches(9000000000000L)).isTrue();
    assertThat(pattern.matches(5000000000000L)).isTrue();
    assertThat(pattern.matches(9000000000001L)).isFalse();
  }

  @Test
  void ofTimeRangeShouldThrowExceptionForNegativeFromTimestamp() {
    // given
    final var generator = new CheckpointIdGenerator(0L);

    // when/then
    assertThatThrownBy(
            () ->
                CheckpointPattern.ofTimeRange(
                    Instant.ofEpochMilli(-1L), Instant.ofEpochMilli(1000L), generator))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Expected 'from' to be non-negative");
  }

  @Test
  void ofTimeRangeShouldThrowExceptionForNegativeToTimestamp() {
    // given
    final var generator = new CheckpointIdGenerator(0L);

    // when/then
    assertThatThrownBy(
            () ->
                CheckpointPattern.ofTimeRange(
                    Instant.ofEpochMilli(1000L), Instant.ofEpochMilli(-1L), generator))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Expected 'to' to be non-negative");
  }

  @Test
  void ofTimeRangeShouldThrowExceptionWhenFromIsGreaterThanTo() {
    // given
    final var generator = new CheckpointIdGenerator(0L);

    // when/then
    assertThatThrownBy(
            () ->
                CheckpointPattern.ofTimeRange(
                    Instant.ofEpochMilli(2000L), Instant.ofEpochMilli(1000L), generator))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Expected 'from' to be <= 'to'");
  }

  @Test
  void ofTimeRangeShouldHandleSameFromAndToTimestamp() {
    // given
    final var generator = new CheckpointIdGenerator(0L);
    final Instant timestamp = Instant.ofEpochMilli(1700000000000L);

    // when
    final var pattern = CheckpointPattern.ofTimeRange(timestamp, timestamp, generator);

    // then
    assertThat(pattern)
        .isEqualTo(
            new TimeRange(
                new Exact(1700000000000L),
                generator.fromTimestamp(timestamp.toEpochMilli()),
                generator.fromTimestamp(timestamp.toEpochMilli())));
    assertThat(pattern.matches(1700000000000L)).isTrue();
    assertThat(pattern.matches(1700000000001L)).isFalse();
    assertThat(pattern.matches(1699999999999L)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("provideLongestCommonPrefixTestCases")
  void longestCommonPrefixShouldReturnCorrectPattern(
      final String[] ids, final CheckpointPattern expectedPattern) {
    // when
    final var result = CheckpointPattern.longestCommonPrefix(ids);

    // then
    assertThat(result).isEqualTo(expectedPattern);
  }

  static java.util.stream.Stream<Arguments> provideLongestCommonPrefixTestCases() {
    return java.util.stream.Stream.of(
        // Two strings with common prefix
        Arguments.of(new String[] {"1700000000000", "1700999999999"}, new Prefix("1700")),
        // Two strings with no common prefix
        Arguments.of(new String[] {"1000000000000", "9000000000000"}, new Any()),
        // Multiple strings with common prefix
        Arguments.of(new String[] {"123456", "123789", "123000"}, new Prefix("123")),
        // Multiple strings with no common prefix
        Arguments.of(new String[] {"100", "200", "300"}, new Any()),
        // All identical strings
        Arguments.of(new String[] {"12345", "12345", "12345"}, new Exact(12345)),
        // Single string
        Arguments.of(new String[] {"12345"}, new Exact(12345)),
        // Empty array
        Arguments.of(new String[] {}, new Any()),
        // null
        Arguments.of(null, new Any()),
        // Three strings with varying common prefix
        Arguments.of(new String[] {"219510", "219599", "219512"}, new Prefix("2195")),
        // Strings with different lengths but common prefix
        Arguments.of(new String[] {"1234", "12345678"}, new Prefix("1234")),
        // Two strings where one is prefix of another
        Arguments.of(new String[] {"123", "123456"}, new Prefix("123")));
  }
}
