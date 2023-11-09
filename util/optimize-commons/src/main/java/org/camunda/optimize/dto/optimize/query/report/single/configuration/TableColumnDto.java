/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
@Builder
@Data
@Slf4j
public class TableColumnDto {
  public static final String VARIABLE_PREFIX = "variable:";
  public static final String INPUT_PREFIX = "input:";
  public static final String OUTPUT_PREFIX = "output:";
  public static final String FLOWNODE_DURATION_PREFIX = "dur:";
  public static final String COUNT_PREFIX = "count:";

  @Builder.Default
  private boolean includeNewVariables = false;
  @Builder.Default
  private List<String> excludedColumns = new ArrayList<>();
  @Builder.Default
  private List<String> includedColumns = new ArrayList<>();
  @Builder.Default
  private List<String> columnOrder = new ArrayList<>();

  public void addNewAndRemoveUnexpectedVariableColumns(final List<String> allVariableColumns) {
    final List<String> newColumns = determineNewColumns(allVariableColumns);
    if (includeNewVariables) {
      includedColumns.addAll(newColumns);
    } else {
      excludedColumns.addAll(newColumns);
    }
    removeUnexpectedColumns(allVariableColumns, VARIABLE_PREFIX, INPUT_PREFIX, OUTPUT_PREFIX);
  }

  public void addNewAndRemoveUnexpectedFlowNodeDurationColumns(final List<String> flowNodeDurationColumns) {
    final List<String> newColumns = determineNewColumns(flowNodeDurationColumns);
    // This is a known bug that needs addressing with https://jira.camunda.com/browse/OPT-7155
    if (includeNewVariables) {
      includedColumns.addAll(newColumns);
    } else {
      excludedColumns.addAll(newColumns);
    }
    removeUnexpectedColumns(flowNodeDurationColumns, FLOWNODE_DURATION_PREFIX);
  }

  public void addCountColumns(final List<String> countColumns) {
    // Count columns are always excluded by default, unless explicitly included
    final List<String> countColumnsNotIncludedOrExcluded = determineNewColumns(countColumns);
    excludedColumns.addAll(countColumnsNotIncludedOrExcluded);
  }

  public void addDtoColumns(final List<String> columns) {
    // Dto columns are always included by default, unless explicitly excluded
    final List<String> newColumnsToAdd = determineNewColumns(columns);
    includedColumns.addAll(newColumnsToAdd);
  }

  public List<String> getIncludedColumns() {
    // if a column is in both lists, excluded always wins
    includedColumns.removeAll(excludedColumns);
    sortIncludedColumns();
    return includedColumns;
  }

  private List<String> determineNewColumns(final List<String> allColumns) {
    return allColumns
      .stream()
      .filter(col -> !excludedColumns.contains(col))
      .filter(col -> !includedColumns.contains(col))
      .distinct()
      .collect(toList());
  }

  private void removeColumns(final List<String> columnsToRemove) {
    excludedColumns.removeAll(columnsToRemove);
    includedColumns.removeAll(columnsToRemove);
  }

  private void sortIncludedColumns() {
    includedColumns.sort(Comparator.comparingDouble(this::getNumbersInColumnName));
    includedColumns.sort(getStringColumnComparator());
  }

  private Comparator<String> getStringColumnComparator() {
    return Comparator.comparing(this::getPrefixOrder)
      .thenComparing(this::getColumnNameWithoutPrefixAndNumbers);
  }

  private int getPrefixOrder(final String columnName) {
    // Order of columns should be:
    // non-variable (ie dto fields) > count fields > flow node duration fields> process var > decision input var > decision
    // output var
    if (columnName.startsWith(COUNT_PREFIX)) {
      return 1;
    } else if (columnName.startsWith(FLOWNODE_DURATION_PREFIX)) {
      return 2;
    } else if (columnName.startsWith(VARIABLE_PREFIX)) {
      return 3;
    } else if (columnName.startsWith(INPUT_PREFIX)) {
      return 4;
    } else if (columnName.startsWith(OUTPUT_PREFIX)) {
      return 5;
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

  private void removeUnexpectedColumns(final List<String> columns, final String... prefixesToFilerFor) {
    // remove all columns that are in excluded/included lists but not in provided list
    final List<String> prefixList = Arrays.asList(prefixesToFilerFor);
    final List<String> columnsToRemove = Stream.concat(excludedColumns.stream(), includedColumns.stream())
      .filter(includedOrExcludedColumn -> prefixList.stream().anyMatch(includedOrExcludedColumn::contains))
      .filter(prefixedColumn -> !columns.contains(prefixedColumn))
      .collect(toList());
    removeColumns(columnsToRemove);
  }

  private double getNumbersInColumnName(String columnName) {
    String digitsInString = columnName.replaceAll("\\D+", "");
    try {
      return digitsInString.isEmpty()
        ? 0
        : Double.parseDouble(digitsInString);
    } catch (NumberFormatException e) {
      log.debug(
        "Cannot parse numbers in variable column names to double, ignoring and sorting by string.",
        e
      );
      return Double.MAX_VALUE;
    }
  }

}
