/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.reader;

import io.camunda.operate.webapp.rest.dto.FlowNodeStatisticsDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import java.util.Collection;

public interface FlowNodeStatisticsReader {
  String AGG_ACTIVITIES = "activities";
  String AGG_UNIQUE_ACTIVITIES = "unique_activities";
  String AGG_ACTIVITY_TO_PROCESS = "activity_to_process";
  String AGG_ACTIVE_ACTIVITIES = "active_activities";
  String AGG_INCIDENT_ACTIVITIES = "incident_activities";
  String AGG_TERMINATED_ACTIVITIES = "terminated_activities";
  String AGG_FINISHED_ACTIVITIES = "finished_activities";

  Collection<FlowNodeStatisticsDto> getFlowNodeStatistics(ListViewQueryDto query);
}
