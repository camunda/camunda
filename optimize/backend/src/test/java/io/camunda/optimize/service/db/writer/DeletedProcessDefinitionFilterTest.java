/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.service.db.reader.JobRegistryReader;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeletedProcessDefinitionFilterTest {

  private JobRegistryReader jobRegistryReader;
  private DeletedProcessDefinitionFilter filter;

  @BeforeEach
  void setUp() {
    jobRegistryReader = mock(JobRegistryReader.class);
    filter = new DeletedProcessDefinitionFilter(jobRegistryReader);
  }

  @Test
  void shouldReturnEmptySetForEmptyInputWithoutQueryingReader() {
    // when
    final Set<String> result = filter.suppressedDefinitionIds(List.of());

    // then
    assertThat(result).isEmpty();
    verifyNoInteractions(jobRegistryReader);
  }

  @Test
  void shouldDelegateToReaderAndReturnItsResult() {
    // given
    final List<String> candidateIds = List.of("1", "2", "3");
    when(jobRegistryReader.findEntityIdsWithJob(
            any(JobType.class), any(EntityType.class), anyCollection()))
        .thenReturn(Set.of("2"));

    // when
    final Set<String> result = filter.suppressedDefinitionIds(candidateIds);

    // then
    assertThat(result).containsExactly("2");
    verify(jobRegistryReader)
        .findEntityIdsWithJob(JobType.DELETE, EntityType.PROCESS_DEFINITION, candidateIds);
  }

  @Test
  void shouldReturnSameListForEmptyInputWithoutQueryingReader() {
    // given
    final List<String> entries = List.of();

    // when
    final List<String> result = filter.filterOutSuppressed(entries, Function.identity());

    // then
    assertThat(result).isSameAs(entries);
    verifyNoInteractions(jobRegistryReader);
  }

  @Test
  void shouldReturnAllEntriesWhenNoneAreSuppressed() {
    // given
    when(jobRegistryReader.findEntityIdsWithJob(
            any(JobType.class), any(EntityType.class), anyCollection()))
        .thenReturn(Set.of());
    final List<String> entries = List.of("1", "2");

    // when
    final List<String> result = filter.filterOutSuppressed(entries, Function.identity());

    // then
    assertThat(result).containsExactlyInAnyOrder("1", "2");
  }

  @Test
  void shouldFilterOutOnlySuppressedEntries() {
    // given
    when(jobRegistryReader.findEntityIdsWithJob(
            any(JobType.class), any(EntityType.class), anyCollection()))
        .thenReturn(Set.of("2"));
    final List<String> entries = List.of("1", "2", "3");

    // when
    final List<String> result = filter.filterOutSuppressed(entries, Function.identity());

    // then
    assertThat(result).containsExactlyInAnyOrder("1", "3");
  }

  @Test
  void shouldReturnEmptyListWhenAllEntriesAreSuppressed() {
    // given
    when(jobRegistryReader.findEntityIdsWithJob(
            any(JobType.class), any(EntityType.class), anyCollection()))
        .thenReturn(Set.of("1"));
    final List<String> entries = List.of("1");

    // when
    final List<String> result = filter.filterOutSuppressed(entries, Function.identity());

    // then
    assertThat(result).isEmpty();
  }
}
