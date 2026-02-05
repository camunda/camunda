/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.demo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** Demo DTO to test @McpToolParams annotation. */
@Schema(description = "Demo request for creating a task")
public class CreateTaskRequest {

  @NotBlank
  @Size(min = 1, max = 100)
  @Schema(description = "The name of the task", example = "Review document")
  @JsonProperty("taskName")
  private String taskName;

  @NotBlank
  @Pattern(regexp = "^[a-z]+$", message = "Priority must be lowercase: low, medium, high")
  @Schema(description = "Priority of the task", example = "high")
  @JsonProperty("priority")
  private String priority;

  @Schema(description = "Optional metadata for the task")
  @JsonProperty("metadata")
  private Map<String, Object> metadata;

  @Schema(description = "Whether the task is urgent (defaults to false)", example = "false")
  @JsonProperty("urgent")
  private Boolean urgent = false;

  public CreateTaskRequest() {}

  public CreateTaskRequest(
      String taskName, String priority, Map<String, Object> metadata, Boolean urgent) {
    this.taskName = taskName;
    this.priority = priority;
    this.metadata = metadata;
    this.urgent = urgent;
  }

  public String getTaskName() {
    return taskName;
  }

  public void setTaskName(String taskName) {
    this.taskName = taskName;
  }

  public String getPriority() {
    return priority;
  }

  public void setPriority(String priority) {
    this.priority = priority;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, Object> metadata) {
    this.metadata = metadata;
  }

  public Boolean getUrgent() {
    return urgent;
  }

  public void setUrgent(Boolean urgent) {
    this.urgent = urgent;
  }
}
