package org.camunda.optimize.dto.optimize.query.report.single.decision;

import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;

public class DecisionParametersDto {
  // nullable
  private SortingDto sorting;

  public DecisionParametersDto() {
  }

  public DecisionParametersDto(SortingDto sorting) {
    this.sorting = sorting;
  }

  public SortingDto getSorting() {
    return sorting;
  }


  public void setSorting(SortingDto sorting) {
    this.sorting = sorting;
  }

  @Override
  public String toString() {
    return "DecisionParametersDto{" +
      "sorting=" + sorting +
      '}';
  }
}
