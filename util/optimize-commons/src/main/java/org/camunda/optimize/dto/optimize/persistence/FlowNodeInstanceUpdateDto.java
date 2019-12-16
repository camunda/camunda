/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.persistence;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.time.OffsetDateTime;

@Builder
@Data
@FieldNameConstants
public class FlowNodeInstanceUpdateDto implements OptimizeDto {
  protected String sourceEventId;
  protected String flowNodeId;
  protected String flowNodeType;
  protected OffsetDateTime startDate;
  protected OffsetDateTime endDate;

  public String getId() {
    return sourceEventId + ":" + flowNodeId;
  }
}
