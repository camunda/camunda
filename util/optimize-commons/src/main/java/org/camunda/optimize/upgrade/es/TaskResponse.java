/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.es;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Map.Entry.comparingByKey;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
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
      .map(status -> ((double) (status.getCreated() + status.getUpdated() + status.getDeleted())) / status.getTotal())
      .orElse(0.0D);
  }

  public TaskResponseDetails getResponseDetails() {
    return responseDetails;
  }

  public Error getError() {
    return error;
  }

  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  @AllArgsConstructor
  @Setter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Task {

    @JsonProperty("id")
    private String id;
    @JsonProperty("status")
    private Status status;

    public String getId() {
      return id;
    }

    @JsonIgnore
    public Optional<Status> getStatus() {
      return Optional.ofNullable(status);
    }

  }

  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  @AllArgsConstructor
  @Getter
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

  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  @Getter
  public static class Error {
    @JsonProperty("type")
    private String type;
    @JsonProperty("reason")
    private String reason;
    @JsonProperty("script_stack")
    private List<String> scriptStack;
    @JsonProperty("caused_by")
    private Map<String, Object> causedBy;

    @Override
    public String toString() {
      String scriptStackString = scriptStack == null ? null : scriptStack.stream()
        .map(stackLine -> "\n" + stackLine)
        .collect(Collectors.toList())
        .toString();
      final String causedByString = Optional.ofNullable(causedBy)
        .map(causes -> {
          final StringBuilder causedByStringBuilder = new StringBuilder("'{");
          causes.entrySet()
            .stream()
            .sorted(comparingByKey())
            .forEach(entry -> causedByStringBuilder.append(entry.getKey()).append("=").append(entry.getValue()));
          causedByStringBuilder.append("}'");
          return causedByStringBuilder.toString();
        })
        .orElse(null);
      return "Error{" +
        "type='" + type + "\', reason='" + reason + '\'' +
        ", script_stack='" + scriptStackString + "\'\n" +
        "caused_by='" + causedByString + '\'' +
        '}';
    }
  }

  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  @AllArgsConstructor
  @Setter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class TaskResponseDetails {
    @JsonProperty("failures")
    private List<Object> failures;

    public List<Object> getFailures() {
      return failures;
    }
  }

}
