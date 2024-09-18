/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.persistence;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.io.Serializable;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AssigneeOperationDto implements OptimizeDto, Serializable {

  @EqualsAndHashCode.Include private String id;

  private String userId;
  private String operationType;
  private OffsetDateTime timestamp;

  public AssigneeOperationDto(
      String id, String userId, String operationType, OffsetDateTime timestamp) {
    this.id = id;
    this.userId = userId;
    this.operationType = operationType;
    this.timestamp = timestamp;
  }

  public AssigneeOperationDto() {}

  public static final class Fields {

    public static final String id = "id";
    public static final String userId = "userId";
    public static final String operationType = "operationType";
    public static final String timestamp = "timestamp";
  }
}
