/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.filter.VariableFilter;
import io.camunda.search.filter.VariableValueFilter;
import java.util.List;

/**
 * Since the operator fields in the VariableValueFilter are reserved keywords in mybatis (OGNL
 * expression language), we need our own representation of it. (VariableValueDbFilter)
 */
public record VariableDbFilter(
    List<VariableValueDbFilter> variableFilters,
    List<Long> scopeKeys,
    List<Long> processInstanceKeys,
    List<Long> variableKeys,
    List<String> tenantIds) {

  public static VariableDbFilter of(final VariableFilter filter) {
    return new VariableDbFilter(
        map(filter.variableFilters()),
        filter.scopeKeys(),
        filter.processInstanceKeys(),
        filter.variableKeys(),
        filter.tenantIds());
  }

  static List<VariableValueDbFilter> map(final List<VariableValueFilter> filters) {
    if (filters != null && !filters.isEmpty()) {
      return filters.stream().map(VariableDbFilter::map).toList();
    } else {
      return null;
    }
  }

  static VariableValueDbFilter map(final VariableValueFilter valueFilter) {
    String operator = null;
    Object value = null;
    Boolean numericNeeded = false;

    if (valueFilter.eq() != null) {
      operator = "=";
      value = valueFilter.eq();
    } else if (valueFilter.neq() != null) {
      operator = "!=";
      value = valueFilter.neq();
    } else if (valueFilter.gt() != null) {
      operator = ">";
      value = valueFilter.gt();
      numericNeeded = true;
    } else if (valueFilter.gte() != null) {
      operator = ">=";
      value = valueFilter.gte();
      numericNeeded = true;
    } else if (valueFilter.lt() != null) {
      operator = "<";
      value = valueFilter.lt();
      numericNeeded = true;
    } else if (valueFilter.lte() != null) {
      operator = "<=";
      value = valueFilter.lte();
      numericNeeded = true;
    }

    return new VariableValueDbFilter(valueFilter.name(), operator, value, numericNeeded);
  }

  public record VariableValueDbFilter(
      String name, String operator, Object value, Boolean numericNeeded) {}
}
