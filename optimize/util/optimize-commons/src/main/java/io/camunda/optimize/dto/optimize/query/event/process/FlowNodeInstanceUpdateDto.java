/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Data
public class FlowNodeInstanceUpdateDto implements OptimizeDto {

  protected String sourceEventId;
  protected String flowNodeId;
  protected String flowNodeType;
  protected MappedEventType mappedAs;
  protected OffsetDateTime date;

  public String getId() {
    return sourceEventId + ":" + flowNodeId;
  }

  public static final class Fields {

    public static final String sourceEventId = "sourceEventId";
    public static final String flowNodeId = "flowNodeId";
    public static final String flowNodeType = "flowNodeType";
    public static final String mappedAs = "mappedAs";
    public static final String date = "date";
  }
}
