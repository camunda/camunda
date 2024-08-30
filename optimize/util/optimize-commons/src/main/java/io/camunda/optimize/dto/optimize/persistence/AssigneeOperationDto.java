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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@NoArgsConstructor
@AllArgsConstructor
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Accessors(chain = true)
public class AssigneeOperationDto implements OptimizeDto, Serializable {

  @EqualsAndHashCode.Include private String id;

  private String userId;
  private String operationType;
  private OffsetDateTime timestamp;

  public static final class Fields {

    public static final String id = "id";
    public static final String userId = "userId";
    public static final String operationType = "operationType";
    public static final String timestamp = "timestamp";
  }
}
