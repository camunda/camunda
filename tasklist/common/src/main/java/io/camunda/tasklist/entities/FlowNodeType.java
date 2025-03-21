/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.entities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum FlowNodeType {
  UNSPECIFIED,
  PROCESS,
  SUB_PROCESS,
  EVENT_SUB_PROCESS,
  START_EVENT,
  INTERMEDIATE_CATCH_EVENT,
  BOUNDARY_EVENT,
  END_EVENT,
  SERVICE_TASK,
  USER_TASK,
  RECEIVE_TASK,
  EXCLUSIVE_GATEWAY,
  PARALLEL_GATEWAY,
  EVENT_BASED_GATEWAY,
  SEQUENCE_FLOW,
  MULTI_INSTANCE_BODY,
  CALL_ACTIVITY,
  AD_HOC_SUB_PROCESS,
  UNKNOWN;

  private static final Logger LOGGER = LoggerFactory.getLogger(FlowNodeType.class);

  public static FlowNodeType fromZeebeBpmnElementType(final String bpmnElementType) {
    if (bpmnElementType == null) {
      return UNSPECIFIED;
    }
    try {
      return FlowNodeType.valueOf(bpmnElementType);
    } catch (final IllegalArgumentException ex) {
      LOGGER.error(
          "Flow node type not found for value [{}]. UNKNOWN type will be assigned.",
          bpmnElementType);
      return UNKNOWN;
    }
  }
}
