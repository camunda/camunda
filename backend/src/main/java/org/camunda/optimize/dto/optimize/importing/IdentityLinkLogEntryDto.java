/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.importing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.time.OffsetDateTime;

@Accessors(chain = true)
@AllArgsConstructor
@Data
@FieldNameConstants(asEnum = true)
public class IdentityLinkLogEntryDto implements OptimizeDto {

  private String id;

  @JsonIgnore
  private String processInstanceId;
  @JsonIgnore
  private String processDefinitionKey;
  @JsonIgnore
  private String engine;

  private IdentityLinkLogType type;
  private String userId;
  private String groupId;
  private String taskId; // == userTaskId
  private String operationType;
  private String assignerId;
  private OffsetDateTime timestamp;
}
