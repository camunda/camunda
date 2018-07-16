package org.camunda.optimize.dto.optimize.query.report.group;

import org.camunda.optimize.dto.optimize.query.report.group.value.FlowNodesGroupByValueDto;
import org.camunda.optimize.service.es.report.command.util.ReportConstants;

public class FlowNodesGroupByDto extends GroupByDto<FlowNodesGroupByValueDto> {

  public FlowNodesGroupByDto() {
    this.type = ReportConstants.GROUP_BY_FLOW_NODES_TYPE;
  }
}
