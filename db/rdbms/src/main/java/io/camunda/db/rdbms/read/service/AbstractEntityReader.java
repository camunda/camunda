/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static io.camunda.db.rdbms.read.domain.DbQueryPage.KeySetPaginationFieldEntry.determineOperator;

import io.camunda.db.rdbms.read.domain.DbQueryPage;
import io.camunda.db.rdbms.read.domain.DbQueryPage.KeySetPagination;
import io.camunda.db.rdbms.read.domain.DbQueryPage.KeySetPaginationFieldEntry;
import io.camunda.db.rdbms.read.domain.DbQueryPage.Operator;
import io.camunda.db.rdbms.read.domain.DbQuerySorting;
import io.camunda.db.rdbms.sql.columns.SearchColumn;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.SortOption;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

abstract class AbstractEntityReader<T> {

  private final List<SearchColumn<T>> searchableColumns;
  private final Cursor cursor;

  public AbstractEntityReader(final SearchColumn<T>[] searchableColumns) {
    this.searchableColumns = List.of(searchableColumns);
    cursor = new Cursor();
  }

  private SearchColumn<T> determineSearchColumn(final String columnProperty) {
    // TODO convert to Map for faster lookup
    return searchableColumns.stream()
        .filter(column -> column.property().equals(columnProperty))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown sortField: " + columnProperty));
  }

  @SafeVarargs
  public final DbQuerySorting<T> convertSort(
      final SortOption sortOption, final SearchColumn<T>... discriminatorColumns) {
    final var builder = new DbQuerySorting.Builder<T>();
    final var discriminatorColumnList = new ArrayList<>(Arrays.asList(discriminatorColumns));

    for (final FieldSorting fieldSorting : sortOption.getFieldSortings()) {
      final var column = determineSearchColumn(fieldSorting.field());

      // remove the column from the discriminator list to not sort double
      discriminatorColumnList.remove(column);
      builder.addEntry(column, fieldSorting.order());
    }

    for (final SearchColumn<T> discriminatorColumn : discriminatorColumnList) {
      builder.addEntry(discriminatorColumn, SortOrder.ASC);
    }

    return builder.build();
  }

  public DbQueryPage convertPaging(final DbQuerySorting<T> sort, final SearchQueryPage page) {
    List<KeySetPagination> keySetPagination = new ArrayList<>();
    if (page.searchAfter() != null || page.searchBefore() != null) {
      keySetPagination = createKeySetPagination(sort, page);
    }

    return new DbQueryPage(page.size(), page.from(), keySetPagination);
  }

  /**
   * To create key set pagination for rdbms, supporting mixed order (e.g. <code>ORDER BY name ASC,
   * startDate DESC, key ASC</code>), a simple solution like
   *
   * <pre>WHERE (name, startDate, key) < ("Process A", "2024-10-10T08:00:00Z", 1234)</pre>
   *
   * <br>
   * does not work anymore. <br>
   * <br>
   * Instead, we need to split up the query into multiple comparisons:
   *
   * <pre>
   * WHERE (name > "Process A")
   *    OR (name = "Process A" AND startDate < "2024-10-10T08:00:00Z")
   *    OR (name = "Process A" AND startDate = "2024-10-10T08:00:00Z" AND key > 1234)
   * </pre>
   *
   * This method takes the sortOrder and the sortValues (before or after) and creates a list grouped
   * expressions. We do this in Java and not in MyBatis because it is easier to program and to test
   */
  private List<KeySetPagination> createKeySetPagination(
      final DbQuerySorting<T> sort, final SearchQueryPage page) {
    final boolean isSearchAfter = page.searchAfter() != null;
    final var cursorValue = isSearchAfter ? page.searchAfter() : page.searchBefore();
    final Object[] sortValues = cursor.decode(cursorValue, sort.columns());
    final List<KeySetPagination> keySetPagination = new ArrayList<>();

    for (int i = 0; i < sort.orderings().size(); i++) {
      final List<KeySetPaginationFieldEntry> fieldEntries = new ArrayList<>();
      // add entries for equals sorting
      for (int j = 0; j < i; j++) {
        final SearchColumn<?> sortColumn = sort.orderings().get(j).column();
        fieldEntries.add(
            new KeySetPaginationFieldEntry(sortColumn.name(), Operator.EQUALS, sortValues[j]));
      }

      // add comparator entry
      final var sorting = sort.orderings().get(i);
      fieldEntries.add(
          new KeySetPaginationFieldEntry(
              sorting.column().name(),
              determineOperator(sorting.order(), isSearchAfter),
              sortValues[i]));
      keySetPagination.add(new KeySetPagination(fieldEntries));
    }

    return keySetPagination;
  }

  protected final SearchQueryResult<T> buildSearchQueryResult(
      final long totalHits, final List<T> hits, final DbQuerySorting<T> dbSort) {
    final var result = new SearchQueryResult.Builder<T>().total(totalHits).items(hits);

    if (!hits.isEmpty()) {
      final var columns = dbSort.columns();
      result
          .firstSortValues(cursor.encode(hits.getFirst(), columns))
          .lastSortValues(cursor.encode(hits.getLast(), columns));
    }

    return result.build();
  }
}
