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
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(TableColumnDto.class);

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
      LOG.debug(
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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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

  private static boolean defaultIncludeNewVariables() {
    return false;
  }

  private static List<String> defaultExcludedColumns() {
    return new ArrayList<>();
  }

  private static List<String> defaultIncludedColumns() {
    return new ArrayList<>();
  }

  private static List<String> defaultColumnOrder() {
    return new ArrayList<>();
  }

  public static TableColumnDtoBuilder builder() {
    return new TableColumnDtoBuilder();
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String includeNewVariables = "includeNewVariables";
    public static final String excludedColumns = "excludedColumns";
    public static final String includedColumns = "includedColumns";
    public static final String columnOrder = "columnOrder";
  }

  public static class TableColumnDtoBuilder {

    private boolean includeNewVariablesValue;
    private boolean includeNewVariablesSet;
    private List<String> excludedColumnsValue;
    private boolean excludedColumnsSet;
    private List<String> includedColumnsValue;
    private boolean includedColumnsSet;
    private List<String> columnOrderValue;
    private boolean columnOrderSet;

    TableColumnDtoBuilder() {}

    public TableColumnDtoBuilder includeNewVariables(final boolean includeNewVariables) {
      includeNewVariablesValue = includeNewVariables;
      includeNewVariablesSet = true;
      return this;
    }

    public TableColumnDtoBuilder excludedColumns(final List<String> excludedColumns) {
      excludedColumnsValue = excludedColumns;
      excludedColumnsSet = true;
      return this;
    }

    public TableColumnDtoBuilder includedColumns(final List<String> includedColumns) {
      includedColumnsValue = includedColumns;
      includedColumnsSet = true;
      return this;
    }

    public TableColumnDtoBuilder columnOrder(final List<String> columnOrder) {
      columnOrderValue = columnOrder;
      columnOrderSet = true;
      return this;
    }

    public TableColumnDto build() {
      boolean includeNewVariablesValue = this.includeNewVariablesValue;
      if (!includeNewVariablesSet) {
        includeNewVariablesValue = TableColumnDto.defaultIncludeNewVariables();
      }
      List<String> excludedColumnsValue = this.excludedColumnsValue;
      if (!excludedColumnsSet) {
        excludedColumnsValue = TableColumnDto.defaultExcludedColumns();
      }
      List<String> includedColumnsValue = this.includedColumnsValue;
      if (!includedColumnsSet) {
        includedColumnsValue = TableColumnDto.defaultIncludedColumns();
      }
      List<String> columnOrderValue = this.columnOrderValue;
      if (!columnOrderSet) {
        columnOrderValue = TableColumnDto.defaultColumnOrder();
      }
      return new TableColumnDto(
          includeNewVariablesValue, excludedColumnsValue, includedColumnsValue, columnOrderValue);
    }

    @Override
    public String toString() {
      return "TableColumnDto.TableColumnDtoBuilder(includeNewVariablesValue="
          + includeNewVariablesValue
          + ", excludedColumnsValue="
          + excludedColumnsValue
          + ", includedColumnsValue="
          + includedColumnsValue
          + ", columnOrderValue="
          + columnOrderValue
          + ")";
    }
  }
}
