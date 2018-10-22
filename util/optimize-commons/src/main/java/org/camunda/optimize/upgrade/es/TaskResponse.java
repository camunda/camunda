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
        ? 1.0D
        : ((double) (status.created + status.updated + status.deleted)) / status.total)
      .orElse(0.0D);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Task {
    @JsonProperty("id")
    private String id;
    @JsonProperty("status")
    private Status status;

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
  }
}
