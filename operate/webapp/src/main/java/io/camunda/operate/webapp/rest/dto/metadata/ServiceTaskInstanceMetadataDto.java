/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.camunda.webapps.schema.entities.event.EventEntity;
import io.camunda.webapps.schema.entities.operate.FlowNodeType;
import java.time.OffsetDateTime;

@JsonInclude(Include.NON_NULL)
public class ServiceTaskInstanceMetadataDto extends JobFlowNodeInstanceMetadataDto
    implements FlowNodeInstanceMetadata {

  public ServiceTaskInstanceMetadataDto(
      final String flowNodeId,
      final String flowNodeInstanceId,
      final FlowNodeType flowNodeType,
      final OffsetDateTime startDate,
      final OffsetDateTime endDate,
      final EventEntity event) {
    super(flowNodeId, flowNodeInstanceId, flowNodeType, startDate, endDate, event);
  }

  public ServiceTaskInstanceMetadataDto() {
    super();
  }
}
