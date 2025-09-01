/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.CorrelatedMessageEntity;

public enum CorrelatedMessageColumn implements SearchColumn<CorrelatedMessageEntity> {
  MESSAGE_KEY("messageKey"),
  MESSAGE_NAME("messageName"),
  CORRELATION_KEY("correlationKey"),
  PROCESS_INSTANCE_KEY("processInstanceKey"),
  FLOW_NODE_INSTANCE_KEY("flowNodeInstanceKey"),
  START_EVENT_ID("startEventId"),
  BPMN_PROCESS_ID("bpmnProcessId"),
  VARIABLES("variables"),
  TENANT_ID("tenantId"),
  DATE_TIME("dateTime");

  private final String property;

  CorrelatedMessageColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<CorrelatedMessageEntity> getEntityClass() {
    return CorrelatedMessageEntity.class;
  }
}