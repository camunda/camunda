/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(asEnum = true)
@EqualsAndHashCode
@Builder
public class TableColumnDto {
  public static final String VARIABLE_PREFIX = "variable:";
  public static final String INPUT_PREFIX = "input:";
  public static final String OUTPUT_PREFIX = "output:";

  @Builder.Default
  private boolean includeNewVariables = true;
  @Builder.Default
  private List<String> excludedColumns = new ArrayList<>();
  @Builder.Default
  private List<String> includedColumns = new ArrayList<>();
  @Builder.Default
  private List<String> columnOrder = new ArrayList<>();

  public void addNewAndRemoveUnexpectedVariableColumns(final List<String> allVariableColumns) {
    addNewVariableColumns(allVariableColumns);

    // remove all variable columns that are in excluded/included lists but not in allVariableColumns
    final List<String> variableColumnsToRemove = new ArrayList<>();
    variableColumnsToRemove.addAll(getAllVariablePrefixedColumns(excludedColumns));
    variableColumnsToRemove.addAll(getAllVariablePrefixedColumns(includedColumns));
    variableColumnsToRemove.removeAll(allVariableColumns);
    removeColumns(variableColumnsToRemove);
  }

  public void addDtoColumns(final List<String> columns) {
    // Dto columns are always included by default, unless explicitly excluded
    final List<String> newColumnsToAdd = columns
      .stream()
      .filter(col -> !excludedColumns.contains(col))
      .filter(col -> !includedColumns.contains(col))
      .distinct()
      .collect(toList());
    includedColumns.addAll(newColumnsToAdd);
  }

  public List<String> getIncludedColumns() {
    // if a column is in both lists, excluded always wins
    includedColumns.removeAll(excludedColumns);
    sortIncludedColumns();
    return includedColumns;
  }

  private void addNewVariableColumns(final List<String> variableColumns) {
    final List<String> newColumnsToAdd = variableColumns
      .stream()
      .filter(col -> !excludedColumns.contains(col))
      .filter(col -> !includedColumns.contains(col))
      .distinct()
      .collect(toList());
    if (includeNewVariables) {
      includedColumns.addAll(newColumnsToAdd);
    } else {
      excludedColumns.addAll(newColumnsToAdd);
    }
  }

  private void removeColumns(final List<String> columnsToRemove) {
    excludedColumns.removeAll(columnsToRemove);
    includedColumns.removeAll(columnsToRemove);
  }

  private void sortIncludedColumns() {
    includedColumns.sort(Comparator.comparingInt(this::getNumbersInColumnName));
    includedColumns.sort(getStringColumnComparator());
  }

  private Comparator<String> getStringColumnComparator() {
    return Comparator.comparing(this::getPrefixOrder)
      .thenComparing(this::getColumnNameWithoutPrefixAndNumbers);
  }

  private int getPrefixOrder(final String columnName) {
    // Order of columns should be:
    // non-variable (ie dto fields) > process var > decision input var > decision output var
    if (columnName.startsWith(VARIABLE_PREFIX)) {
      return 1;
    } else if (columnName.startsWith(INPUT_PREFIX)) {
      return 2;
    } else if (columnName.startsWith(OUTPUT_PREFIX)) {
      return 3;
    }
    return 0;
  }

  private String getColumnNameWithoutPrefixAndNumbers(String columnName) {
    if (getPrefixOrder(columnName) == 0) {
      // do not sort dto fields to keep them in original order
      return "";
    }
    return columnName.replace(VARIABLE_PREFIX, "")
      .replace(INPUT_PREFIX, "")
      .replace(OUTPUT_PREFIX, "")
      .replaceAll("[0-9]", "");
  }

  private int getNumbersInColumnName(String columnName) {
    String digitsInString = columnName.replaceAll("\\D+", "");
    return digitsInString.isEmpty()
      ? 0
      : Integer.parseInt(digitsInString);
  }

  private List<String> getAllVariablePrefixedColumns(List<String> columns) {
    return columns.stream()
      .filter(col -> col.contains(VARIABLE_PREFIX)
        || col.contains(INPUT_PREFIX)
        || col.contains(OUTPUT_PREFIX))
      .collect(toList());
  }
}
