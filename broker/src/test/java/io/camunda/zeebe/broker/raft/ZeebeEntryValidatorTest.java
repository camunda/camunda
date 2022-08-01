/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.raft;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.atomix.raft.zeebe.EntryValidator.ValidationResult;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class ZeebeEntryValidatorTest {
  private final ZeebeEntryValidator validator = new ZeebeEntryValidator();

  @Test
  void shouldRejectEntryWithGapInPosition() {
    // given
    final var lastEntry = new ApplicationEntry(1, 1, new UnsafeBuffer());
    final var entry = new ApplicationEntry(3, 3, new UnsafeBuffer());

    // when
    final ValidationResult result = validator.validateEntry(lastEntry, entry);

    // then
    assertThat(result.failed()).as("validation has failed").isTrue();
  }

  @Test
  void shouldAcceptEntryWithoutGapInPosition() {
    // given
    final var lastEntry = new ApplicationEntry(1, 2, new UnsafeBuffer());
    final var entry = new ApplicationEntry(3, 3, new UnsafeBuffer());

    // when
    final ValidationResult result = validator.validateEntry(lastEntry, entry);

    // then
    assertThat(result).as("validation was successful").isEqualTo(ValidationResult.ok());
  }

  @Test
  void shouldAcceptEntryWhenNoLastKnownEntry() {
    // given
    final var entry = new ApplicationEntry(3, 3, new UnsafeBuffer());

    // when
    final ValidationResult result = validator.validateEntry(null, entry);

    // then
    assertThat(result).as("validation was successful").isEqualTo(ValidationResult.ok());
  }
}
