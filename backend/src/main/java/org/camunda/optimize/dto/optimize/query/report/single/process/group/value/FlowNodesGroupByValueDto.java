package org.camunda.optimize.dto.optimize.query.report.single.process.group.value;

public class FlowNodesGroupByValueDto implements GroupByValueDto {

  @Override
  public boolean isCombinable(Object o) {
    return true;
  }
}
