/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;

public class TableColumnDto {

  public static final String VARIABLE_PREFIX = "variable:";
  public static final String INPUT_PREFIX = "input:";
  public static final String OUTPUT_PREFIX = "output:";
  public static final String FLOWNODE_DURATION_PREFIX = "dur:";
  public static final String COUNT_PREFIX = "count:";
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(TableColumnDto.class);

  private boolean includeNewVariables = false;
  private List<String> excludedColumns = new ArrayList<>();
  private List<String> includedColumns = new ArrayList<>();
  private List<String> columnOrder = new ArrayList<>();

  public TableColumnDto(
      final boolean includeNewVariables,
      final List<String> excludedColumns,
      final List<String> includedColumns,
      final List<String> columnOrder) {
    this.includeNewVariables = includeNewVariables;
    this.excludedColumns = excludedColumns;
    this.includedColumns = includedColumns;
    this.columnOrder = columnOrder;
  }

  public TableColumnDto() {}

  public void addNewAndRemoveUnexpectedVariableColumns(final List<String> allVariableColumns) {
    final List<String> newColumns = determineNewColumns(allVariableColumns);
    if (includeNewVariables) {
      includedColumns.addAll(newColumns);
    } else {
      excludedColumns.addAll(newColumns);
    }
    removeUnexpectedColumns(allVariableColumns, VARIABLE_PREFIX, INPUT_PREFIX, OUTPUT_PREFIX);
  }

