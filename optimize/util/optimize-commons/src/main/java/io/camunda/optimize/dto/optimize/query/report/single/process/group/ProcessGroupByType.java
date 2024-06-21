/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.group;

import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_ASSIGNEE;
import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_CANDIDATE_GROUP;
import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_DURATION;
import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_END_DATE_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_FLOW_NODES_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_NONE_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_RUNNING_DATE_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_START_DATE_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_USER_TASKS_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_VARIABLE_TYPE;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ProcessGroupByType {
  START_DATE(GROUP_BY_START_DATE_TYPE),
  END_DATE(GROUP_BY_END_DATE_TYPE),
  RUNNING_DATE(GROUP_BY_RUNNING_DATE_TYPE),
  FLOW_NODES(GROUP_BY_FLOW_NODES_TYPE),
  USER_TASKS(GROUP_BY_USER_TASKS_TYPE),
  NONE(GROUP_BY_NONE_TYPE),
  VARIABLE(GROUP_BY_VARIABLE_TYPE),
  ASSIGNEE(GROUP_BY_ASSIGNEE),
  CANDIDATE_GROUP(GROUP_BY_CANDIDATE_GROUP),
  DURATION(GROUP_BY_DURATION);

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
