/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.optimize.service.exceptions.OptimizeMetadataReadException;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.BackoffCalculator;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DatabaseMetadataStartupValidatorTest {

  private static final OptimizeMetadataReadException READ_FAILURE =
      new OptimizeMetadataReadException(
          "Failed retrieving the Optimize metadata document from database!",
          new IOException("no_shard_available_action_exception"));

  private final FakeClock clock = new FakeClock();
  private final List<Long> sleeps = new ArrayList<>();

  @Test
  void shouldRetryUntilMetadataCanBeRead() {
    // given
    final AtomicInteger attempts = new AtomicInteger();
    final DatabaseMetadataStartupValidator validator = validator(Duration.ofMinutes(10));

    // when
    validator.validate(
        () -> {
          if (attempts.incrementAndGet() < 3) {
            throw READ_FAILURE;
          }
        });

    // then
    assertThat(attempts).hasValue(3);
    assertThat(sleeps).hasSize(2).allSatisfy(sleep -> assertThat(sleep).isPositive());
  }

  @Test
  void shouldFailFastOnSchemaVersionMismatch() {
    // given
    final AtomicInteger attempts = new AtomicInteger();
    final OptimizeRuntimeException versionMismatch =
        new OptimizeRuntimeException("The database Optimize schema version [1] doesn't match");
    final DatabaseMetadataStartupValidator validator = validator(Duration.ofMinutes(10));

    // when / then
    assertThatThrownBy(
            () ->
                validator.validate(
                    () -> {
                      attempts.incrementAndGet();
                      throw versionMismatch;
                    }))
        .isSameAs(versionMismatch);
    assertThat(attempts).hasValue(1);
    assertThat(sleeps).isEmpty();
  }

  @Test
  void shouldGiveUpOnceMaxWaitIsExceeded() {
    // given
    final Duration maxWait = Duration.ofSeconds(10);
    final DatabaseMetadataStartupValidator validator = validator(maxWait);

    // when / then
    assertThatThrownBy(
            () ->
                validator.validate(
                    () -> {
                      throw READ_FAILURE;
                    }))
        .isInstanceOf(OptimizeRuntimeException.class)
        .isNotInstanceOf(OptimizeMetadataReadException.class)
        .hasMessageContaining("within [PT10S]")
        .hasCause(READ_FAILURE);
    assertThat(sleeps.stream().mapToLong(Long::longValue).sum()).isEqualTo(maxWait.toMillis());
  }

  @Test
  void shouldNotRetryWhenMaxWaitIsZero() {
    // given
    final AtomicInteger attempts = new AtomicInteger();
    final DatabaseMetadataStartupValidator validator = validator(Duration.ZERO);

    // when / then
    assertThatThrownBy(
            () ->
                validator.validate(
                    () -> {
                      attempts.incrementAndGet();
                      throw READ_FAILURE;
                    }))
        .hasCause(READ_FAILURE);
    assertThat(attempts).hasValue(1);
    assertThat(sleeps).isEmpty();
  }

  @Test
  void shouldStopRetryingWhenInterrupted() {
    // given
    final DatabaseMetadataStartupValidator validator =
        new DatabaseMetadataStartupValidator(
            new BackoffCalculator(30, 1000),
            Duration.ofMinutes(10),
            clock,
            millis -> {
              throw new InterruptedException();
            });

    try {
      // when / then
      assertThatThrownBy(
              () ->
                  validator.validate(
                      () -> {
                        throw READ_FAILURE;
                      }))
          .isInstanceOf(OptimizeRuntimeException.class)
          .hasCause(READ_FAILURE);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void shouldUseDefaultMaxWaitWhenNotConfigured() {
    // when
    final Duration maxWait = DatabaseMetadataStartupValidator.resolveMaxWait(null);

    // then
    assertThat(maxWait).isEqualTo(DatabaseMetadataStartupValidator.DEFAULT_MAX_WAIT);
  }

  @Test
  void shouldTreatNegativeMaxWaitAsZero() {
    // when
    final Duration maxWait = DatabaseMetadataStartupValidator.resolveMaxWait(-5);

    // then
    assertThat(maxWait).isZero();
  }

  private DatabaseMetadataStartupValidator validator(final Duration maxWait) {
    return new DatabaseMetadataStartupValidator(
        new BackoffCalculator(30, 1000),
        maxWait,
        clock,
        millis -> {
          sleeps.add(millis);
          clock.advance(millis);
        });
  }

  private static final class FakeClock extends Clock {
    private Instant now = Instant.parse("2026-10-19T00:00:00Z");

    void advance(final long millis) {
      now = now.plusMillis(millis);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(final ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
