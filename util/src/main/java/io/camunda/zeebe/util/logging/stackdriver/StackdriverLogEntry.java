/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.logging.stackdriver;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * POJO allowing the easy construction and serialization of a Stackdriver compatible LogEntry
 *
 * <p>See here for documentation:
 * https://cloud.google.com/logging/docs/agent/configuration#special-fields
 * https://cloud.google.com/logging/docs/reference/v2/rest/v2/LogEntry
 */
@JsonInclude(Include.NON_EMPTY)
public final class StackdriverLogEntry {
  // Setting this as the entry's type will guarantee it will show up in the Error Reporting tool
  public static final String ERROR_REPORT_TYPE =
      "type.googleapis.com/google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent";

  @JsonProperty("severity")
  private String severity;

  @JsonProperty("logging.googleapis.com/sourceLocation")
  private SourceLocation sourceLocation;

  @JsonProperty(value = "message", required = true)
  private String message;

  @JsonProperty("serviceContext")
  private ServiceContext service;

  @JsonProperty("context")
  private Map<String, Object> context;

  @JsonProperty("@type")
  private String type;

  @JsonProperty("exception")
  private String exception;

  @JsonProperty("timestampSeconds")
  private Long timestampSeconds;

  @JsonProperty("timestampNanos")
  private Long timestampNanos;

  StackdriverLogEntry() {}

  public static StackdriverLogEntryBuilder builder() {
    return new StackdriverLogEntryBuilder();
  }

  public String getSeverity() {
    return severity;
  }

  public void setSeverity(final String severity) {
    this.severity = severity;
  }

  public SourceLocation getSourceLocation() {
    return sourceLocation;
  }

  public void setSourceLocation(final SourceLocation sourceLocation) {
    this.sourceLocation = sourceLocation;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(final String message) {
    this.message = message;
  }

  public ServiceContext getService() {
    return service;
  }

  public void setService(final ServiceContext service) {
    this.service = service;
  }

  public Map<String, Object> getContext() {
    return context;
  }

  public void setContext(final Map<String, Object> context) {
    this.context = context;
  }

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public String getException() {
    return exception;
  }

  public void setException(final String exception) {
    this.exception = exception;
  }

  public long getTimestampSeconds() {
    return timestampSeconds;
  }

  public void setTimestampSeconds(final long timestampSeconds) {
    this.timestampSeconds = timestampSeconds;
  }

  public long getTimestampNanos() {
    return timestampNanos;
  }

  public void setTimestampNanos(final long timestampNanos) {
    this.timestampNanos = timestampNanos;
  }
}
