/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.impl.DbLong;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Iteration conformance tests for the basic {@link io.camunda.zeebe.db.ColumnFamily} contract, kept
 * apart from the point operations so that a backend can adopt them once it supports iteration.
 */
public abstract class AbstractColumnFamilyIterationTest extends AbstractColumnFamilyTestBase {

  @Test
  public void shouldUseForeachValue() {
    // given
    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

    // when
    final List<Long> values = new ArrayList<>();
    columnFamily.forEach((value) -> values.add(value.getValue()));

    // then
    assertThat(values).containsExactly((long) Short.MAX_VALUE, 255L, 123L, 921L, 1L);
  }

  @Test
  public void shouldUseForeachPair() {
    // given
    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

    // when
    final List<Long> keys = new ArrayList<>();
    final List<Long> values = new ArrayList<>();
    columnFamily.forEach(
        (key, value) -> {
          keys.add(key.getValue());
          values.add(value.getValue());
        });

    // then
    assertThat(keys).containsExactly(1L, 1213L, 4567L, 6734L, (long) Short.MAX_VALUE);
    assertThat(values).containsExactly((long) Short.MAX_VALUE, 255L, 123L, 921L, 1L);
  }

  @Test
  public void shouldDeleteOnForeachPair() {
    // given
    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

    // when
    columnFamily.forEach((key, value) -> columnFamily.deleteExisting(key));

    final List<Long> keys = new ArrayList<>();
    final List<Long> values = new ArrayList<>();
    columnFamily.forEach(
        (key, value) -> {
          keys.add(key.getValue());
          values.add(value.getValue());
        });

    // then
    assertThat(keys).isEmpty();
    assertThat(values).isEmpty();
    key.wrapLong(4567L);
    assertThat(columnFamily.exists(key)).isFalse();

    key.wrapLong(6734);
    assertThat(columnFamily.exists(key)).isFalse();

    key.wrapLong(1213);
    assertThat(columnFamily.exists(key)).isFalse();

    key.wrapLong(1);
    assertThat(columnFamily.exists(key)).isFalse();

    key.wrapLong(Short.MAX_VALUE);
    assertThat(columnFamily.exists(key)).isFalse();
  }

  @Test
  public void shouldUseWhileTrue() {
    // given
    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

    // when
    final List<Long> keys = new ArrayList<>();
    final List<Long> values = new ArrayList<>();
    columnFamily.whileTrue(
        (key, value) -> {
          keys.add(key.getValue());
          values.add(value.getValue());

          return key.getValue() != 4567;
        });

    // then
    assertThat(keys).containsExactly(1L, 1213L, 4567L);
    assertThat(values).containsExactly((long) Short.MAX_VALUE, 255L, 123L);
  }

  @Test
  public void shouldDeleteWhileTrue() {
    // given
    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

    // when
    columnFamily.whileTrue(
        (key, value) -> {
          columnFamily.deleteExisting(key);
          return key.getValue() != 4567;
        });

    final List<Long> keys = new ArrayList<>();
    final List<Long> values = new ArrayList<>();
    columnFamily.forEach(
        (key, value) -> {
          keys.add(key.getValue());
          values.add(value.getValue());
        });

    // then
    assertThat(keys).containsExactly(6734L, (long) Short.MAX_VALUE);
    assertThat(values).containsExactly(921L, 1L);
  }

  @Test
  public void shouldUseWhileTrueWithStartAt() {
    // given
    final var startAt = new DbLong();
    startAt.wrapLong(1213);

    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

    // when
    final List<Long> keys = new ArrayList<>();
    final List<Long> values = new ArrayList<>();
    columnFamily.whileTrue(
        startAt,
        (key, value) -> {
          keys.add(key.getValue());
          values.add(value.getValue());

          return key.getValue() != 4567;
        });

    // then
    assertThat(keys).containsExactly(1213L, 4567L);
    assertThat(values).containsExactly(255L, 123L);
  }

  @Test
  public void shouldUseWhileTrueWithStartAtMissingKey() {
    // given
    final var startAt = new DbLong();
    startAt.wrapLong(1212);

    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

    // when
    final List<Long> keys = new ArrayList<>();
    final List<Long> values = new ArrayList<>();
    columnFamily.whileTrue(
        startAt,
        (key, value) -> {
          keys.add(key.getValue());
          values.add(value.getValue());

          return key.getValue() != 4567;
        });

    // then
    assertThat(keys).containsExactly(1213L, 4567L);
    assertThat(values).containsExactly(255L, 123L);
  }

  @Test
  public void shouldUseWhileTrueWithNullStartAt() {
    // given
    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);

    // when
    final List<Long> keys = new ArrayList<>();
    final List<Long> values = new ArrayList<>();
    columnFamily.whileTrue(
        null,
        (key, value) -> {
          keys.add(key.getValue());
          values.add(value.getValue());

          return key.getValue() != 4567;
        });

