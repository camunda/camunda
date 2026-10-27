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
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.ProcessInstanceSort;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import java.util.List;
import org.instancio.Instancio;
import org.instancio.Select;
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

  @Test
  void shouldConvertSortWithDuplicatedColumn() {
    final var reader = new ProcessInstanceDbReader(null, TEST_CONFIG);

    final var convertedSort =
        reader.convertSort(
            ProcessInstanceSort.of(
                b ->
                    b.processDefinitionName()
                        .asc()
                        .startDate()
                        .desc()
                        .processDefinitionName()
                        .desc()),
            ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY);

    assertThat(convertedSort.orderings()).hasSize(3);
    assertThat(convertedSort.orderings())
        .containsExactly(
            new SortingEntry<>(ProcessInstanceSearchColumn.PROCESS_DEFINITION_NAME, SortOrder.ASC),
            new SortingEntry<>(ProcessInstanceSearchColumn.START_DATE, SortOrder.DESC),
            // Note: duplicated PROCESS_DEFINITION_NAME is ignored
            new SortingEntry<>(ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY, SortOrder.ASC));
  }

  @Test
  void shouldRejectEmptyAfterCursor() {
    final DbQuerySorting<ProcessInstanceEntity> sort =
        DbQuerySorting.of(
            b -> b.addEntry(ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY, SortOrder.ASC));
    final SearchQueryPage page = SearchQueryPage.of(p -> p.from(0).size(10).after(""));

    assertThatThrownBy(() -> reader.convertPaging(sort, page))
        .isInstanceOf(CamundaSearchException.class)
        .extracting(e -> ((CamundaSearchException) e).getReason())
        .isEqualTo(Reason.INVALID_ARGUMENT);
  }

  @Test
  void shouldRejectEmptyBeforeCursor() {
    final DbQuerySorting<ProcessInstanceEntity> sort =
        DbQuerySorting.of(
            b -> b.addEntry(ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY, SortOrder.ASC));
    final SearchQueryPage page = SearchQueryPage.of(p -> p.from(0).size(10).before(""));

    assertThatThrownBy(() -> reader.convertPaging(sort, page))
        .isInstanceOf(CamundaSearchException.class)
        .extracting(e -> ((CamundaSearchException) e).getReason())
        .isEqualTo(Reason.INVALID_ARGUMENT);
  }

  // ---- Null-value keyset pagination tests ----

  @Test
  void shouldBuildSearchAfterWithNullInFirstColumnTwoCols() {
    // given: an entity whose first sort column (processDefinitionName) is null
    final var entity =
        Instancio.of(ProcessInstanceEntity.class)
            .set(Select.field(ProcessInstanceEntity::processDefinitionName), null)
            .create();

    final DbQuerySorting<ProcessInstanceEntity> sort =
        DbQuerySorting.of(
            b ->
                b.addEntry(ProcessInstanceSearchColumn.PROCESS_DEFINITION_NAME, SortOrder.ASC)
                    .addEntry(ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY, SortOrder.ASC));

    final SearchQueryResult result = reader.buildSearchQueryResult(1L, List.of(entity), sort);

    // when
    final SearchQueryPage page =
        SearchQueryPage.of(p -> p.from(0).size(10).after(result.endCursor()));
    final DbQueryPage dbPage = reader.convertPaging(sort, page);

    // then: 2 keyset entries
    assertThat(dbPage.keySetPagination()).hasSize(2);

    // first entry: processDefinitionName IS NOT NULL (null + ASC + searchAfter => IS_NOT_NULL)
    final var ks0 = dbPage.keySetPagination().get(0);
    assertThat(ks0.entries())
        .containsExactly(
            new KeySetPaginationFieldEntry("PROCESS_DEFINITION_NAME", Operator.IS_NOT_NULL, null));

    // second entry: processDefinitionName IS_NULL (null equality prefix) AND processInstanceKey >
    // value
    final var ks1 = dbPage.keySetPagination().get(1);
    assertThat(ks1.entries())
        .containsExactly(
            new KeySetPaginationFieldEntry("PROCESS_DEFINITION_NAME", Operator.IS_NULL, null),
            new KeySetPaginationFieldEntry(
                "PROCESS_INSTANCE_KEY", Operator.GREATER, entity.processInstanceKey()));
  }

  @Test
  void shouldBuildSearchAfterWithNullInSecondColumnThreeCols() {
    // given: an entity whose second sort column (processDefinitionVersionTag) is null
    final var entity =
        Instancio.of(ProcessInstanceEntity.class)
            .set(Select.field(ProcessInstanceEntity::processDefinitionVersionTag), null)
            .create();

    final DbQuerySorting<ProcessInstanceEntity> sort =
        DbQuerySorting.of(
            b ->
                b.addEntry(ProcessInstanceSearchColumn.PROCESS_DEFINITION_NAME, SortOrder.ASC)
                    .addEntry(
                        ProcessInstanceSearchColumn.PROCESS_DEFINITION_VERSION_TAG, SortOrder.ASC)
                    .addEntry(ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY, SortOrder.ASC));

    final SearchQueryResult result = reader.buildSearchQueryResult(1L, List.of(entity), sort);

    // when
    final SearchQueryPage page =
        SearchQueryPage.of(p -> p.from(0).size(10).after(result.endCursor()));
    final DbQueryPage dbPage = reader.convertPaging(sort, page);

    // then: 3 keyset entries
    assertThat(dbPage.keySetPagination()).hasSize(3);

    // ks0: processDefinitionName > value  (non-null, ASC, searchAfter)
    final var ks0 = dbPage.keySetPagination().get(0);
    assertThat(ks0.entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_NAME", Operator.GREATER, entity.processDefinitionName()));

    // ks1: processDefinitionName = value AND processDefinitionVersionTag IS NOT NULL
    //       (null + ASC + searchAfter => IS_NOT_NULL)
    final var ks1 = dbPage.keySetPagination().get(1);
    assertThat(ks1.entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_NAME", Operator.EQUALS, entity.processDefinitionName()),
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_VERSION_TAG", Operator.IS_NOT_NULL, null));

    // ks2: processDefinitionName = value AND processDefinitionVersionTag IS NULL AND
    //       processInstanceKey > value
    final var ks2 = dbPage.keySetPagination().get(2);
    assertThat(ks2.entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_NAME", Operator.EQUALS, entity.processDefinitionName()),
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_VERSION_TAG", Operator.IS_NULL, null),
            new KeySetPaginationFieldEntry(
                "PROCESS_INSTANCE_KEY", Operator.GREATER, entity.processInstanceKey()));
  }

  @Test
  void shouldBuildSearchAfterWithNullInFirstColumnThreeCols() {
    // given: entity where the first sort column (processDefinitionVersionTag) is null
    final var entity =
        Instancio.of(ProcessInstanceEntity.class)
            .set(Select.field(ProcessInstanceEntity::processDefinitionVersionTag), null)
            .create();

    final DbQuerySorting<ProcessInstanceEntity> sort =
        DbQuerySorting.of(
            b ->
                b.addEntry(
                        ProcessInstanceSearchColumn.PROCESS_DEFINITION_VERSION_TAG, SortOrder.ASC)
                    .addEntry(ProcessInstanceSearchColumn.START_DATE, SortOrder.DESC)
                    .addEntry(ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY, SortOrder.ASC));

    final SearchQueryResult result = reader.buildSearchQueryResult(1L, List.of(entity), sort);

    // when
    final SearchQueryPage page =
        SearchQueryPage.of(p -> p.from(0).size(10).after(result.endCursor()));
    final DbQueryPage dbPage = reader.convertPaging(sort, page);

    // then: 3 keyset entries
    assertThat(dbPage.keySetPagination()).hasSize(3);

    // ks0: processDefinitionVersionTag IS NOT NULL  (null + ASC + searchAfter => IS_NOT_NULL)
    assertThat(dbPage.keySetPagination().get(0).entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_VERSION_TAG", Operator.IS_NOT_NULL, null));

    // ks1: processDefinitionVersionTag IS NULL (null equality) AND startDate < value
    //       (DESC + searchAfter => LOWER)
    assertThat(dbPage.keySetPagination().get(1).entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_VERSION_TAG", Operator.IS_NULL, null),
            new KeySetPaginationFieldEntry("START_DATE", Operator.LOWER, entity.startDate()));

    // ks2: processDefinitionVersionTag IS NULL AND startDate = value AND processInstanceKey > value
    assertThat(dbPage.keySetPagination().get(2).entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_VERSION_TAG", Operator.IS_NULL, null),
            new KeySetPaginationFieldEntry("START_DATE", Operator.EQUALS, entity.startDate()),
            new KeySetPaginationFieldEntry(
                "PROCESS_INSTANCE_KEY", Operator.GREATER, entity.processInstanceKey()));
  }

  @Test
  void shouldBuildSearchBeforeWithNullInFirstColumnTwoCols() {
    // given: entity where first column (processDefinitionVersionTag) is null. ASC sorts NULLs
    // first, so when traversing backwards nothing sorts before a leading null - the strict leading
    // clause is dropped, leaving only the tie-break that walks earlier null rows by unique key.
    final var entity =
        Instancio.of(ProcessInstanceEntity.class)
            .set(Select.field(ProcessInstanceEntity::processDefinitionVersionTag), null)
            .create();

    final DbQuerySorting<ProcessInstanceEntity> sort =
        DbQuerySorting.of(
            b ->
                b.addEntry(
                        ProcessInstanceSearchColumn.PROCESS_DEFINITION_VERSION_TAG, SortOrder.ASC)
                    .addEntry(ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY, SortOrder.ASC));

    final SearchQueryResult result = reader.buildSearchQueryResult(1L, List.of(entity), sort);

    // when
    final SearchQueryPage page =
        SearchQueryPage.of(p -> p.from(0).size(10).before(result.startCursor()));
    final DbQueryPage dbPage = reader.convertPaging(sort, page);

    // then: only the tie-break group remains (the unsatisfiable leading group is dropped)
    assertThat(dbPage.keySetPagination()).hasSize(1);

    // processDefinitionVersionTag IS NULL AND processInstanceKey < value
    assertThat(dbPage.keySetPagination().get(0).entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_VERSION_TAG", Operator.IS_NULL, null),
            new KeySetPaginationFieldEntry(
                "PROCESS_INSTANCE_KEY", Operator.LOWER, entity.processInstanceKey()));
  }

  @Test
  void shouldBuildSearchAfterDescWithNullInFirstColumn() {
    // given: entity where the DESC sort column (processDefinitionVersionTag) is null. DESC sorts
    // NULLs last, so nothing sorts after a null cursor - the strict leading clause must be dropped
    // (not IS NULL, which would re-match every null row and stall the cursor). Only the tie-break
    // that advances within the trailing null block by the unique key survives.
    final var entity =
        Instancio.of(ProcessInstanceEntity.class)
            .set(Select.field(ProcessInstanceEntity::processDefinitionVersionTag), null)
            .create();

    final DbQuerySorting<ProcessInstanceEntity> sort =
        DbQuerySorting.of(
            b ->
                b.addEntry(
                        ProcessInstanceSearchColumn.PROCESS_DEFINITION_VERSION_TAG, SortOrder.DESC)
                    .addEntry(ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY, SortOrder.ASC));

    final SearchQueryResult result = reader.buildSearchQueryResult(1L, List.of(entity), sort);

    // when
    final SearchQueryPage page =
        SearchQueryPage.of(p -> p.from(0).size(10).after(result.endCursor()));
    final DbQueryPage dbPage = reader.convertPaging(sort, page);

    // then: only the tie-break group remains (the unsatisfiable leading group is dropped)
    assertThat(dbPage.keySetPagination()).hasSize(1);

    // processDefinitionVersionTag IS NULL AND processInstanceKey > value
    assertThat(dbPage.keySetPagination().get(0).entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_VERSION_TAG", Operator.IS_NULL, null),
            new KeySetPaginationFieldEntry(
                "PROCESS_INSTANCE_KEY", Operator.GREATER, entity.processInstanceKey()));
  }

  @Test
  void shouldBuildSearchBeforeDescWithNullInFirstColumn() {
    // given: entity where the DESC sort column (processDefinitionVersionTag) is null. DESC sorts
    // NULLs last, so traversing backwards from a trailing null reaches the non-null rows - the
    // strict leading clause is IS NOT NULL (was incorrectly IS NULL, which re-matched null rows).
    final var entity =
        Instancio.of(ProcessInstanceEntity.class)
            .set(Select.field(ProcessInstanceEntity::processDefinitionVersionTag), null)
            .create();

    final DbQuerySorting<ProcessInstanceEntity> sort =
        DbQuerySorting.of(
            b ->
                b.addEntry(
                        ProcessInstanceSearchColumn.PROCESS_DEFINITION_VERSION_TAG, SortOrder.DESC)
                    .addEntry(ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY, SortOrder.ASC));

    final SearchQueryResult result = reader.buildSearchQueryResult(1L, List.of(entity), sort);

    // when
    final SearchQueryPage page =
        SearchQueryPage.of(p -> p.from(0).size(10).before(result.startCursor()));
    final DbQueryPage dbPage = reader.convertPaging(sort, page);

    // then: 2 keyset entries
    assertThat(dbPage.keySetPagination()).hasSize(2);

    // ks0: processDefinitionVersionTag IS NOT NULL  (null + DESC + searchBefore => IS_NOT_NULL)
    assertThat(dbPage.keySetPagination().get(0).entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_VERSION_TAG", Operator.IS_NOT_NULL, null));

    // ks1: processDefinitionVersionTag IS NULL AND processInstanceKey < value
    assertThat(dbPage.keySetPagination().get(1).entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_VERSION_TAG", Operator.IS_NULL, null),
            new KeySetPaginationFieldEntry(
                "PROCESS_INSTANCE_KEY", Operator.LOWER, entity.processInstanceKey()));
  }

  @Test
  void shouldBuildSearchAfterWithAllNullSortColumnsThreeCols() {
    // given: all three sort columns are null except the tiebreaker (processInstanceKey)
    final var entity =
        Instancio.of(ProcessInstanceEntity.class)
            .set(Select.field(ProcessInstanceEntity::processDefinitionVersionTag), null)
            .set(Select.field(ProcessInstanceEntity::processDefinitionName), null)
            .create();

    final DbQuerySorting<ProcessInstanceEntity> sort =
        DbQuerySorting.of(
            b ->
                b.addEntry(
                        ProcessInstanceSearchColumn.PROCESS_DEFINITION_VERSION_TAG, SortOrder.ASC)
                    .addEntry(ProcessInstanceSearchColumn.PROCESS_DEFINITION_NAME, SortOrder.ASC)
                    .addEntry(ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY, SortOrder.ASC));

    final SearchQueryResult result = reader.buildSearchQueryResult(1L, List.of(entity), sort);

    // when
    final SearchQueryPage page =
        SearchQueryPage.of(p -> p.from(0).size(10).after(result.endCursor()));
    final DbQueryPage dbPage = reader.convertPaging(sort, page);

    // then: 3 keyset entries
    assertThat(dbPage.keySetPagination()).hasSize(3);

    // ks0: processDefinitionVersionTag IS NOT NULL  (null + ASC + searchAfter)
    assertThat(dbPage.keySetPagination().get(0).entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_VERSION_TAG", Operator.IS_NOT_NULL, null));

    // ks1: processDefinitionVersionTag IS NULL AND processDefinitionName IS NOT NULL
    assertThat(dbPage.keySetPagination().get(1).entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_VERSION_TAG", Operator.IS_NULL, null),
            new KeySetPaginationFieldEntry("PROCESS_DEFINITION_NAME", Operator.IS_NOT_NULL, null));

    // ks2: processDefinitionVersionTag IS NULL AND processDefinitionName IS NULL
    //       AND processInstanceKey > value
    assertThat(dbPage.keySetPagination().get(2).entries())
        .containsExactly(
            new KeySetPaginationFieldEntry(
                "PROCESS_DEFINITION_VERSION_TAG", Operator.IS_NULL, null),
            new KeySetPaginationFieldEntry("PROCESS_DEFINITION_NAME", Operator.IS_NULL, null),
            new KeySetPaginationFieldEntry(
                "PROCESS_INSTANCE_KEY", Operator.GREATER, entity.processInstanceKey()));
  }
}
