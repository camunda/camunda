package org.camunda.optimize.dto.optimize.query.report.single.process.group.value;

public class NoneGroupByValueDto implements ProcessGroupByValueDto {

  @Override
  public boolean isCombinable(Object o) {
    return true;
  }
}
