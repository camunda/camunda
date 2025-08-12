/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.task.util;

import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import java.util.regex.Pattern;

public class MigrationUtil {

  public static final Pattern MIGRATION_REPOSITORY_NOT_EXISTS =
      Pattern.compile(
          "no such index \\[[a-zA-Z0-9\\-]+-migration-steps-repository-[0-9]+\\.[0-9]+\\.[0-9]+_]");

  public static TaskEntity migrate(final TaskEntity entity) {
    final TaskEntity taskEntity = new TaskEntity();
    taskEntity.setId(entity.getId());
    taskEntity.setBpmnProcessId(entity.getBpmnProcessId());

    // TODO: Complete the migration logic for TaskEntity
    return taskEntity;
  }
}
