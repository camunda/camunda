/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static io.camunda.db.rdbms.read.domain.DbQueryPage.KeySetPaginationFieldEntry.determineOperator;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.DbQueryPage;
import io.camunda.db.rdbms.read.domain.DbQueryPage.KeySetPagination;
import io.camunda.db.rdbms.read.domain.DbQueryPage.KeySetPaginationFieldEntry;
import io.camunda.db.rdbms.read.domain.DbQueryPage.Operator;
import io.camunda.db.rdbms.read.domain.DbQuerySorting;
import io.camunda.db.rdbms.sql.columns.SearchColumn;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.page.SearchQueryPage.SearchQueryResultType;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.SortOption;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract class AbstractEntityReader<T> {

  private static final SearchColumn<?>[] EMPTY_SEARCHABLE_COLUMNS = new SearchColumn[0];
  private final Map<String, SearchColumn<T>> columns;
  private final RdbmsReaderConfig readerConfig;

  public AbstractEntityReader(
      final SearchColumn<T>[] searchableColumns, final RdbmsReaderConfig readerConfig) {
    final var searchColumns =
        Objects.requireNonNullElse(searchableColumns, (SearchColumn<T>[]) EMPTY_SEARCHABLE_COLUMNS);

    columns =
        Stream.of(searchColumns)
            .collect(Collectors.toMap(SearchColumn::property, Function.identity()));
    this.readerConfig = readerConfig;
  }

  private SearchColumn<T> getSearchColumn(final String property) {
    return Optional.ofNullable(columns.get(property))
        .orElseThrow(() -> new IllegalArgumentException("Unknown sortField: " + property));
  }

  @SafeVarargs
  public final DbQuerySorting<T> convertSort(
      final SortOption sortOption, final SearchColumn<T>... discriminatorColumns) {
    final var builder = new DbQuerySorting.Builder<T>();
    final var discriminatorColumnList = new ArrayList<>(Arrays.asList(discriminatorColumns));

    for (final FieldSorting fieldSorting : sortOption.getFieldSortings()) {
      final var column = getSearchColumn(fieldSorting.field());

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
    if (page.after() != null || page.before() != null) {
      keySetPagination = createKeySetPagination(sort, page);
    }

    if (SearchQueryResultType.UNLIMITED.equals(page.resultType())) {
      // assuming Integer.MAX_VALUE is enough
      return new DbQueryPage(Integer.MAX_VALUE, 0, keySetPagination);
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
    final boolean isSearchAfter = page.after() != null;
    final var cursorValue = isSearchAfter ? page.after() : page.before();
    final Object[] sortValues = Cursor.decode(cursorValue, sort.columns());
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

    if (!hits.isEmpty() && dbSort != null) {
      final var columns = dbSort.columns();
      result
          .startCursor(Cursor.encode(hits.getFirst(), columns))
          .endCursor(Cursor.encode(hits.getLast(), columns));
    }

    return result.build();
  }

  /**
   * Checks if the search result should be empty based on resource and tenant authorization.
   * Returns {@code true} if authorization is enabled but no authorized resource or tenant IDs are present.
   *
   * @param resourceAccessChecks the resource access checks containing authorization and tenant checks
   * @return {@code true} if the search result should be empty, {@code false
   */
  protected boolean shouldReturnEmptyResult(final ResourceAccessChecks resourceAccessChecks) {
    return noResourceAccess(resourceAccessChecks) || noTenantAccess(resourceAccessChecks);
  }

  protected boolean noResourceAccess(final ResourceAccessChecks resourceAccessChecks) {
    return !resourceAccessChecks.authorizationCheck().hasAnyResourceAccess();
  }

  protected boolean noTenantAccess(final ResourceAccessChecks resourceAccessChecks) {
    return !resourceAccessChecks.tenantCheck().hasAnyTenantAccess();
  }

  /**
   * Checks if the provided database query page should result in an empty page.
   *
   * @param page the database query page to check
   * @param totalHits the total number of hits
   * @return {@code true} if the page size is zero or total hits is zero, {@code false} otherwise
   */
  protected boolean shouldReturnEmptyPage(final DbQueryPage page, final long totalHits) {
    return page.size() == 0 || totalHits == 0;
  }

  /**
   * Executes a paged query using provided suppliers for count and results, handling empty page
   * logic.
   *
   * @param countSupplier supplies the total count of hits
   * @param resultsSupplier supplies the result list
   * @param page the database query page
   * @param dbSort the database query sorting
   * @return a SearchQueryResult containing the results and total count
   */
  protected SearchQueryResult<T> executePagedQuery(
      final Supplier<Long> countSupplier,
      final Supplier<List<T>> resultsSupplier,
      final DbQueryPage page,
      final DbQuerySorting<T> dbSort) {
    final long totalHits = countSupplier.get();
    if (shouldReturnEmptyPage(page, totalHits)) {
      return buildSearchQueryResult(totalHits, List.of(), dbSort);
    }
    final List<T> results = resultsSupplier.get();
    return buildSearchQueryResult(totalHits, results, dbSort);
  }
}
