package org.camunda.optimize.dto.optimize.query.report.single.sorting;

import java.util.Optional;

public class SortingDto {
  public static final String SORT_BY_KEY = "key";
  public static final String SORT_BY_VALUE = "value";

  private String by;
  private SortOrder order;

  protected SortingDto() {
  }

  public SortingDto(String by, SortOrder order) {
    this.by = by;
    this.order = order;
  }

  public Optional<String> getBy() {
    return Optional.ofNullable(by);
  }

  public Optional<SortOrder> getOrder() {
    return Optional.ofNullable(order);
  }

  @Override
  public String toString() {
    return "SortingDto{" +
      "by='" + by + '\'' +
      ", order=" + order +
      '}';
  }
}
