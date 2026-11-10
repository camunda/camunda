/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import org.jspecify.annotations.Nullable;

/**
 * Visits the current rows of a lookup in the order of an index on {@code (value, key)}, like a
 * RocksDB iterator over a column family keyed by value and key: the committed rows, read in pages
 * with keyset pagination, merged with the transaction's staged writes.
 *
 * <p>A page query selects the committed matching rows after a {@link Cursor}, ordered by value and
 * key, up to a limit. Because neither row-value comparisons nor every vendor's {@code LIMIT} are
 * portable, the query spells the cursor out as {@code value > ? OR (value = ? AND key > ?)} and
 * picks its vendor's limit clause.
 *
 * <p>Each page is merged with the staged writes before it is visited: a staged write hides the
 * committed row of its key, and the staged rows that match and sort into the page's range, from the
 * cursor up to the last committed row of the page (or without end on the last page), take their
 * place in the order. The committed rows of a page become loaded rows of the session, so reading
 * them again in the transaction, e.g. by the visitor, needs no query. The next page is only read if
 * the visitor keeps going; pages grow up to {@link #MAX_PAGE_SIZE}.
 *
 * <p>The scan runs in one transaction, so all its pages share a connection and the loaded rows. A
 * visitor may write the rows it visits: the cursor never returns to visited rows, and a later page
 * merges the writes.
 *
 * @param <E> the row
 * @param <M> the mapper of the page queries
 */
final class OrderedScan<E, M> {

  static final int MAX_PAGE_SIZE = 1024;

  private final EntitySession session;
  private final EntityType<Long, E> type;
  private final Class<M> mapperType;
  private final ToLongFunction<E> value;
  private final Comparator<E> order;

  /**
   * @param type the rows' type, keyed by a long
   * @param value the indexed value of a matching row, by which the rows are ordered before their
   *     key
   */
  OrderedScan(
      final EntitySession session,
      final EntityType<Long, E> type,
      final Class<M> mapperType,
      final ToLongFunction<E> value) {
    this.session = session;
    this.type = type;
    this.mapperType = mapperType;
    this.value = value;
    order = Comparator.comparingLong(value).thenComparingLong(type::keyOf);
  }

  /**
   * Visits the matching rows in order, from the start until the visitor returns false or no row is
   * left.
   *
   * @param startAt the position to start at (inclusive), or null to start at the first row
   * @param firstPageSize how many committed rows the first page reads at most
   * @param matches the condition of the page query, applied to the staged rows; it is tested before
   *     the {@code value} of a row is read
   * @param query selects the committed matching rows after a cursor
   * @param visitor visits the rows
   * @return the row at which the visitor returned false, or null if it didn't
   */
  @Nullable E visit(
      final @Nullable Cursor startAt,
      final int firstPageSize,
      final Predicate<? super E> matches,
      final PageQuery<M, E> query,
      final RowVisitor<E> visitor) {
    return session.inTransaction(
        () -> {
          @Nullable Cursor cursor = startAt;
          boolean inclusive = startAt != null;
          int limit = Math.min(Math.max(firstPageSize, 1), MAX_PAGE_SIZE);
          while (true) {
            final Cursor from = cursor;
            final boolean fromInclusive = inclusive;
            final int pageLimit = limit;
            final List<E> committed =
                session.read(
                    mapperType, mapper -> query.select(mapper, from, fromInclusive, pageLimit));
            final boolean lastPage = committed.size() < pageLimit;
            final Cursor to = lastPage ? null : cursorOf(committed.getLast());

            final List<E> page =
                session.query(
                    type,
                    committed,
                    row ->
                        matches.test(row)
                            && isAfter(row, from, fromInclusive)
                            && (to == null || compare(row, to) <= 0),
                    order);
            visitor.beforeVisiting(page);
            for (final E row : page) {
              if (!visitor.visit(row)) {
                return row;
              }
            }
            if (lastPage) {
              return null;
            }
            cursor = to;
            inclusive = false;
            limit = Math.min(limit * 2, MAX_PAGE_SIZE);
          }
        });
  }

  /** The position of the row in the order of the scan. */
  Cursor cursorOf(final E row) {
    return new Cursor(value.applyAsLong(row), type.keyOf(row));
  }

  private boolean isAfter(final E row, final @Nullable Cursor from, final boolean inclusive) {
    if (from == null) {
      return true;
    }
    final int comparison = compare(row, from);
    return inclusive ? comparison >= 0 : comparison > 0;
  }

  private int compare(final E row, final Cursor cursor) {
    final int byValue = Long.compare(value.applyAsLong(row), cursor.value());
    return byValue != 0 ? byValue : Long.compare(type.keyOf(row), cursor.key());
  }

  /** A position in the order of a scan: the indexed value and the key of a row. */
  record Cursor(long value, long key) {}

  /** Selects a page of committed rows. */
  @FunctionalInterface
  interface PageQuery<M, E> {

    /**
     * @param cursor the position after which (or at which, if inclusive) the page starts, or null
     *     to start at the first row
     * @param limit how many rows to select at most
     * @return the committed matching rows from the cursor on, ordered by value and key
     */
    List<E> select(M mapper, @Nullable Cursor cursor, boolean inclusive, int limit);
  }

  /** Visits the rows of a scan. */
  @FunctionalInterface
  interface RowVisitor<E> {

    /**
     * @return whether to continue with the next row
     */
    boolean visit(E row);

    /** Called with the rows of a page, in order, before they are visited. */
    default void beforeVisiting(final List<E> rows) {}
  }
}
