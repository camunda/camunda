package org.camunda.optimize.dto.optimize.query.report.single.group;

import org.camunda.optimize.dto.optimize.query.report.single.group.value.FlowNodesGroupByValueDto;
import org.camunda.optimize.service.es.report.command.util.ReportConstants;

public class FlowNodesGroupByDto extends GroupByDto<FlowNodesGroupByValueDto> {

  public FlowNodesGroupByDto() {
    this.type = ReportConstants.GROUP_BY_FLOW_NODES_TYPE;
  }
}
