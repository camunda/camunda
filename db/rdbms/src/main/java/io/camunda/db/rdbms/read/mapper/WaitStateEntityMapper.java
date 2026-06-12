/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.db.rdbms.write.domain.WaitStateDbModel;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.entities.WaitStateConditionDetails;
import io.camunda.search.entities.WaitStateDetails;
import io.camunda.search.entities.WaitStateDetails.WaitStateType;
import io.camunda.search.entities.WaitStateEntity;
import io.camunda.search.entities.WaitStateJobDetails;
import io.camunda.search.entities.WaitStateMessageDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WaitStateEntityMapper {

  private static final Logger LOG = LoggerFactory.getLogger(WaitStateEntityMapper.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static WaitStateEntity toEntity(final WaitStateDbModel dbModel) {
    return new WaitStateEntity(
        dbModel.processInstanceKey(),
        dbModel.elementInstanceKey(),
        dbModel.elementId(),
        toFlowNodeType(dbModel.elementType()),
        dbModel.rootProcessInstanceKey(),
        parseDetails(dbModel.waitStateType(), dbModel.details()),
        dbModel.tenantId());
  }

  private static FlowNodeType toFlowNodeType(final String elementType) {
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

  private static WaitStateDetails parseDetails(
      final String waitStateType, final String detailsJson) {
    if (waitStateType == null || detailsJson == null) {
      return null;
    }
    try {
      final WaitStateType type = WaitStateType.valueOf(waitStateType);
      return switch (type) {
        case JOB -> OBJECT_MAPPER.readValue(detailsJson, WaitStateJobDetails.class);
        case MESSAGE -> OBJECT_MAPPER.readValue(detailsJson, WaitStateMessageDetails.class);
        case CONDITION -> OBJECT_MAPPER.readValue(detailsJson, WaitStateConditionDetails.class);
      };
    } catch (final Exception e) {
      LOG.warn("Failed to parse wait state details for type {}: {}", waitStateType, e.getMessage());
      return null;
    }
  }
}
