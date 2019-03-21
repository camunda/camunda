package org.camunda.optimize.dto.optimize.query.report.single.decision;

import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;

import java.util.Optional;

public class DecisionParametersDto {
  // nullable
  private SortingDto sorting;

  public DecisionParametersDto() {
  }

  public DecisionParametersDto(SortingDto sorting) {
    this.sorting = sorting;
  }

  public Optional<SortingDto> getSorting() {
    return Optional.ofNullable(sorting);
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
