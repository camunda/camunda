/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeletedProcessDefinitionFilterTest {

  private DeletedProcessDefinitionCache deletedProcessDefinitionCache;
  private DeletedProcessDefinitionFilter filter;

  @BeforeEach
  void setUp() {
    deletedProcessDefinitionCache = mock(DeletedProcessDefinitionCache.class);
    filter = new DeletedProcessDefinitionFilter(deletedProcessDefinitionCache);
  }

  @Test
  void shouldReturnEmptySetForEmptyInputWithoutQueryingCache() {
    // when
    final Set<String> result = filter.suppressedDefinitionIds(List.of());

    // then
    assertThat(result).isEmpty();
    verifyNoInteractions(deletedProcessDefinitionCache);
  }

  @Test
  void shouldReturnOnlyIdsThatAreSuppressedInCache() {
    // given
    final List<String> candidateIds = List.of("1", "2", "3");
    when(deletedProcessDefinitionCache.isSuppressed("2")).thenReturn(true);

    // when
    final Set<String> result = filter.suppressedDefinitionIds(candidateIds);

    // then
    assertThat(result).containsExactly("2");
  }

  @Test
  void shouldReturnSameListForEmptyInputWithoutQueryingCache() {
    // given
    final List<String> entries = List.of();

    // when
    final List<String> result = filter.filterOutSuppressed(entries, Function.identity());

    // then
    assertThat(result).isSameAs(entries);
    verifyNoInteractions(deletedProcessDefinitionCache);
  }

  @Test
  void shouldReturnAllEntriesWhenNoneAreSuppressed() {
    // given
    final List<String> entries = List.of("1", "2");

    // when
    final List<String> result = filter.filterOutSuppressed(entries, Function.identity());

    // then
    assertThat(result).containsExactlyInAnyOrder("1", "2");
  }

  @Test
  void shouldFilterOutOnlySuppressedEntries() {
    // given
    when(deletedProcessDefinitionCache.isSuppressed("2")).thenReturn(true);
    final List<String> entries = List.of("1", "2", "3");

    // when
    final List<String> result = filter.filterOutSuppressed(entries, Function.identity());

    // then
    assertThat(result).containsExactlyInAnyOrder("1", "3");
  }

  @Test
  void shouldReturnEmptyListWhenAllEntriesAreSuppressed() {
    // given
    when(deletedProcessDefinitionCache.isSuppressed("1")).thenReturn(true);
    final List<String> entries = List.of("1");

    // when
    final List<String> result = filter.filterOutSuppressed(entries, Function.identity());

    // then
    assertThat(result).isEmpty();
  }
}
