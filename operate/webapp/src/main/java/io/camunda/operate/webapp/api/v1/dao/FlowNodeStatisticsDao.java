/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao;

import io.camunda.operate.webapp.api.v1.entities.FlowNodeStatistics;
import java.util.List;

public interface FlowNodeStatisticsDao {
  String FLOW_NODE_ID_AGG = "flowNodeIdAgg";
  String COUNT_INCIDENT = "countIncident";
  String COUNT_CANCELED = "countCanceled";
  String COUNT_COMPLETED = "countCompleted";
  String COUNT_ACTIVE = "countActive";

  List<FlowNodeStatistics> getFlowNodeStatisticsForProcessInstance(Long processInstanceKey);
}
