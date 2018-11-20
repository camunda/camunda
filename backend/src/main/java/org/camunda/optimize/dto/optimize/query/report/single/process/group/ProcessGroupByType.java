package org.camunda.optimize.dto.optimize.query.report.single.process.group;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_FLOW_NODES_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_NONE_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_START_DATE_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_VARIABLE_TYPE;

public enum ProcessGroupByType {
  START_DATE(GROUP_BY_START_DATE_TYPE),
  FLOW_NODES(GROUP_BY_FLOW_NODES_TYPE),
  NONE(GROUP_BY_NONE_TYPE),
  VARIABLE(GROUP_BY_VARIABLE_TYPE),
  ;

  private final String id;

  ProcessGroupByType(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return getId();
  }
}
