/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.schema;

import java.util.ArrayList;
import java.util.List;
import liquibase.change.Change;
import liquibase.change.ColumnConfig;
import liquibase.change.ConstraintsConfig;
import liquibase.change.core.AddColumnChange;
import liquibase.change.core.AddForeignKeyConstraintChange;
import liquibase.change.core.AddNotNullConstraintChange;
import liquibase.change.core.AddUniqueConstraintChange;
import liquibase.change.core.CreateIndexChange;
import liquibase.change.core.CreateTableChange;
import liquibase.change.core.DropColumnChange;
import liquibase.change.core.DropIndexChange;
import liquibase.change.core.ModifyDataTypeChange;
import liquibase.change.core.RawSQLChange;
import liquibase.change.core.RenameColumnChange;
import liquibase.changelog.ChangeSet;

/**
 * Validates Liquibase changesets for rolling-upgrade compatibility.
 *
 * <p>During a rolling upgrade, some nodes still run version n-1 while others have already applied
 * the schema for version n. Schema changes must be strictly additive (expand-only) so that older
 * exporters can continue to write without errors.
 *
 * <h2>Allowed operations (expand-only)</h2>
 *
 * <ul>
 *   <li>{@code createTable} – creating a new table does not affect existing write paths; any column
 *       definition is acceptable because n-1 nodes never write to a brand-new table
 *   <li>{@code addColumn} – allowed only when the new column is nullable; n-1 nodes do not supply a
 *       value for the column, so it must be nullable
 *   <li>{@code createIndex} – a new index does not reject existing writes
 *   <li>{@code dropIndex} – removing an index does not affect writes (only read performance)
 *   <li>{@code sql} (raw SQL) – used for database-specific partial index creation ({@code CREATE
 *       INDEX WHERE ...}); safe from a rolling-upgrade perspective but must be manually reviewed
 * </ul>
 *
 * <h2>Forbidden operations</h2>
 *
 * <ul>
 *   <li>{@code dropColumn} – breaks writes from n-1 that still include the column
 *   <li>{@code renameColumn} – breaks writes from n-1 that reference the old name
 *   <li>{@code modifyDataType} – may break writes from n-1 (e.g. type narrowing)
 *   <li>{@code addNotNullConstraint} on an existing column – rejects null writes from n-1
 *   <li>{@code addUniqueConstraint} on an existing write path – rejects duplicate writes
 *   <li>{@code addForeignKeyConstraint} on an existing write path – rejects orphaned writes
 *   <li>any other undeclared operation – rejected by default (allowlist approach)
 * </ul>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * var violations = RollingUpgradeCompatibilityValidator.validate(changeSets);
 * if (!violations.isEmpty()) {
 *     violations.forEach(System.err::println);
 *     throw new AssertionError("Schema changes are not rolling-upgrade compatible");
 * }
 * }</pre>
 */
public final class RollingUpgradeCompatibilityValidator {

  private RollingUpgradeCompatibilityValidator() {}

  /**
   * Validates the given changesets against rolling-upgrade-safe rules.
   *
   * @param changeSets the Liquibase changesets to validate
   * @return a list of human-readable violation messages; empty when all changesets are compatible
   */
  public static List<String> validate(final List<ChangeSet> changeSets) {
    final var violations = new ArrayList<String>();
    for (final var changeSet : changeSets) {
      for (final var change : changeSet.getChanges()) {
        violations.addAll(validateChange(changeSet.getId(), change));
      }
    }
    return violations;
  }

