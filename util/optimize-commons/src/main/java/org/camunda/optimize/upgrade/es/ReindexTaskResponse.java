package org.camunda.optimize.upgrade.es;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReindexTaskResponse {
  @JsonProperty("task")
  private String taskId;

  public String getTaskId() {
    return taskId;
  }
}
