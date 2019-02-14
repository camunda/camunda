package org.camunda.optimize.dto.optimize.importing;

import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.time.OffsetDateTime;

public class UserTaskInstanceDto implements OptimizeDto {

  private final String id;

  private final String processDefinitionId;
  private final String processDefinitionKey;
  private final String processDefinitionVersion;

  private final String processInstanceId;

  private final String activityId;
  private final String activityInstanceId;

  private final OffsetDateTime startDate;
  private final OffsetDateTime endDate;
  private final OffsetDateTime dueDate;

  private final String deleteReason;

  private final Long durationInMs;
  private final String engine;

  public UserTaskInstanceDto(final String id, final String processDefinitionId, final String processDefinitionKey,
                             final String processDefinitionVersion, final String processInstanceId, final String activityId,
                             final String activityInstanceId, final OffsetDateTime startDate, final OffsetDateTime endDate,
                             final OffsetDateTime dueDate, final String deleteReason, final Long durationInMs, final String engine) {
    this.id = id;
    this.processDefinitionKey = processDefinitionKey;
    this.processDefinitionId = processDefinitionId;
    this.processDefinitionVersion = processDefinitionVersion;
    this.processInstanceId = processInstanceId;
    this.activityId = activityId;
    this.activityInstanceId = activityInstanceId;
    this.startDate = startDate;
    this.endDate = endDate;
    this.dueDate = dueDate;
    this.deleteReason = deleteReason;
    this.durationInMs = durationInMs;
    this.engine = engine;
  }

  public String getId() {
    return id;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public String getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public String getActivityId() {
    return activityId;
  }

  public String getActivityInstanceId() {
    return activityInstanceId;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public String getDeleteReason() {
    return deleteReason;
  }

  public Long getDurationInMs() {
    return durationInMs;
  }

  public String getEngine() {
    return engine;
  }
}
