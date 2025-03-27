/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebe;

import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import java.util.Map;

public interface TasklistServicesAdapter {

  ProcessInstanceCreationRecordValue createProcessInstance(
      final String bpmnProcessId, final Map<String, Object> variables, final String tenantId);

  ProcessInstanceCreationRecordValue createProcessInstanceWithoutAuthentication(
      final String bpmnProcessId, final Map<String, Object> variables, final String tenantId);

  void assignUserTask(final TaskEntity task, final String assignee);

  void unassignUserTask(final TaskEntity task);

  void completeUserTask(final TaskEntity task, final Map<String, Object> variables);

  default boolean isJobBasedUserTask(final TaskEntity task) {
    return task.getImplementation().equals(TaskImplementation.JOB_WORKER);
  }

  void deployResourceWithoutAuthentication(final String classpathResource, final String tenantId);
}