  public void addNewAndRemoveUnexpectedFlowNodeDurationColumns(
      final List<String> flowNodeDurationColumns) {
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

  public void setIncludedColumns(final List<String> includedColumns) {
    this.includedColumns = includedColumns;
  }

  private List<String> determineNewColumns(final List<String> allColumns) {
    return allColumns.stream()
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
    // non-variable (ie dto fields) > count fields > flow node duration fields> process var >
    // decision input var > decision
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

  private String getColumnNameWithoutPrefixAndNumbers(final String columnName) {
    if (getPrefixOrder(columnName) == 0) {
      // do not sort dto fields to keep them in original order
      return "";
    }
    return columnName
        .replace(VARIABLE_PREFIX, "")
        .replace(INPUT_PREFIX, "")
        .replace(OUTPUT_PREFIX, "")
        .replaceAll("[0-9]", "");
  }

  private void removeUnexpectedColumns(
      final List<String> columns, final String... prefixesToFilerFor) {
    // remove all columns that are in excluded/included lists but not in provided list
    final List<String> prefixList = Arrays.asList(prefixesToFilerFor);
    final List<String> columnsToRemove =
        Stream.concat(excludedColumns.stream(), includedColumns.stream())
            .filter(
                includedOrExcludedColumn ->
                    prefixList.stream().anyMatch(includedOrExcludedColumn::contains))
            .filter(prefixedColumn -> !columns.contains(prefixedColumn))
            .collect(toList());
    removeColumns(columnsToRemove);
  }

  private double getNumbersInColumnName(final String columnName) {
    final String digitsInString = columnName.replaceAll("\\D+", "");
    try {
      return digitsInString.isEmpty() ? 0 : Double.parseDouble(digitsInString);
    } catch (final NumberFormatException e) {
      log.debug(
          "Cannot parse numbers in variable column names to double, ignoring and sorting by string.",
          e);
      return Double.MAX_VALUE;
    }
  }

  public boolean isIncludeNewVariables() {
    return includeNewVariables;
  }

  public void setIncludeNewVariables(final boolean includeNewVariables) {
    this.includeNewVariables = includeNewVariables;
  }

  public List<String> getExcludedColumns() {
    return excludedColumns;
  }

  public void setExcludedColumns(final List<String> excludedColumns) {
    this.excludedColumns = excludedColumns;
  }

  public List<String> getColumnOrder() {
    return columnOrder;
  }

  public void setColumnOrder(final List<String> columnOrder) {
    this.columnOrder = columnOrder;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof TableColumnDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + (isIncludeNewVariables() ? 79 : 97);
    final Object $excludedColumns = getExcludedColumns();
    result = result * PRIME + ($excludedColumns == null ? 43 : $excludedColumns.hashCode());
    final Object $includedColumns = getIncludedColumns();
    result = result * PRIME + ($includedColumns == null ? 43 : $includedColumns.hashCode());
    final Object $columnOrder = getColumnOrder();
    result = result * PRIME + ($columnOrder == null ? 43 : $columnOrder.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof TableColumnDto)) {
      return false;
    }
    final TableColumnDto other = (TableColumnDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (isIncludeNewVariables() != other.isIncludeNewVariables()) {
      return false;
    }
    final Object this$excludedColumns = getExcludedColumns();
    final Object other$excludedColumns = other.getExcludedColumns();
    if (this$excludedColumns == null
        ? other$excludedColumns != null
        : !this$excludedColumns.equals(other$excludedColumns)) {
      return false;
    }
    final Object this$includedColumns = getIncludedColumns();
    final Object other$includedColumns = other.getIncludedColumns();
    if (this$includedColumns == null
        ? other$includedColumns != null
        : !this$includedColumns.equals(other$includedColumns)) {
      return false;
    }
    final Object this$columnOrder = getColumnOrder();
    final Object other$columnOrder = other.getColumnOrder();
    if (this$columnOrder == null
        ? other$columnOrder != null
        : !this$columnOrder.equals(other$columnOrder)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "TableColumnDto(includeNewVariables="
        + isIncludeNewVariables()
        + ", excludedColumns="
        + getExcludedColumns()
        + ", includedColumns="
        + getIncludedColumns()
        + ", columnOrder="
        + getColumnOrder()
        + ")";
  }

  private static boolean $default$includeNewVariables() {
    return false;
  }

  private static List<String> $default$excludedColumns() {
    return new ArrayList<>();
  }

  private static List<String> $default$includedColumns() {
    return new ArrayList<>();
  }

  private static List<String> $default$columnOrder() {
    return new ArrayList<>();
  }

  public static TableColumnDtoBuilder builder() {
    return new TableColumnDtoBuilder();
  }

  public static final class Fields {

    public static final String includeNewVariables = "includeNewVariables";
    public static final String excludedColumns = "excludedColumns";
    public static final String includedColumns = "includedColumns";
    public static final String columnOrder = "columnOrder";
  }

  public static class TableColumnDtoBuilder {

    private boolean includeNewVariables$value;
    private boolean includeNewVariables$set;
    private List<String> excludedColumns$value;
    private boolean excludedColumns$set;
    private List<String> includedColumns$value;
    private boolean includedColumns$set;
    private List<String> columnOrder$value;
    private boolean columnOrder$set;

    TableColumnDtoBuilder() {}

    public TableColumnDtoBuilder includeNewVariables(final boolean includeNewVariables) {
      includeNewVariables$value = includeNewVariables;
      includeNewVariables$set = true;
      return this;
    }

    public TableColumnDtoBuilder excludedColumns(final List<String> excludedColumns) {
      excludedColumns$value = excludedColumns;
      excludedColumns$set = true;
      return this;
    }

    public TableColumnDtoBuilder includedColumns(final List<String> includedColumns) {
      includedColumns$value = includedColumns;
      includedColumns$set = true;
      return this;
    }

    public TableColumnDtoBuilder columnOrder(final List<String> columnOrder) {
      columnOrder$value = columnOrder;
      columnOrder$set = true;
      return this;
    }

    public TableColumnDto build() {
      boolean includeNewVariables$value = this.includeNewVariables$value;
      if (!includeNewVariables$set) {
        includeNewVariables$value = TableColumnDto.$default$includeNewVariables();
      }
      List<String> excludedColumns$value = this.excludedColumns$value;
      if (!excludedColumns$set) {
        excludedColumns$value = TableColumnDto.$default$excludedColumns();
      }
      List<String> includedColumns$value = this.includedColumns$value;
      if (!includedColumns$set) {
        includedColumns$value = TableColumnDto.$default$includedColumns();
      }
      List<String> columnOrder$value = this.columnOrder$value;
      if (!columnOrder$set) {
        columnOrder$value = TableColumnDto.$default$columnOrder();
      }
      return new TableColumnDto(
          includeNewVariables$value,
          excludedColumns$value,
          includedColumns$value,
          columnOrder$value);
    }

    @Override
    public String toString() {
      return "TableColumnDto.TableColumnDtoBuilder(includeNewVariables$value="
          + includeNewVariables$value
          + ", excludedColumns$value="
          + excludedColumns$value
          + ", includedColumns$value="
          + includedColumns$value
          + ", columnOrder$value="
          + columnOrder$value
          + ")";
    }
  }
}
