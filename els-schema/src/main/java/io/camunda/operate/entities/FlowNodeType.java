/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum FlowNodeType {

  UNSPECIFIED,
  PROCESS,
  SUB_PROCESS,
  EVENT_SUB_PROCESS,
  START_EVENT,
  INTERMEDIATE_CATCH_EVENT,
  INTERMEDIATE_THROW_EVENT,
  BOUNDARY_EVENT,
  END_EVENT,
  SERVICE_TASK,
  RECEIVE_TASK,
  USER_TASK,
  MANUAL_TASK,
  EXCLUSIVE_GATEWAY,
  PARALLEL_GATEWAY,
  EVENT_BASED_GATEWAY,
  SEQUENCE_FLOW,
  MULTI_INSTANCE_BODY,
  CALL_ACTIVITY,
  BUSINESS_RULE_TASK,
  SCRIPT_TASK,
  SEND_TASK,

  UNKNOWN;

  private static final Logger logger = LoggerFactory.getLogger(FlowNodeType.class);

  public static FlowNodeType fromZeebeBpmnElementType(String bpmnElementType) {
    if (bpmnElementType == null) {
      return UNSPECIFIED;
    }
    try {
      return FlowNodeType.valueOf(bpmnElementType);
    } catch (IllegalArgumentException ex) {
      logger.error("Flow node type not found for value [{}]. UNKNOWN type will be assigned.", bpmnElementType);
      return UNKNOWN;
    }
  }

}
