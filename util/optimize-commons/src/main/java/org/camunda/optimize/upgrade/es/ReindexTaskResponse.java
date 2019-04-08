/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.es;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReindexTaskResponse {
  @JsonProperty("task")
  private String taskId;

  protected ReindexTaskResponse() {
  }

  public ReindexTaskResponse(String taskId) {
    this.taskId = taskId;
  }

  public String getTaskId() {
    return taskId;
  }
}
