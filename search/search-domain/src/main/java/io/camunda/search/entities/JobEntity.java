/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JobEntity(
    Long jobKey,
    @Nullable String type,
    @Nullable String worker,
    @Nullable JobState state,
    @Nullable JobKind kind,
    @Nullable ListenerEventType listenerEventType,
    @Nullable Integer retries,
    @Nullable Boolean isDenied,
    @Nullable String deniedReason,
    @Nullable Boolean hasFailedWithRetriesLeft,
    @Nullable String errorCode,
    @Nullable String errorMessage,
    Map<String, String> customHeaders,
    @Nullable OffsetDateTime deadline,
    @Nullable OffsetDateTime endTime,
    @Nullable String processDefinitionId,
    @Nullable Long processDefinitionKey,
    @Nullable Long processInstanceKey,
    @Nullable Long rootProcessInstanceKey,
    @Nullable String elementId,
    @Nullable Long elementInstanceKey,
    String tenantId,
    @Nullable OffsetDateTime creationTime,
    @Nullable OffsetDateTime lastUpdateTime)
    implements TenantOwnedEntity {

  public JobEntity {
    requireNonNull(jobKey, "jobKey");
    requireNonNull(tenantId, "tenantId");
    // Mutable collections are required: MyBatis hydrates collection-mapped fields (e.g. from a
    // <collection> result map or a LEFT JOIN) by calling .add() on the existing instance.
    // Immutable defaults (e.g. Map.of()) would cause UnsupportedOperationException at runtime.
    customHeaders = customHeaders != null ? customHeaders : new HashMap<>();
  }

  public static class Builder implements ObjectBuilder<JobEntity> {
    private Long jobKey;
    private String type;
    private String worker;
    private JobState state;
    private JobKind kind;
    private ListenerEventType listenerEventType;
    private Integer retries;
    private Boolean isDenied;
    private String deniedReason;
    private Boolean hasFailedWithRetriesLeft;
    private String errorCode;
    private String errorMessage;
    private Map<String, String> customHeaders;
    private OffsetDateTime deadline;
    private OffsetDateTime endTime;
    private String processDefinitionId;
    private Long processDefinitionKey;
    private Long processInstanceKey;
    private Long rootProcessInstanceKey;
    private String elementId;
    private Long elementInstanceKey;
    private String tenantId;
    private OffsetDateTime creationTime;
    private OffsetDateTime lastUpdateTime;

    public Builder jobKey(final Long jobKey) {
      this.jobKey = jobKey;
      return this;
    }

    public Builder type(final String type) {
      this.type = type;
      return this;
    }

    public Builder worker(final String worker) {
      this.worker = worker;
      return this;
    }

    public Builder state(final JobState state) {
      this.state = state;
      return this;
    }

    public Builder kind(final JobKind kind) {
      this.kind = kind;
      return this;
    }

    public Builder listenerEventType(final ListenerEventType listenerEventType) {
      this.listenerEventType = listenerEventType;
      return this;
    }

    public Builder retries(final Integer retries) {
      this.retries = retries;
      return this;
    }

    public Builder isDenied(final Boolean isDenied) {
      this.isDenied = isDenied;
      return this;
    }

    public Builder deniedReason(final String deniedReason) {
      this.deniedReason = deniedReason;
      return this;
    }

    public Builder hasFailedWithRetriesLeft(final Boolean hasFailedWithRetriesLeft) {
      this.hasFailedWithRetriesLeft = hasFailedWithRetriesLeft;
      return this;
    }

    public Builder errorCode(final String errorCode) {
      this.errorCode = errorCode;
      return this;
    }

    public Builder errorMessage(final String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    public Builder customHeaders(final Map<String, String> customHeaders) {
      this.customHeaders = customHeaders;
      return this;
    }

    public Builder deadline(final OffsetDateTime deadline) {
      this.deadline = deadline;
      return this;
    }

    public Builder endTime(final OffsetDateTime endTime) {
      this.endTime = endTime;
      return this;
    }

    public Builder processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    public Builder processDefinitionKey(final Long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processInstanceKey(final Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder rootProcessInstanceKey(final Long rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    public Builder elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    public Builder elementInstanceKey(final Long elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder creationTime(final OffsetDateTime creationTime) {
      this.creationTime = creationTime;
      return this;
    }

    public Builder lastUpdateTime(final OffsetDateTime lastUpdateTime) {
      this.lastUpdateTime = lastUpdateTime;
      return this;
    }

    @Override
    public JobEntity build() {
      return new JobEntity(
          jobKey,
          type,
          worker,
          state,
          kind,
          listenerEventType,
          retries,
          isDenied,
          deniedReason,
          hasFailedWithRetriesLeft,
          errorCode,
          errorMessage,
          customHeaders,
          deadline,
          endTime,
          processDefinitionId,
          processDefinitionKey,
          processInstanceKey,
          rootProcessInstanceKey,
          elementId,
          elementInstanceKey,
          tenantId,
          creationTime,
          lastUpdateTime);
    }
  }

  public enum JobState {
    CANCELED,
    COMPLETED,
    CREATED,
    ERROR_THROWN,
    FAILED,
    MIGRATED,
    RETRIES_UPDATED,
    TIMED_OUT,
  }

  public enum JobKind {
    BPMN_ELEMENT,
    EXECUTION_LISTENER,
    TASK_LISTENER,
    AD_HOC_SUB_PROCESS
  }

  public enum ListenerEventType {
    ASSIGNING,
    BEFORE_ALL,
    CANCELING,
    COMPLETING,
    CREATING,
    END,
    START,
    UNSPECIFIED,
    UPDATING
  }
}
