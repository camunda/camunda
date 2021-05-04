/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.schema.templates;

import org.springframework.stereotype.Component;

@Component
public class TaskVariableTemplate extends AbstractTemplateDescriptor {

  public static final String INDEX_NAME = "task-variable";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String TASK_ID = "taskId";
  public static final String NAME = "name";
  public static final String VALUE = "value";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }
}
