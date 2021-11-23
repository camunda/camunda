/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.es.contract;

import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.webapp.management.dto.UsageMetricDTO;
import java.time.OffsetDateTime;

public interface UsageMetricsContract {

  void registerTaskCompleteEvent(TaskEntity task);

  UsageMetricDTO retrieveDistinctAssigneesBetweenDates(
      OffsetDateTime startTime, OffsetDateTime endTime);
}
