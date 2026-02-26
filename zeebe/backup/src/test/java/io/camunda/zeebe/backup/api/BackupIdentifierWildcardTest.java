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
}
