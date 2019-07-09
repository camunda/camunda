/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.EventSourceType;
import org.camunda.operate.entities.EventType;

public abstract class MockUtil {

  public static EventEntity createEventEntity(Long workflowKey, Long workflowInstanceId, EventSourceType eventSourceType, EventType eventType) {
    EventEntity eventEntity = new EventEntity();
    eventEntity.setId(TestUtil.createRandomString(10));
    eventEntity.setWorkflowKey(workflowKey);
    eventEntity.setWorkflowInstanceKey(workflowInstanceId);
    eventEntity.setEventSourceType(eventSourceType);
    eventEntity.setEventType(eventType);
    return eventEntity;
  }

}
