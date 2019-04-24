/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.time.OffsetDateTime;
import java.util.Objects;

@AllArgsConstructor
@Data
public class UserOperationLogEntryDto implements OptimizeDto {

  private final String id;

  private final String processInstanceId;
  private final String userTaskId;
  private final String engine;

  private final String userId;
  private final OffsetDateTime timestamp;

  private final String operationType;
  private final String property;
  private final String originalValue;
  private final String newValue;

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof UserOperationLogEntryDto)) {
      return false;
    }
    final UserOperationLogEntryDto that = (UserOperationLogEntryDto) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
