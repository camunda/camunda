/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.flownode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum FlowNodeType {
  UNSPECIFIED,
  PROCESS,
  SUB_PROCESS,
  EVENT_SUB_PROCESS,
  AD_HOC_SUB_PROCESS,
  AD_HOC_SUB_PROCESS_INNER_INSTANCE,
  START_EVENT,
  INTERMEDIATE_CATCH_EVENT,
  INTERMEDIATE_THROW_EVENT,
  BOUNDARY_EVENT,
  END_EVENT,
  SERVICE_TASK,
  RECEIVE_TASK,
  USER_TASK,
  MANUAL_TASK,
  TASK,
  EXCLUSIVE_GATEWAY,
  INCLUSIVE_GATEWAY,
  PARALLEL_GATEWAY,
  EVENT_BASED_GATEWAY,
  SEQUENCE_FLOW,
  MULTI_INSTANCE_BODY,
  CALL_ACTIVITY,
  BUSINESS_RULE_TASK,
  SCRIPT_TASK,
  SEND_TASK,

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
