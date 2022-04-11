/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.process;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.time.OffsetDateTime;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Data
@FieldNameConstants
public class FlowNodeInstanceUpdateDto implements OptimizeDto {
  protected String sourceEventId;
  protected String flowNodeId;
  protected String flowNodeType;
  protected MappedEventType mappedAs;
  protected OffsetDateTime date;

  public String getId() {
    return sourceEventId + ":" + flowNodeId;
  }
}
