/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AdHocSubprocessActivityEntity(
    Long processDefinitionKey,
    String processDefinitionId,
    String adHocSubprocessId,
    String elementId,
    String elementName,
    ActivityType type,
    String documentation,
    String tenantId) {

  public enum ActivityType {
    UNSPECIFIED,
    PROCESS,
    SUB_PROCESS,
    EVENT_SUB_PROCESS,
    INTERMEDIATE_CATCH_EVENT,
    INTERMEDIATE_THROW_EVENT,
    BOUNDARY_EVENT,
    SERVICE_TASK,
    RECEIVE_TASK,
    USER_TASK,
    MANUAL_TASK,
    TASK,
    MULTI_INSTANCE_BODY,
    CALL_ACTIVITY,
    BUSINESS_RULE_TASK,
    SCRIPT_TASK,
    SEND_TASK,
    UNKNOWN;

    public static ActivityType fromZeebeBpmnElementTypeName(final String elementTypeName) {
      return fromZeebeBpmnElementType(BpmnElementType.bpmnElementTypeFor(elementTypeName));
    }

    public static ActivityType fromZeebeBpmnElementType(final BpmnElementType bpmnElementType) {
      if (bpmnElementType == null) {
        return UNSPECIFIED;
      }

      try {
        return ActivityType.valueOf(bpmnElementType.name());
      } catch (final IllegalArgumentException ex) {
        return UNKNOWN;
      }
    }
  }
}
