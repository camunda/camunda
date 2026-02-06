/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.listview;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Schema(description = "Process instance query")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ListViewQueryDto {

  private boolean running;
  private boolean active;
  private boolean incidents;

  private boolean finished;
  private boolean completed;
  private boolean canceled;

  private boolean retriesLeft;

  @Schema(description = "Array of process instance ids", nullable = true)
  private List<String> ids;

  private String errorMessage;

  private Integer incidentErrorHashCode;

  private String activityId;

  @Schema(description = "Start date after (inclusive)", nullable = true)
  private OffsetDateTime startDateAfter;

  @Schema(description = "Start date before (exclusive)", nullable = true)
  private OffsetDateTime startDateBefore;

  @Schema(description = "End date after (inclusive)", nullable = true)
  private OffsetDateTime endDateAfter;

  @Schema(description = "End date before (exclusive)", nullable = true)
  private OffsetDateTime endDateBefore;

  private List<String> processIds;

  private String bpmnProcessId;

  @Schema(
      description =
          "Process version, goes together with bpmnProcessId. Can be null, then all version of the process are selected.")
  private Integer processVersion;

  private List<String> excludeIds;

  private VariablesQueryDto variable;

  private String batchOperationId;

  private Long parentInstanceId;

  private String tenantId;

  public ListViewQueryDto() {}

  public boolean isRunning() {
    return running;
  }

  public ListViewQueryDto setRunning(final boolean running) {
    this.running = running;
    return this;
  }

  public boolean isCompleted() {
    return completed;
  }

  public ListViewQueryDto setCompleted(final boolean completed) {
    this.completed = completed;
    return this;
  }

  public boolean isIncidents() {
    return incidents;
  }

  public ListViewQueryDto setIncidents(final boolean incidents) {
    this.incidents = incidents;
    return this;
  }

  public boolean isActive() {
    return active;
  }

  public ListViewQueryDto setActive(final boolean active) {
    this.active = active;
    return this;
  }

  public boolean isFinished() {
    return finished;
  }

  public ListViewQueryDto setFinished(final boolean finished) {
    this.finished = finished;
    return this;
  }

  public boolean isCanceled() {
    return canceled;
  }

  public ListViewQueryDto setCanceled(final boolean canceled) {
    this.canceled = canceled;
    return this;
  }

  public boolean isRetriesLeft() {
    return retriesLeft;
  }

  public ListViewQueryDto setRetriesLeft(final boolean retriesLeft) {
    this.retriesLeft = retriesLeft;
    return this;
  }

  public List<String> getIds() {
    return ids;
  }

  public ListViewQueryDto setIds(final List<String> ids) {
    this.ids = ids;
    return this;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public ListViewQueryDto setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
    return this;
  }

  public Integer getIncidentErrorHashCode() {
    return incidentErrorHashCode;
  }

  public ListViewQueryDto setIncidentErrorHashCode(final Integer incidentErrorHashCode) {
    this.incidentErrorHashCode = incidentErrorHashCode;
    return this;
  }

  public String getActivityId() {
    return activityId;
  }

  public ListViewQueryDto setActivityId(final String activityId) {
    this.activityId = activityId;
    return this;
  }

  public OffsetDateTime getStartDateAfter() {
    return startDateAfter;
  }

  public ListViewQueryDto setStartDateAfter(final OffsetDateTime startDateAfter) {
    this.startDateAfter = startDateAfter;
    return this;
  }

  public OffsetDateTime getStartDateBefore() {
    return startDateBefore;
  }

  public ListViewQueryDto setStartDateBefore(final OffsetDateTime startDateBefore) {
    this.startDateBefore = startDateBefore;
    return this;
  }

  public OffsetDateTime getEndDateAfter() {
    return endDateAfter;
  }

  public ListViewQueryDto setEndDateAfter(final OffsetDateTime endDateAfter) {
    this.endDateAfter = endDateAfter;
    return this;
  }

  public OffsetDateTime getEndDateBefore() {
    return endDateBefore;
  }

  public ListViewQueryDto setEndDateBefore(final OffsetDateTime endDateBefore) {
    this.endDateBefore = endDateBefore;
    return this;
  }

  public List<String> getProcessIds() {
    return processIds;
  }

  public ListViewQueryDto setProcessIds(final List<String> processIds) {
    this.processIds = processIds;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ListViewQueryDto setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public Integer getProcessVersion() {
    return processVersion;
  }

  public ListViewQueryDto setProcessVersion(final Integer processVersion) {
    this.processVersion = processVersion;
    return this;
  }

  public List<String> getExcludeIds() {
    return excludeIds;
  }

  public ListViewQueryDto setExcludeIds(final List<String> excludeIds) {
    this.excludeIds = excludeIds;
    return this;
  }

  public VariablesQueryDto getVariable() {
    return variable;
  }

  public ListViewQueryDto setVariable(final VariablesQueryDto variable) {
    this.variable = variable;
    return this;
  }

  public String getBatchOperationId() {
    return batchOperationId;
  }

  public ListViewQueryDto setBatchOperationId(final String batchOperationId) {
    this.batchOperationId = batchOperationId;
    return this;
  }

  public Long getParentInstanceId() {
    return parentInstanceId;
  }

  public ListViewQueryDto setParentInstanceId(final Long parentInstanceId) {
    this.parentInstanceId = parentInstanceId;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public ListViewQueryDto setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        running,
        active,
        incidents,
        finished,
        completed,
        canceled,
        retriesLeft,
        ids,
        errorMessage,
        incidentErrorHashCode,
        activityId,
        startDateAfter,
        startDateBefore,
        endDateAfter,
        endDateBefore,
        processIds,
        bpmnProcessId,
        processVersion,
        excludeIds,
        variable,
        batchOperationId,
        parentInstanceId,
        tenantId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ListViewQueryDto that = (ListViewQueryDto) o;
    return running == that.running
        && active == that.active
        && incidents == that.incidents
        && finished == that.finished
        && completed == that.completed
        && canceled == that.canceled
        && retriesLeft == that.retriesLeft
        && Objects.equals(ids, that.ids)
        && Objects.equals(errorMessage, that.errorMessage)
        && Objects.equals(incidentErrorHashCode, that.incidentErrorHashCode)
        && Objects.equals(activityId, that.activityId)
        && Objects.equals(startDateAfter, that.startDateAfter)
        && Objects.equals(startDateBefore, that.startDateBefore)
        && Objects.equals(endDateAfter, that.endDateAfter)
        && Objects.equals(endDateBefore, that.endDateBefore)
        && Objects.equals(processIds, that.processIds)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(processVersion, that.processVersion)
        && Objects.equals(excludeIds, that.excludeIds)
        && Objects.equals(variable, that.variable)
        && Objects.equals(batchOperationId, that.batchOperationId)
        && Objects.equals(parentInstanceId, that.parentInstanceId)
        && Objects.equals(tenantId, that.tenantId);
  }
}
