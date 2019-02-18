package org.camunda.optimize.dto.optimize.importing;

import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.time.OffsetDateTime;
import java.util.Objects;

public class UserOperationLogEntryDto implements OptimizeDto {

  private final String id;

  private final String userTaskId;
  private final String userId;
  private final OffsetDateTime timestamp;

  private final String operationType;
  private final String property;
  private final String originalValue;
  private final String newValue;

  private final String engineAlias;

  public UserOperationLogEntryDto(final String id, final String userTaskId, final String userId,
                                  final OffsetDateTime timestamp, final String operationType, final String property,
                                  final String originalValue, final String newValue, final String engineAlias) {
    this.id = id;
    this.userTaskId = userTaskId;
    this.userId = userId;
    this.timestamp = timestamp;
    this.operationType = operationType;
    this.property = property;
    this.originalValue = originalValue;
    this.newValue = newValue;
    this.engineAlias = engineAlias;
  }

  public String getId() {
    return id;
  }

  public String getUserTaskId() {
    return userTaskId;
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

  public String getProperty() {
    return property;
  }

  public String getOriginalValue() {
    return originalValue;
  }

  public String getNewValue() {
    return newValue;
  }

  public String getEngineAlias() {
    return engineAlias;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof UserOperationLogEntryDto)) {
      return false;
    }
    final UserOperationLogEntryDto that = (UserOperationLogEntryDto) o;
    return Objects.equals(id, that.id) &&
      Objects.equals(engineAlias, that.engineAlias);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, engineAlias);
  }
}
