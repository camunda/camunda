/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_ASSIGNEE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_CANDIDATE_GROUP;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_END_DATE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_FLOW_NODE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_NONE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_PROCESS;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_START_DATE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_USER_TASK;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_VARIABLE;

public enum DistributedByType {
  NONE(DISTRIBUTED_BY_NONE),
  USER_TASK(DISTRIBUTED_BY_USER_TASK),
  FLOW_NODE(DISTRIBUTED_BY_FLOW_NODE),
  ASSIGNEE(DISTRIBUTED_BY_ASSIGNEE),
  CANDIDATE_GROUP(DISTRIBUTED_BY_CANDIDATE_GROUP),
  VARIABLE(DISTRIBUTED_BY_VARIABLE),
  START_DATE(DISTRIBUTED_BY_START_DATE),
  END_DATE(DISTRIBUTED_BY_END_DATE),
  PROCESS(DISTRIBUTED_BY_PROCESS)
  ;

  private final String id;

  DistributedByType(final String id) {
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
