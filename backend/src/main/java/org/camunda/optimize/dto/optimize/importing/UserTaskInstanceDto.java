package org.camunda.optimize.dto.optimize.importing;

import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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

  private final Long totalDurationInMs;
  private final String engine;

  private final Set<UserOperationDto> userOperations = new HashSet<>();

  public UserTaskInstanceDto(final String id, final String processDefinitionId, final String processDefinitionKey,
                             final String processDefinitionVersion, final String processInstanceId,
                             final String activityId, final String activityInstanceId, final OffsetDateTime startDate,
                             final OffsetDateTime endDate, final OffsetDateTime dueDate, final String deleteReason,
                             final Long totalDurationInMs, final String engine) {
    this(
      id, processDefinitionId, processDefinitionKey, processDefinitionVersion, processInstanceId, activityId,
      activityInstanceId, startDate, endDate, dueDate, deleteReason, totalDurationInMs, engine, Collections.emptySet()
    );
  }

  public UserTaskInstanceDto(final String id, final Set<UserOperationDto> userOperations, final String engine) {
    this(id, null, null, null, null, null, null, null, null, null, null, null, engine, userOperations);
  }

  public UserTaskInstanceDto(final String id, final String processDefinitionId, final String processDefinitionKey,
                             final String processDefinitionVersion, final String processInstanceId,
                             final String activityId, final String activityInstanceId, final OffsetDateTime startDate,
                             final OffsetDateTime endDate, final OffsetDateTime dueDate, final String deleteReason,
                             final Long totalDurationInMs, final String engine, final Set<UserOperationDto> userOperations) {
    this.id = id;
    this.processDefinitionId = processDefinitionId;
    this.processDefinitionKey = processDefinitionKey;
    this.processDefinitionVersion = processDefinitionVersion;
    this.processInstanceId = processInstanceId;
    this.activityId = activityId;
    this.activityInstanceId = activityInstanceId;
    this.startDate = startDate;
    this.endDate = endDate;
    this.dueDate = dueDate;
    this.deleteReason = deleteReason;
    this.totalDurationInMs = totalDurationInMs;
    this.engine = engine;
    this.userOperations.addAll(userOperations);
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

  public Long getTotalDurationInMs() {
    return totalDurationInMs;
  }

  public String getEngine() {
    return engine;
  }

  public Set<UserOperationDto> getUserOperations() {
    return userOperations;
  }
}
