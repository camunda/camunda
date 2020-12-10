/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.entities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum FlowNodeType {

  UNSPECIFIED,
  PROCESS,
  SUB_PROCESS,
  START_EVENT,
  INTERMEDIATE_CATCH_EVENT,
  BOUNDARY_EVENT,
  END_EVENT,
  SERVICE_TASK,
  RECEIVE_TASK,
  EXCLUSIVE_GATEWAY,
  PARALLEL_GATEWAY,
  EVENT_BASED_GATEWAY,
  SEQUENCE_FLOW,
  MULTI_INSTANCE_BODY,
  CALL_ACTIVITY,
  UNKNOWN;

  private static final Logger logger = LoggerFactory.getLogger(FlowNodeType.class);

  public static FlowNodeType fromZeebeBpmnElementType(String bpmnElementType) {
    if (bpmnElementType == null) {
      return UNSPECIFIED;
    }
    try {
      return FlowNodeType.valueOf(bpmnElementType);
    } catch (IllegalArgumentException ex) {
      logger.error("Activity type not found for value [{}]. UNKNOWN type will be assigned.", bpmnElementType);
      return UNKNOWN;
    }
  }

}