    // then
    assertThat(keys).containsExactly(1213L, 4567L);
    assertThat(values).containsExactly(255L, 123L);
  }

  @Test
  public void shouldUseWhileTrueReverse() {
    // given
    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

    // when
    final List<Long> keys = new ArrayList<>();
    final List<Long> values = new ArrayList<>();
    final var startAt = new DbLong();
    startAt.wrapLong(6734);
    columnFamily.whileTrueReverse(
        startAt,
        (key, value) -> {
          keys.add(key.getValue());
          values.add(value.getValue());
          return key.getValue() != 1213;
        });

    // then — reverse from 6734: 6734, 4567, 1213 (stops because visitor returns false)
    assertThat(keys).containsExactly(6734L, 4567L, 1213L);
    assertThat(values).containsExactly(921L, 123L, 255L);
  }

  @Test
  public void shouldUseWhileTrueReverseWithMissingKey() {
    // given
    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

    // when — start at 5000 which doesn't exist, seekForPrev lands on 4567
    final List<Long> keys = new ArrayList<>();
    final List<Long> values = new ArrayList<>();
    final var startAt = new DbLong();
    startAt.wrapLong(5000);
    columnFamily.whileTrueReverse(
        startAt,
        (key, value) -> {
          keys.add(key.getValue());
          values.add(value.getValue());
          return true;
        });

    // then — reverse from 4567: 4567, 1213, 1
    assertThat(keys).containsExactly(4567L, 1213L, 1L);
    assertThat(values).containsExactly(123L, 255L, (long) Short.MAX_VALUE);
  }

  @Test
  public void shouldUseWhileTrueReverseAll() {
    // given
    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

    // when — start at Long.MAX_VALUE to iterate all in reverse
    final List<Long> keys = new ArrayList<>();
    final List<Long> values = new ArrayList<>();
    final var startAt = new DbLong();
    startAt.wrapLong(Long.MAX_VALUE);
    columnFamily.whileTrueReverse(
        startAt,
        (key, value) -> {
          keys.add(key.getValue());
          values.add(value.getValue());
          return true;
        });

    // then — all keys in reverse order
    assertThat(keys).containsExactly((long) Short.MAX_VALUE, 6734L, 4567L, 1213L, 1L);
    assertThat(values).containsExactly(1L, 921L, 123L, 255L, (long) Short.MAX_VALUE);
  }

  @Test
  public void shouldReturnEmptyOnWhileTrueReverseWithNoEntries() {
    // given — empty CF

    // when
    final List<Long> keys = new ArrayList<>();
    final var startAt = new DbLong();
    startAt.wrapLong(100);
    columnFamily.whileTrueReverse(
        startAt,
        (key, value) -> {
          keys.add(key.getValue());
          return true;
        });

    // then
    assertThat(keys).isEmpty();
  }

  // ---- Key-only iteration tests ----

  @Test
  public void shouldUseForEachKey() {
    // given
    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

    // when
    final List<Long> keys = new ArrayList<>();
    columnFamily.forEachKey(key -> keys.add(key.getValue()));

    // then
    assertThat(keys).containsExactly(1L, 1213L, 4567L, 6734L, (long) Short.MAX_VALUE);
  }

  @Test
  public void shouldUseWhileTrueKey() {
    // given
    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

    // when
    final List<Long> keys = new ArrayList<>();
    columnFamily.whileTrue(
        key -> {
          keys.add(key.getValue());
          return key.getValue() != 4567;
        });

    // then
    assertThat(keys).containsExactly(1L, 1213L, 4567L);
  }

  @Test
  public void shouldUseWhileTrueKeyWithStartAt() {
    // given
    final var startAt = new DbLong();
    startAt.wrapLong(1213);

    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

    // when
    final List<Long> keys = new ArrayList<>();
    columnFamily.whileTrue(
        startAt,
        key -> {
          keys.add(key.getValue());
          return key.getValue() != 4567;
        });

    // then
    assertThat(keys).containsExactly(1213L, 4567L);
  }

  @Test
  public void shouldUseWhileTrueKeyWithStartAtMissingKey() {
    // given
    final var startAt = new DbLong();
    startAt.wrapLong(1212);

    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

    // when
    final List<Long> keys = new ArrayList<>();
    columnFamily.whileTrue(
        startAt,
        key -> {
          keys.add(key.getValue());
          return key.getValue() != 4567;
        });

    // then
    assertThat(keys).containsExactly(1213L, 4567L);
  }

  @Test
  public void shouldUseWhileTrueReverseKey() {
    // given
    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

    // when
    final List<Long> keys = new ArrayList<>();
    final var startAt = new DbLong();
    startAt.wrapLong(6734);
    columnFamily.whileTrueReverse(
        startAt,
        key -> {
          keys.add(key.getValue());
          return key.getValue() != 1213;
        });

    // then — reverse from 6734: 6734, 4567, 1213 (stops because visitor returns false)
    assertThat(keys).containsExactly(6734L, 4567L, 1213L);
  }

  @Test
  public void shouldUseWhileTrueReverseKeyWithMissingKey() {
    // given
    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

    // when — start at 5000 which doesn't exist, seekForPrev lands on 4567
    final List<Long> keys = new ArrayList<>();
    final var startAt = new DbLong();
    startAt.wrapLong(5000);
    columnFamily.whileTrueReverse(
        startAt,
        key -> {
          keys.add(key.getValue());
          return true;
        });

    // then — reverse from 4567: 4567, 1213, 1
    assertThat(keys).containsExactly(4567L, 1213L, 1L);
  }

  @Test
  public void shouldUseWhileTrueReverseKeyAll() {
    // given
    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

    // when — start at Long.MAX_VALUE to iterate all in reverse
    final List<Long> keys = new ArrayList<>();
    final var startAt = new DbLong();
    startAt.wrapLong(Long.MAX_VALUE);
    columnFamily.whileTrueReverse(
        startAt,
        key -> {
          keys.add(key.getValue());
          return true;
        });

    // then — all keys in reverse order
    assertThat(keys).containsExactly((long) Short.MAX_VALUE, 6734L, 4567L, 1213L, 1L);
  }

  @Test
  public void shouldReturnEmptyOnWhileTrueReverseKeyWithNoEntries() {
    // given — empty CF

    // when
    final List<Long> keys = new ArrayList<>();
    final var startAt = new DbLong();
    startAt.wrapLong(100);
    columnFamily.whileTrueReverse(
        startAt,
        key -> {
          keys.add(key.getValue());
          return true;
        });

    // then
    assertThat(keys).isEmpty();
  }
}
