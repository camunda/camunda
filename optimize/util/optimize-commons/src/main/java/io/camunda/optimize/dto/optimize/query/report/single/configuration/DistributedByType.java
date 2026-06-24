/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration;

import static io.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_ASSIGNEE;
import static io.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_CANDIDATE_GROUP;
import static io.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_END_DATE;
import static io.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_FLOW_NODE;
import static io.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_NONE;
import static io.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_PROCESS;
import static io.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_START_DATE;
import static io.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_USER_TASK;
import static io.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_VARIABLE;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DistributedByType {
  NONE(DISTRIBUTED_BY_NONE),
  USER_TASK(DISTRIBUTED_BY_USER_TASK),
  FLOW_NODE(DISTRIBUTED_BY_FLOW_NODE),
  ASSIGNEE(DISTRIBUTED_BY_ASSIGNEE),
  CANDIDATE_GROUP(DISTRIBUTED_BY_CANDIDATE_GROUP),
  VARIABLE(DISTRIBUTED_BY_VARIABLE),
  START_DATE(DISTRIBUTED_BY_START_DATE),
  END_DATE(DISTRIBUTED_BY_END_DATE),
  PROCESS(DISTRIBUTED_BY_PROCESS);

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
