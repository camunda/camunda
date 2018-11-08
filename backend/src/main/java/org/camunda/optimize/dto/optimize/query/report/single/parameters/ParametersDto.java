package org.camunda.optimize.dto.optimize.query.report.single.parameters;

public class ParametersDto {
  // nullable
  private ProcessPartDto processPart;
  // nullable
  private SortingDto sorting;

  public ParametersDto() {
  }

  public ParametersDto(ProcessPartDto processPart) {
    this(processPart, null);
  }

  public ParametersDto(SortingDto sorting) {
    this(null, sorting);
  }

  public ParametersDto(ProcessPartDto processPart, SortingDto sorting) {
    this.processPart = processPart;
    this.sorting = sorting;
  }

  public ProcessPartDto getProcessPart() {
    return processPart;
  }

  public SortingDto getSorting() {
    return sorting;
  }

  public void setProcessPart(ProcessPartDto processPart) {
    this.processPart = processPart;
  }

  public void setSorting(SortingDto sorting) {
    this.sorting = sorting;
  }

  @Override
  public String toString() {
    return "ParametersDto{" +
      "processPart=" + processPart +
      ", sorting=" + sorting +
      '}';
  }
}
