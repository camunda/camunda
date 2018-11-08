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
