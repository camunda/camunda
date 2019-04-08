/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing;

import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.time.OffsetDateTime;
import java.util.Objects;

public class UserOperationDto implements OptimizeDto {

  private String id;

  private String userId;
  private OffsetDateTime timestamp;

  private String type;
  private String property;
  private String originalValue;
  private String newValue;

  protected UserOperationDto() {
  }

  public UserOperationDto(final String id, final String userId, final OffsetDateTime timestamp,
                          final String type, final String property, final String originalValue, final String newValue) {
    this.id = id;
    this.userId = userId;
    this.timestamp = timestamp;
    this.type = type;
    this.property = property;
    this.originalValue = originalValue;
    this.newValue = newValue;
  }

  public String getId() {
    return id;
  }


  public String getUserId() {
    return userId;
  }

  public OffsetDateTime getTimestamp() {
    return timestamp;
  }

  public String getType() {
    return type;
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

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof UserOperationDto)) {
      return false;
    }
    final UserOperationDto that = (UserOperationDto) o;
    return id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
