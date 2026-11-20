/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.kit;

import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where a controlled thread stops: before a statement (or one entry of a JDBC batch), or before
 * commit. Match it with the factories, by what the writer code shows:
 *
 * <pre>{@code
 * thread.stepTo(update("BATCH_OPERATION"));                       // operation and table
 * thread.stepTo(update("FLOW_NODE_INSTANCE").entry(2));          // the 2nd row of that batch
 * thread.stepTo(statement("BatchOperationMapper.upsertItem"));   // MyBatis statement id
 * thread.stepTo(commit());
 * }</pre>
 *
 * @param operation SELECT, INSERT, UPDATE, DELETE or MERGE; COMMIT for the commit
 * @param table the table the statement writes or reads, upper case; empty for the commit
 * @param statementId the MyBatis statement, e.g. {@code BatchOperationMapper.upsertItem}
 * @param entry the entry of the JDBC batch, from 1; 1 for a single statement
 * @param entries the entries of the JDBC batch; 1 for a single statement
 * @param sql the SQL text, whitespace collapsed
 */
public record SyncPoint(
    String operation, String table, String statementId, int entry, int entries, String sql) {

  private static final String MAPPER_PACKAGE = "io.camunda.db.rdbms.sql.";
  private static final Pattern TARGET =
      Pattern.compile(
          "^\\s*(?:INSERT\\s+(?:ALL\\s+)?(?:IGNORE\\s+)?INTO|UPDATE|DELETE\\s+(?:TOP\\s*\\([^)]*\\)\\s*)?FROM"
              + "|MERGE\\s+(?:INTO\\s+)?|SELECT\\s.*?\\sFROM)\\s+([\\w.\"`\\[\\]]+)",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  static SyncPoint commitPoint() {
    return new SyncPoint("COMMIT", "", "", 1, 1, "");
  }

  static SyncPoint of(
      final String statementId, final String sql, final int entry, final int entries) {
    final String operation = sql.strip().split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
    final Matcher target = TARGET.matcher(sql);
    final String table =
        target.find()
            ? target
                .group(1)
                .replaceAll("[\"`\\[\\]]", "")
                .replaceAll("^.*\\.", "")
                .toUpperCase(Locale.ROOT)
            : "";
    return new SyncPoint(
        operation, table, statementId.replace(MAPPER_PACKAGE, ""), entry, entries, sql);
  }

  public boolean isCommit() {
    return operation.equals("COMMIT");
  }

  /** E.g. {@code UPDATE BATCH_OPERATION [1/2] (BatchOperationMapper.incrementCompleted...)}. */
  @Override
  public String toString() {
    return isCommit()
        ? "commit"
        : operation + " " + table + " [" + entry + "/" + entries + "] (" + statementId + ")";
  }

  // ---------------------------------------------------------------- matchers

  /** Any sync point: the next one. */
  public static Match any() {
    return new Match("any sync point", p -> true);
  }

  public static Match commit() {
    return new Match("commit", SyncPoint::isCommit);
  }

  public static Match insert(final String table) {
    return operationOn("INSERT", table);
  }

  public static Match update(final String table) {
    return operationOn("UPDATE", table);
  }

  public static Match delete(final String table) {
    return operationOn("DELETE", table);
  }

  /**
   * An insert-or-update; vendors differ ({@code INSERT ... ON CONFLICT}, {@code INSERT IGNORE},
   * {@code MERGE}). Prefer {@link #statement} if the table has several of them.
   */
  public static Match upsert(final String table) {
    return new Match(
        "upsert " + table,
        p ->
            (p.operation.equals("INSERT") || p.operation.equals("MERGE"))
                && p.table.equalsIgnoreCase(table));
  }

  /**
   * A MyBatis statement, as the writer names it, with or without the mapper: {@code
   * "BatchOperationMapper.incrementCompletedOperationsCount"}.
   */
  public static Match statement(final String statementId) {
    return new Match(
        "statement " + statementId,
        p -> p.statementId.equals(statementId) || p.statementId.endsWith("." + statementId));
  }

  private static Match operationOn(final String operation, final String table) {
    return new Match(
        operation + " " + table,
        p -> p.operation.equals(operation) && p.table.equalsIgnoreCase(table));
  }

  /** A sync point pattern; {@link #entry} narrows it to one entry of a JDBC batch. */
  public static final class Match implements Predicate<SyncPoint> {
    private final String description;
    private final Predicate<SyncPoint> predicate;

    private Match(final String description, final Predicate<SyncPoint> predicate) {
      this.description = description;
      this.predicate = predicate;
    }

    /** This or the other sync point, whichever comes first. */
    public Match or(final Match other) {
      return new Match(description + " or " + other.description, predicate.or(other.predicate));
    }

    /** Only the given entry (from 1) of the JDBC batch, i.e. before the n-th row. */
    public Match entry(final int entry) {
      return new Match(description + " [" + entry + "/n]", predicate.and(p -> p.entry == entry));
    }

    @Override
    public boolean test(final SyncPoint syncPoint) {
      return predicate.test(syncPoint);
    }

    @Override
    public String toString() {
      return description;
    }
  }
}
