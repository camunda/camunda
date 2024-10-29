/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.read.domain.DbQueryPage;
import io.camunda.db.rdbms.read.domain.DbQueryPage.KeySetPaginationFieldEntry;
import io.camunda.db.rdbms.read.domain.DbQueryPage.Operator;
import io.camunda.db.rdbms.read.domain.DbQuerySorting;
import io.camunda.db.rdbms.read.domain.DbQuerySorting.SortingEntry;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper.ProcessInstanceSearchColumn;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.ProcessInstanceSort;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AbstractEntityReaderTest {

  @Test
  void shouldConvertSort() {
    final var reader = new ProcessInstanceReader(null);

    final var convertedSort =
        reader.convertSort(
            ProcessInstanceSort.of(b -> b.processDefinitionName().asc().startDate().desc()),
            ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY);

    assertThat(convertedSort.orderings()).hasSize(3);
    assertThat(convertedSort.orderings())
        .containsExactly(
            new SortingEntry<>(ProcessInstanceSearchColumn.PROCESS_DEFINITION_NAME, SortOrder.ASC),
            new SortingEntry<>(ProcessInstanceSearchColumn.START_DATE, SortOrder.DESC),
            new SortingEntry<>(ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY, SortOrder.ASC));
  }

  @Test
  void shouldSkipUnknownSortColumn() {
    final var reader = new ProcessInstanceReader(null);

    final var convertedSort =
        reader.convertSort(
            new ProcessInstanceSort(
                List.of(
                    new FieldSorting("processName", SortOrder.ASC),
                    new FieldSorting("foo", SortOrder.ASC))),
            ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY);

    assertThat(convertedSort.orderings()).hasSize(2);
    assertThat(convertedSort.orderings())
        .containsExactly(
            new SortingEntry<>(ProcessInstanceSearchColumn.PROCESS_DEFINITION_NAME, SortOrder.ASC),
            new SortingEntry<>(ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY, SortOrder.ASC));
  }

  @Test
  void shouldExtractSortValues() {
    final var reader = new ProcessInstanceReader(null);

    final var hit1 = Mockito.mock(ProcessInstanceEntity.class);
    when(hit1.key()).thenReturn(1L);
    when(hit1.processName()).thenReturn("foo");
    final var hit2 = Mockito.mock(ProcessInstanceEntity.class);
    when(hit2.key()).thenReturn(2L);
    when(hit2.processName()).thenReturn("bar");
    final var hit3 = Mockito.mock(ProcessInstanceEntity.class);
    when(hit3.key()).thenReturn(3L);
    when(hit3.processName()).thenReturn("alice");
    final var searchResult = List.of(hit1, hit2, hit3);

    final DbQuerySorting<ProcessInstanceEntity> sorting =
        DbQuerySorting.of(
            b ->
                b.addEntry(ProcessInstanceSearchColumn.PROCESS_DEFINITION_NAME, SortOrder.ASC)
                    .addEntry(ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY, SortOrder.ASC));

    final var sortValues = reader.extractSortValues(searchResult, sorting);

    assertThat(sortValues).hasSize(2);
    assertThat(sortValues).containsExactly("alice", 3L);
  }

  @Test
  void convertWithValidSortAndPage() {
    final DbQuerySorting<ProcessInstanceEntity> sort =
        DbQuerySorting.of(
            b ->
                b.addEntry(ProcessInstanceSearchColumn.PROCESS_DEFINITION_NAME, SortOrder.ASC)
                    .addEntry(ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY, SortOrder.ASC));
    final SearchQueryPage page = new SearchQueryPage(0, 10, null, null);

    final DbQueryPage result = AbstractEntityReader.convertPaging(sort, page);

    assertThat(result.size()).isEqualTo(10);
    assertThat(result.from()).isEqualTo(0);
    assertThat(result.keySetPagination()).isEmpty();
  }

  @Test
  void convertWithSearchAfter() {
    final DbQuerySorting<ProcessInstanceEntity> sort =
        DbQuerySorting.of(
            b ->
                b.addEntry(ProcessInstanceSearchColumn.PROCESS_DEFINITION_ID, SortOrder.ASC)
                    .addEntry(ProcessInstanceSearchColumn.PROCESS_DEFINITION_NAME, SortOrder.DESC)
                    .addEntry(ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY, SortOrder.ASC));
    final SearchQueryPage page =
        new SearchQueryPage(0, 10, new Object[] {"test-process-id", "Test Process", 42L}, null);

    final DbQueryPage result = AbstractEntityReader.convertPaging(sort, page);

    assertThat(result.size()).isEqualTo(10);
    assertThat(result.from()).isEqualTo(0);
    assertThat(result.keySetPagination()).hasSize(3);
    final var sorting0 = result.keySetPagination().getFirst();
    assertThat(sorting0.entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_ID", Operator.GREATER, "test-process-id"));

    final var sorting1 = result.keySetPagination().get(1);
    assertThat(sorting1.entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_ID", Operator.EQUALS, "test-process-id"),
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_NAME", Operator.LOWER, "Test Process"));

    final var sorting2 = result.keySetPagination().get(2);
    assertThat(sorting2.entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_ID", Operator.EQUALS, "test-process-id"),
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_NAME", Operator.EQUALS, "Test Process"),
            new KeySetPaginationFieldEntry("PROCESS_INSTANCE_KEY", Operator.GREATER, 42L));
  }

  @Test
  void convertWithSearchBefore() {
    final DbQuerySorting<ProcessInstanceEntity> sort =
        DbQuerySorting.of(
            b ->
                b.addEntry(ProcessInstanceSearchColumn.PROCESS_DEFINITION_ID, SortOrder.ASC)
                    .addEntry(ProcessInstanceSearchColumn.PROCESS_DEFINITION_NAME, SortOrder.DESC)
                    .addEntry(ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY, SortOrder.ASC));
    final SearchQueryPage page =
        new SearchQueryPage(0, 10, null, new Object[] {"test-process-id", "Test Process", 42L});

    final DbQueryPage result = AbstractEntityReader.convertPaging(sort, page);

    assertThat(result.size()).isEqualTo(10);
    assertThat(result.from()).isEqualTo(0);
    assertThat(result.keySetPagination()).hasSize(3);
    final var sorting0 = result.keySetPagination().getFirst();
    assertThat(sorting0.entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_ID", Operator.LOWER, "test-process-id"));

    final var sorting1 = result.keySetPagination().get(1);
    assertThat(sorting1.entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_ID", Operator.EQUALS, "test-process-id"),
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_NAME", Operator.GREATER, "Test Process"));

    final var sorting2 = result.keySetPagination().get(2);
    assertThat(sorting2.entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_ID", Operator.EQUALS, "test-process-id"),
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_NAME", Operator.EQUALS, "Test Process"),
            new KeySetPaginationFieldEntry("PROCESS_INSTANCE_KEY", Operator.LOWER, 42L));
  }
}
