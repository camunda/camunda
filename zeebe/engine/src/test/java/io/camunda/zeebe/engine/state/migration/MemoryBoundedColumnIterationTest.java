/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

final class MemoryBoundedColumnIterationTest {

  @Test
  void shouldDrainAllValues(final @TempDir File tmpDir) {
    // given
    final var iteration = new MemoryBoundedColumnIteration(1024 * 1024L);
    final var db = DefaultZeebeDbFactory.defaultFactory().createDb(tmpDir);
    final var key = new DbLong();
    final var value = new DbLong();
    final var column =
        db.createColumnFamily(ZbColumnFamilies.DEFAULT, db.createContext(), key, value);
    final Map<Long, Long> expected = new HashMap<>();
    LongStream.range(0, 100)
        .forEach(
            i -> {
              key.wrapLong(i);
              value.wrapLong(i);
              column.upsert(key, value);
              expected.put(i, i);
            });
    final Map<Long, Long> drainedValues = new HashMap<>();

    // when
    iteration.drain(column, (k, v) -> drainedValues.put(k.getValue(), v.getValue()));

    // then
    assertThat(drainedValues).isEqualTo(expected);
    assertThat(column.isEmpty()).isTrue();
  }

  @Test
  void shouldIterateInBoundedChunks(final @TempDir File tmpDir) {
    // given
    final var iteration = new MemoryBoundedColumnIteration(50L * Long.BYTES);
    final var db = DefaultZeebeDbFactory.defaultFactory().createDb(tmpDir);
    final var key = new DbLong();
    final var value = new DbLong();
    final var column =
        db.createColumnFamily(ZbColumnFamilies.DEFAULT, db.createContext(), key, value);
    LongStream.range(0, 100)
        .forEach(
            i -> {
              key.wrapLong(i);
              value.wrapLong(i);
              column.upsert(key, value);
            });
    final var spiedColumn = Mockito.spy(column);

    // when
    iteration.drain(spiedColumn, (k, v) -> {});

    // then - expect 4 transactions since our limit is 50 longs, and each iteration adds 2 longs
    Mockito.verify(spiedColumn, Mockito.times(4)).whileTrue(Mockito.any());
  }
}
