/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.DbQueryPage;
import io.camunda.db.rdbms.read.domain.DbQueryPage.KeySetPaginationFieldEntry;
import io.camunda.db.rdbms.read.domain.DbQueryPage.Operator;
import io.camunda.db.rdbms.read.domain.DbQuerySorting;
import io.camunda.db.rdbms.read.domain.DbQuerySorting.SortingEntry;
import io.camunda.db.rdbms.sql.columns.ProcessInstanceSearchColumn;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.ProcessInstanceSort;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import java.util.List;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

class AbstractEntityReaderTest {
  public static final RdbmsReaderConfig TEST_CONFIG = RdbmsReaderConfig.builder().build();
  final AbstractEntityReader<ProcessInstanceEntity> reader =
      new ProcessInstanceDbReader(null, TEST_CONFIG);

  @Test
  void shouldConvertSort() {
    final var reader = new ProcessInstanceDbReader(null, TEST_CONFIG);

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
  void shouldThrowOnUnknownSortColumn() {
    final var reader = new ProcessInstanceDbReader(null, TEST_CONFIG);

    assertThatThrownBy(
            () ->
                reader.convertSort(
                    new ProcessInstanceSort(
                        List.of(
                            new FieldSorting("processDefinitionName", SortOrder.ASC),
                            new FieldSorting("foo", SortOrder.ASC))),
                    ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unknown sortField: foo");
  }

  @Test
  void convertWithValidSortAndPage() {
    final DbQuerySorting<ProcessInstanceEntity> sort =
        DbQuerySorting.of(
            b ->
                b.addEntry(ProcessInstanceSearchColumn.PROCESS_DEFINITION_NAME, SortOrder.ASC)
                    .addEntry(ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY, SortOrder.ASC));
    final SearchQueryPage page = SearchQueryPage.of(p -> p.from(0).size(10));

    final DbQueryPage result = reader.convertPaging(sort, page);

    assertThat(result.size()).isEqualTo(10);
    assertThat(result.from()).isEqualTo(0);
    assertThat(result.keySetPagination()).isEmpty();
  }

  @Test
  void convertWithSearchAfter() {
    final var entity = Instancio.create(ProcessInstanceEntity.class);

    final DbQuerySorting<ProcessInstanceEntity> sort =
        DbQuerySorting.of(
            b ->
                b.addEntry(ProcessInstanceSearchColumn.PROCESS_DEFINITION_ID, SortOrder.ASC)
                    .addEntry(ProcessInstanceSearchColumn.START_DATE, SortOrder.DESC)
                    .addEntry(ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY, SortOrder.ASC));

    final SearchQueryResult result = reader.buildSearchQueryResult(1L, List.of(entity), sort);

    final SearchQueryPage page =
        SearchQueryPage.of(p -> p.from(0).size(10).after(result.endCursor()));

    final DbQueryPage dbPage = reader.convertPaging(sort, page);

    assertThat(dbPage.size()).isEqualTo(10);
    assertThat(dbPage.from()).isEqualTo(0);
    assertThat(dbPage.keySetPagination()).hasSize(3);
    final var sorting0 = dbPage.keySetPagination().getFirst();
    assertThat(sorting0.entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_ID", Operator.GREATER, entity.processDefinitionId()));

    final var sorting1 = dbPage.keySetPagination().get(1);
    assertThat(sorting1.entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_ID", Operator.EQUALS, entity.processDefinitionId()),
            new KeySetPaginationFieldEntry("START_DATE", Operator.LOWER, entity.startDate()));

    final var sorting2 = dbPage.keySetPagination().get(2);
    assertThat(sorting2.entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_ID", Operator.EQUALS, entity.processDefinitionId()),
            new KeySetPaginationFieldEntry("START_DATE", Operator.EQUALS, entity.startDate()),
            new KeySetPaginationFieldEntry(
                "PROCESS_INSTANCE_KEY", Operator.GREATER, entity.processInstanceKey()));
  }

  @Test
  void convertWithSearchBefore() {
    final var entity = Instancio.create(ProcessInstanceEntity.class);

    final DbQuerySorting<ProcessInstanceEntity> sort =
        DbQuerySorting.of(
            b ->
                b.addEntry(ProcessInstanceSearchColumn.PROCESS_DEFINITION_ID, SortOrder.ASC)
                    .addEntry(
                        ProcessInstanceSearchColumn.PROCESS_DEFINITION_VERSION, SortOrder.DESC)
                    .addEntry(ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY, SortOrder.ASC));

    final SearchQueryResult result = reader.buildSearchQueryResult(1L, List.of(entity), sort);

    final SearchQueryPage page =
        SearchQueryPage.of(p -> p.from(0).size(10).before(result.startCursor()));

    final DbQueryPage dbPage = reader.convertPaging(sort, page);

    assertThat(dbPage.size()).isEqualTo(10);
    assertThat(dbPage.from()).isEqualTo(0);
    assertThat(dbPage.keySetPagination()).hasSize(3);
    final var sorting0 = dbPage.keySetPagination().getFirst();
    assertThat(sorting0.entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_ID", Operator.LOWER, entity.processDefinitionId()));

    final var sorting1 = dbPage.keySetPagination().get(1);
    assertThat(sorting1.entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_ID", Operator.EQUALS, entity.processDefinitionId()),
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_VERSION", Operator.GREATER, entity.processDefinitionVersion()));

    final var sorting2 = dbPage.keySetPagination().get(2);
    assertThat(sorting2.entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_ID", Operator.EQUALS, entity.processDefinitionId()),
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_VERSION", Operator.EQUALS, entity.processDefinitionVersion()),
            new KeySetPaginationFieldEntry(
                "PROCESS_INSTANCE_KEY", Operator.LOWER, entity.processInstanceKey()));
  }
}
