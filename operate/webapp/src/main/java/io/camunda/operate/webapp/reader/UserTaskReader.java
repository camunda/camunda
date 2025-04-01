/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.reader;

import io.camunda.webapps.schema.entities.usertask.SnapshotTaskVariableEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import java.util.List;
import java.util.Optional;

public interface UserTaskReader {

  List<TaskEntity> getUserTasks();

  Optional<TaskEntity> getUserTaskByFlowNodeInstanceKey(long flowNodeInstanceKey);

  List<SnapshotTaskVariableEntity> getUserTaskVariables(long taskKey);
}
