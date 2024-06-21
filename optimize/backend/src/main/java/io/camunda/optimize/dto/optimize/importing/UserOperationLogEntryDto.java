/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.importing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

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
