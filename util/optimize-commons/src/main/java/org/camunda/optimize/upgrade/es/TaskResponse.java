/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.es;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskResponse {
  @JsonProperty("completed")
  private boolean completed;

  @JsonProperty("task")
  private Task task;

  @JsonProperty("error")
  private Error error;

  protected TaskResponse() {
  }

  public TaskResponse(boolean completed, Task task, Error error) {
    this.completed = completed;
    this.task = task;
    this.error = error;
  }

  public String getId() {
    return Optional.ofNullable(task).map(Task::getId).orElse(null);
  }

  public Status getStatus() {
    return Optional.ofNullable(task).flatMap(Task::getStatus).orElse(null);
  }

  public boolean isDone() {
    return completed;
  }

  public Double getProgress() {
    return Optional.ofNullable(task).flatMap(Task::getStatus)
      .map(status -> status.total == 0
        ? 0.0D
        : ((double) (status.created + status.updated + status.deleted)) / status.total)
      .orElse(0.0D);
  }

  public Error getError() {
    return error;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Task {
    @JsonProperty("id")
    private String id;
    @JsonProperty("status")
    private Status status;

    protected Task() {
    }

    public Task(String id, Status status) {
      this.id = id;
      this.status = status;
    }

    public String getId() {
      return id;
    }

    public Optional<Status> getStatus() {
      return Optional.ofNullable(status);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Status {

    @JsonProperty("total")
    private Long total;
    @JsonProperty("updated")
    private Long updated;
    @JsonProperty("created")
    private Long created;
    @JsonProperty("deleted")
    private Long deleted;
    public Long getTotal() {
      return total;
    }

    protected Status() {
    }

    public Status(Long total, Long updated, Long created, Long deleted) {
      this.total = total;
      this.updated = updated;
      this.created = created;
      this.deleted = deleted;
    }

    public Long getUpdated() {
      return updated;
    }

    public Long getCreated() {
      return created;
    }

    public Long getDeleted() {
      return deleted;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Error {
    @JsonProperty("type")
    private String type;
    @JsonProperty("reason")
    private String reason;
    @JsonProperty("phase")
    private String phase;

    protected Error() {
    }

    public Error(String type, String reason, String phase) {
      this.type = type;
      this.reason = reason;
      this.phase = phase;
    }

    public String getType() {
      return type;
    }

    public String getReason() {
      return reason;
    }

    public String getPhase() {
      return phase;
    }

    @Override
    public String toString() {
      return "Error{" +
        "type='" + type + '\'' +
        ", reason='" + reason + '\'' +
        ", phase='" + phase + '\'' +
        '}';
    }
  }

}
