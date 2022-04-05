/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.entities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Results<T> {

  private List<T> items = new ArrayList<>();
  private Object[] sortValues = new Object[]{};

  private long total;

  public long getTotal() {
    return total;
  }

  public Results<T> setTotal(final long total) {
    this.total = total;
    return this;
  }

  public List<T> getItems() {
    return items;
  }

  public Results<T> setItems(final List<T> items) {
    this.items = items;
    return this;
  }

  public Object[] getSortValues() {
    return sortValues;
  }

  public Results<T> setSortValues(final Object[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Results<?> results = (Results<?>) o;
    return total == results.total && Objects.equals(items, results.items)
        && Arrays.equals(sortValues, results.sortValues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(items, Arrays.hashCode(sortValues), total);
  }

  @Override
  public String toString() {
    return "Results{" +
        "items=" + items +
        ", sortValues=" + Arrays.toString(sortValues) +
        ", total=" + total +
        '}';
  }
}
