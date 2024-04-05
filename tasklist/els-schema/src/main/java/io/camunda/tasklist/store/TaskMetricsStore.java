/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.store;

import io.camunda.tasklist.entities.TaskEntity;
import java.time.OffsetDateTime;
import java.util.List;

public interface TaskMetricsStore {

  void registerTaskCompleteEvent(TaskEntity task);

  List<String> retrieveDistinctAssigneesBetweenDates(
      OffsetDateTime startTime, OffsetDateTime endTime);
}