  private static List<String> validateChange(final String changeSetId, final Change change) {
    if (change instanceof CreateTableChange) {
      // new tables have no n-1 write paths; any column definition is acceptable
      return List.of();
    } else if (change instanceof final AddColumnChange addColumn) {
      return validateAddColumn(changeSetId, addColumn);
    } else if (change instanceof CreateIndexChange) {
      // creating an index never rejects existing writes
      return List.of();
    } else if (change instanceof DropIndexChange) {
      // dropping an index does not affect write paths
      return List.of();
    } else if (change instanceof RawSQLChange) {
      // raw SQL is used for database-specific partial index creation (CREATE INDEX WHERE ...);
      // it is safe from a rolling-upgrade perspective but must be manually reviewed
      return List.of();
    } else if (change instanceof final DropColumnChange dropColumn) {
      return List.of(
          formatViolation(
              changeSetId,
              "Dropping column '%s' from table '%s' is not allowed in the rolling-upgrade-compatible"
                  + " phase. Defer destructive changes to a later minor release.",
              dropColumn.getColumnName(),
              dropColumn.getTableName()));
    } else if (change instanceof final RenameColumnChange renameColumn) {
      return List.of(
          formatViolation(
              changeSetId,
              "Renaming column '%s' to '%s' on table '%s' is not allowed in the"
                  + " rolling-upgrade-compatible phase. Rename breaks writes from n-1 nodes that"
                  + " still use the old column name.",
              renameColumn.getOldColumnName(),
              renameColumn.getNewColumnName(),
              renameColumn.getTableName()));
    } else if (change instanceof final ModifyDataTypeChange modifyType) {
      return List.of(
          formatViolation(
              changeSetId,
              "Changing data type of column '%s' on table '%s' to '%s' is not allowed in the"
                  + " rolling-upgrade-compatible phase. Type changes can break writes from n-1"
                  + " nodes.",
              modifyType.getColumnName(),
              modifyType.getTableName(),
              modifyType.getNewDataType()));
    } else if (change instanceof final AddNotNullConstraintChange notNull) {
      return List.of(
          formatViolation(
              changeSetId,
              "Adding NOT NULL constraint to existing column '%s' on table '%s' is not allowed in"
                  + " the rolling-upgrade-compatible phase. This rejects null writes from n-1"
                  + " nodes.",
              notNull.getColumnName(),
              notNull.getTableName()));
    } else if (change instanceof final AddUniqueConstraintChange uniqueConstraint) {
      return List.of(
          formatViolation(
              changeSetId,
              "Adding UNIQUE constraint '%s' on table '%s' column(s) '%s' is not allowed in the"
                  + " rolling-upgrade-compatible phase. This can reject duplicate writes from n-1"
                  + " nodes.",
              uniqueConstraint.getConstraintName(),
              uniqueConstraint.getTableName(),
              uniqueConstraint.getColumnNames()));
    } else if (change instanceof final AddForeignKeyConstraintChange fkConstraint) {
      return List.of(
          formatViolation(
              changeSetId,
              "Adding FOREIGN KEY constraint '%s' on table '%s' column '%s' is not allowed in the"
                  + " rolling-upgrade-compatible phase. This can reject orphaned writes from n-1"
                  + " nodes.",
              fkConstraint.getConstraintName(),
              fkConstraint.getBaseTableName(),
              fkConstraint.getBaseColumnNames()));
    } else {
      // All other operations are forbidden by default (allowlist approach)
      return List.of(
          formatViolation(
              changeSetId,
              "Change type '%s' is not in the allowlist of rolling-upgrade-safe operations."
                  + " Allowed: createTable, addColumn (nullable), createIndex, dropIndex, sql"
                  + " (raw). Defer other changes to a later minor release.",
              change.getClass().getSimpleName()));
    }
  }

  private static List<String> validateAddColumn(
      final String changeSetId, final AddColumnChange addColumn) {
    final var violations = new ArrayList<String>();
    for (final var column : addColumn.getColumns()) {
      if (isNonNullable(column)) {
        violations.add(
            formatViolation(
                changeSetId,
                "Column '%s' added to table '%s' must be nullable for rolling-upgrade"
                    + " compatibility. n-1 nodes do not know about this column and will not supply"
                    + " a value.",
                column.getName(),
                addColumn.getTableName()));
      }
    }
    return violations;
  }

  /**
   * Returns {@code true} when the column is explicitly marked NOT NULL.
   *
   * <p>A column is considered non-nullable if its {@link ConstraintsConfig} sets {@code
   * nullable="false"}.
   */
  static boolean isNonNullable(final ColumnConfig column) {
    final var constraints = column.getConstraints();
    if (constraints == null) {
      return false;
    }
    final var nullable = constraints.isNullable();
    // isNullable() returns null when not explicitly set – that means nullable by default
    return Boolean.FALSE.equals(nullable);
  }

  private static String formatViolation(
      final String changeSetId, final String template, final Object... args) {
    return "[changeset: " + changeSetId + "] " + String.format(template, args);
  }
}
