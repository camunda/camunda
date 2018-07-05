package org.camunda.operate.entities;

import io.zeebe.client.api.record.ValueType;

public enum EventSourceType {

  JOB,
  WORKFLOW_INSTANCE,
  INCIDENT,
  UNKNOWN;

  public static EventSourceType fromZeebeValueType(ValueType valueType) {
    switch (valueType) {
    case JOB:
      return JOB;
    case INCIDENT:
      return INCIDENT;
    case WORKFLOW_INSTANCE:
      return WORKFLOW_INSTANCE;
    default:
      return UNKNOWN;
    }
  }

}
