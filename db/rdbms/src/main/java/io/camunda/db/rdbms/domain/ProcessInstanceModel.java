/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.domain;

import java.time.OffsetDateTime;

public record ProcessInstanceModel(
    Long processInstanceKey,
    String bpmnProcessId,
    Long processDefinitionKey,
    State state,
    OffsetDateTime startDate,
    OffsetDateTime endDate,
    String tenantId,
    Long parentProcessInstanceKey,
    Long parentElementInstanceKey,
    String elementId,
    int version) {

  public enum State {
    ACTIVE,
    COMPLETED,
    CANCELED
  }
}
