package org.camunda.optimize.dto.optimize.query.report.single.group.value;

public class NoneGroupByValueDto implements GroupByValueDto {

  @Override
  public boolean isCombinable(Object o) {
    return true;
  }
}
