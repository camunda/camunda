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
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.common.CheckpointIdGenerator;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
  void ofTimeRangeShouldCreatePrefixPatternWithoutOffset() {
    // given
    final var generator = new CheckpointIdGenerator(0L);
    final long fromTimestamp = 1700000000000L; // Nov 14, 2023
    final long toTimestamp = 1700999999999L; // Nov 26, 2023

    // when
    final var pattern = CheckpointPattern.ofTimeRange(fromTimestamp, toTimestamp, generator);

    // then
    assertThat(pattern).isInstanceOf(Prefix.class);
    assertThat(pattern).isEqualTo(new Prefix("1700"));
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
    final long fromTimestamp = 1700000000000L; // Nov 14, 2023 22:13:20 UTC
    final long toTimestamp = 1700086400000L; // Nov 15, 2023 22:13:20 UTC (1 day later)

    final long dayInSeconds = Duration.ofDays(1).toMillis();

    // when
    final var pattern = CheckpointPattern.ofTimeRange(fromTimestamp, toTimestamp, generator);

    System.out.println(pattern);
    // then - should create prefix pattern based on common prefix of adjusted timestamps
    assertThat(pattern).isEqualTo(CheckpointPattern.of("219510*"));
    assertThat(pattern.matches(generator.fromTimestamp(fromTimestamp))).isTrue();
    assertThat(pattern.matches(generator.fromTimestamp(toTimestamp))).isTrue();
    assertThat(pattern.matches(generator.fromTimestamp(fromTimestamp) + 10 * dayInSeconds))
        .isFalse();
    assertThat(pattern.matches(generator.fromTimestamp(toTimestamp) + 10 * dayInSeconds)).isFalse();
  }

  @Test
  void ofTimeRangeShouldCreateAnyPatternWhenNoCommonPrefix() {
    // given
    final var generator = new CheckpointIdGenerator(0L);
    final long fromTimestamp = 1000000000000L; // Sept 8, 2001
    final long toTimestamp = 9000000000000L; // Nov 20, 2255

    // when
    final var pattern = CheckpointPattern.ofTimeRange(fromTimestamp, toTimestamp, generator);

    // then
    assertThat(pattern).isInstanceOf(Any.class);
  }

  @Test
  void ofTimeRangeShouldThrowExceptionForNegativeFromTimestamp() {
    // given
    final var generator = new CheckpointIdGenerator(0L);

    // when/then
    assertThatThrownBy(() -> CheckpointPattern.ofTimeRange(-1L, 1000L, generator))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Expected fromTimestamp to be non-negative");
  }

  @Test
  void ofTimeRangeShouldThrowExceptionForNegativeToTimestamp() {
    // given
    final var generator = new CheckpointIdGenerator(0L);

    // when/then
    assertThatThrownBy(() -> CheckpointPattern.ofTimeRange(1000L, -1L, generator))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Expected toTimestamp to be non-negative");
  }

  @Test
  void ofTimeRangeShouldThrowExceptionWhenFromIsGreaterThanTo() {
    // given
    final var generator = new CheckpointIdGenerator(0L);

    // when/then
    assertThatThrownBy(() -> CheckpointPattern.ofTimeRange(2000L, 1000L, generator))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Expected fromTimestamp to be <= toTimestamp");
  }

  @Test
  void ofTimeRangeShouldHandleSameFromAndToTimestamp() {
    // given
    final var generator = new CheckpointIdGenerator(0L);
    final long timestamp = 1700000000000L;

    // when
    final var pattern = CheckpointPattern.ofTimeRange(timestamp, timestamp, generator);

    // then
    assertThat(pattern).isInstanceOf(Prefix.class);
    assertThat(pattern).isEqualTo(new Prefix("1700000000000"));
    assertThat(pattern.matches(1700000000000L)).isTrue();
    assertThat(pattern.matches(1700000000001L)).isFalse();
    assertThat(pattern.matches(1699999999999L)).isFalse();
  }
}
