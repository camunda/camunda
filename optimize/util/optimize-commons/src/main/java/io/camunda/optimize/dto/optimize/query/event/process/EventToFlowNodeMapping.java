/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class EventToFlowNodeMapping {

  @EqualsAndHashCode.Include private String eventIdentifier;
  private MappedEventType mappedAs;
  private String flowNodeId;
  private String flowNodeType;
  private List<String> previousMappedFlowNodeIds;
  private List<String> nextMappedFlowNodeIds;

  public static final class Fields {

    public static final String eventIdentifier = "eventIdentifier";
    public static final String mappedAs = "mappedAs";
    public static final String flowNodeId = "flowNodeId";
    public static final String flowNodeType = "flowNodeType";
    public static final String previousMappedFlowNodeIds = "previousMappedFlowNodeIds";
    public static final String nextMappedFlowNodeIds = "nextMappedFlowNodeIds";
  }
}
