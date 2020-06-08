/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.time.OffsetDateTime;

@Accessors(chain = true)
@Builder
@Data
@FieldNameConstants(asEnum = true)
public class UserOperationLogEntryDto implements OptimizeDto {
  private String id;

  @JsonIgnore
  private String processDefinitionId;
  @JsonIgnore
  private String processDefinitionKey;
  @JsonIgnore
  private String processInstanceId;

  private UserOperationType operationType;
  private OffsetDateTime timestamp;
}
