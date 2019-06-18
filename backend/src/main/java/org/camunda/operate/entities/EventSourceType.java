/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.entities;

import io.zeebe.protocol.record.ValueType;

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
