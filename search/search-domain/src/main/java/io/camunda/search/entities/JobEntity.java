/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JobEntity(
    Long jobKey,
    String type,
    String worker,
    JobState state,
    JobKind kind,
    ListenerEventType listenerEventType,
    Integer retries,
    Boolean isDenied,
    String deniedReason,
    Boolean hasFailedWithRetriesLeft,
    String errorCode,
    String errorMessage,
    Map<String, String> customHeaders,
    OffsetDateTime deadline,
    OffsetDateTime endTime,
    String processDefinitionId,
    Long processDefinitionKey,
    Long processInstanceKey,
    String elementId,
    Long elementInstanceKey,
    String tenantId) {

  public enum JobState {
    CREATED,
    COMPLETED,
    FAILED,
    RETRIES_UPDATED,
    TIMED_OUT,
    CANCELED,
    ERROR_THROWN,
    MIGRATED,
  }

  public enum JobKind {
    BPMN_ELEMENT,
    EXECUTION_LISTENER,
    TASK_LISTENER
  }

  public enum ListenerEventType {
    UNSPECIFIED,
    START,
    END,
    CREATING,
    ASSIGNING,
    UPDATING,
    COMPLETING,
    CANCELING
  }
}
