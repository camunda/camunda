package org.camunda.optimize.dto.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserOperationLogEntryEngineDto implements EngineDto {
  private String id;
  private String processDefinitionId;
  private String processDefinitionKey;
  private String processInstanceId;
  private String taskId;
  private String userId;
  private OffsetDateTime timestamp;
  private String operationType;
  private String entityType;
  private String property;
  private String orgValue;
  private String newValue;

  @Override
  public String getId() {
    return id;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public String getTaskId() {
    return taskId;
  }

  public String getUserId() {
    return userId;
  }

  public OffsetDateTime getTimestamp() {
    return timestamp;
  }

  public String getOperationType() {
    return operationType;
  }

  public String getEntityType() {
    return entityType;
  }

  public String getProperty() {
    return property;
  }

  public String getOrgValue() {
    return orgValue;
  }

  public String getNewValue() {
    return newValue;
  }
}
