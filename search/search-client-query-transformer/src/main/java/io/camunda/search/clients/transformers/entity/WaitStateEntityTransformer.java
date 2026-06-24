/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.entities.WaitStateConditionDetails;
import io.camunda.search.entities.WaitStateDetails;
import io.camunda.search.entities.WaitStateDetails.WaitStateType;
import io.camunda.search.entities.WaitStateEntity;
import io.camunda.search.entities.WaitStateJobDetails;
import io.camunda.search.entities.WaitStateMessageDetails;
import io.camunda.search.entities.WaitStateSignalDetails;
import io.camunda.search.entities.WaitStateTimerDetails;
import io.camunda.search.entities.WaitStateUserTaskDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WaitStateEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.waitstate.WaitStateEntity, WaitStateEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(WaitStateEntityTransformer.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Override
  public WaitStateEntity apply(
      final io.camunda.webapps.schema.entities.waitstate.WaitStateEntity value) {
    return new WaitStateEntity(
        value.getProcessInstanceKey(),
        value.getElementInstanceKey(),
        value.getElementId(),
        toFlowNodeType(value.getElementType()),
        value.getRootProcessInstanceKey(),
        value.getBpmnProcessId(),
        parseDetails(value.getWaitStateType(), value.getDetails()),
        value.getTenantId());
  }

  private FlowNodeType toFlowNodeType(final String elementType) {
    if (elementType == null) {
      return FlowNodeType.UNSPECIFIED;
    }
    try {
      return FlowNodeType.valueOf(elementType);
    } catch (final IllegalArgumentException e) {
      LOG.warn("Unknown element type: {}", elementType);
      return FlowNodeType.UNKNOWN;
    }
  }

  private WaitStateDetails parseDetails(final String waitStateType, final String detailsJson) {
    if (waitStateType == null || detailsJson == null) {
      return null;
    }
    try {
      return switch (WaitStateType.valueOf(waitStateType)) {
        case JOB -> OBJECT_MAPPER.readValue(detailsJson, WaitStateJobDetails.class);
        case MESSAGE -> OBJECT_MAPPER.readValue(detailsJson, WaitStateMessageDetails.class);
        case USER_TASK -> OBJECT_MAPPER.readValue(detailsJson, WaitStateUserTaskDetails.class);
        case TIMER -> OBJECT_MAPPER.readValue(detailsJson, WaitStateTimerDetails.class);
        case SIGNAL -> OBJECT_MAPPER.readValue(detailsJson, WaitStateSignalDetails.class);
        case CONDITION -> OBJECT_MAPPER.readValue(detailsJson, WaitStateConditionDetails.class);
      };
    } catch (final Exception e) {
      LOG.warn("Failed to parse wait state details for type {}: {}", waitStateType, e.getMessage());
      return null;
    }
  }
}
