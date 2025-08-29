/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.task.adapter;

import io.camunda.webapps.schema.entities.usertask.TaskEntity;

public record TaskEntityPair(TaskEntity source, TaskWithIndex target) {

  public TaskEntityPair {
    if (source == null || target == null) {
      throw new IllegalArgumentException("Neither source nor target can be null");
    }
  }
}
