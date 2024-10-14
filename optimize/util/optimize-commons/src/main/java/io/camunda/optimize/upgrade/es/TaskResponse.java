/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.es;

import static java.util.Map.Entry.comparingByKey;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskResponse {

  @JsonProperty("completed")
  private boolean completed;

  @JsonProperty("task")
  private Task task;

  @JsonProperty("error")
  private Error error;

  @JsonProperty("response")
  private TaskResponseDetails responseDetails;

  public TaskResponse(
      final boolean completed,
      final Task task,
      final Error error,
      final TaskResponseDetails responseDetails) {
    this.completed = completed;
    this.task = task;
    this.error = error;
    this.responseDetails = responseDetails;
  }

  protected TaskResponse() {}

  public boolean isCompleted() {
    return completed;
  }

  @JsonIgnore
  public Status getTaskStatus() {
    return Optional.ofNullable(task).flatMap(Task::getStatus).orElse(null);
  }

  @JsonIgnore
  public Double getProgress() {
    return Optional.ofNullable(task)
        .flatMap(Task::getStatus)
        .filter(status -> status.getTotal() != 0)
        .map(
            status ->
                ((double) (status.getCreated() + status.getUpdated() + status.getDeleted()))
                    / status.getTotal())
        .orElse(0.0D);
  }

  public TaskResponseDetails getResponseDetails() {
    return responseDetails;
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

    public Task(final String id, final Status status) {
      this.id = id;
      this.status = status;
    }

    protected Task() {}

    public String getId() {
      return id;
    }

    @JsonProperty("id")
    public void setId(final String id) {
      this.id = id;
    }

    @JsonIgnore
    public Optional<Status> getStatus() {
      return Optional.ofNullable(status);
    }

    @JsonProperty("status")
    public void setStatus(final Status status) {
      this.status = status;
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

    public Status(final Long total, final Long updated, final Long created, final Long deleted) {
      this.total = total;
      this.updated = updated;
      this.created = created;
      this.deleted = deleted;
    }

    protected Status() {}

    public Long getTotal() {
      return total;
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

    @JsonProperty("script_stack")
    private List<String> scriptStack;

    @JsonProperty("caused_by")
    private Map<String, Object> causedBy;

    public Error(
        final String type,
        final String reason,
        final List<String> scriptStack,
        final Map<String, Object> causedBy) {
      this.type = type;
      this.reason = reason;
      this.scriptStack = scriptStack;
      this.causedBy = causedBy;
    }

    protected Error() {}

    @Override
    public String toString() {
      final String scriptStackString =
          scriptStack == null
              ? null
              : scriptStack.stream()
                  .map(stackLine -> "\n" + stackLine)
                  .collect(Collectors.toList())
                  .toString();
      final String causedByString =
          Optional.ofNullable(causedBy)
              .map(
                  causes ->
                      causes.entrySet().stream()
                          .sorted(comparingByKey())
                          .map(entry -> entry.getKey() + "=" + entry.getValue())
                          .collect(Collectors.joining(",", "'{", "}'")))
              .orElse(null);
      return "Error{"
          + "type='"
          + type
          + "\', reason='"
          + reason
          + '\''
          + ", script_stack='"
          + scriptStackString
          + "\'\n"
          + "caused_by="
          + causedByString
          + '}';
    }

    public String getType() {
      return type;
    }

    public String getReason() {
      return reason;
    }

    public List<String> getScriptStack() {
      return scriptStack;
    }

    public Map<String, Object> getCausedBy() {
      return causedBy;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class TaskResponseDetails {

    @JsonProperty("failures")
    private List<Object> failures;

    public TaskResponseDetails(final List<Object> failures) {
      this.failures = failures;
    }

    protected TaskResponseDetails() {}

    public List<Object> getFailures() {
      return failures;
    }

    @JsonProperty("failures")
    public void setFailures(final List<Object> failures) {
      this.failures = failures;
    }
  }
}
