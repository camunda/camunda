package org.camunda.optimize.dto.optimize.query.report.single.sorting;

public class SortingDto {
  private String by;
  private SortOrder order;

  protected SortingDto() {
  }

  public SortingDto(String by, SortOrder order) {
    this.by = by;
    this.order = order;
  }

  public String getBy() {
    return by;
  }

  public SortOrder getOrder() {
    return order;
  }

  @Override
  public String toString() {
    return "SortingDto{" +
      "by='" + by + '\'' +
      ", order=" + order +
      '}';
  }
}
