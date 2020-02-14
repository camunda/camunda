/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_ASSIGNEE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_CANDIDATE_GROUP;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_NONE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_USER_TASK;

public enum DistributedBy {
  NONE(DISTRIBUTED_BY_NONE),
  USER_TASK(DISTRIBUTED_BY_USER_TASK),
  ASSIGNEE(DISTRIBUTED_BY_ASSIGNEE),
  CANDIDATE_GROUP(DISTRIBUTED_BY_CANDIDATE_GROUP);

  private final String id;

  DistributedBy(final String id) {
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
