package org.camunda.optimize.dto.optimize.query.report.single.process.group;

import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.FlowNodesGroupByValueDto;

public class FlowNodesGroupByDto extends ProcessGroupByDto<FlowNodesGroupByValueDto> {

  public FlowNodesGroupByDto() {
    this.type = ProcessGroupByType.FLOW_NODES;
  }
}
