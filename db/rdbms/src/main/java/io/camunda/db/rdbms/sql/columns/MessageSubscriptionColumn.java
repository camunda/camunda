/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.MessageSubscriptionEntity;

public enum MessageSubscriptionColumn implements SearchColumn<MessageSubscriptionEntity> {
  MESSAGE_SUBSCRIPTION_KEY("messageSubscriptionKey"),
  PROCESS_DEFINITION_ID("processDefinitionId"),
  PROCESS_DEFINITION_KEY("processDefinitionKey"),
  PROCESS_INSTANCE_KEY("processInstanceKey"),
  FLOW_NODE_ID("flowNodeId"),
  FLOW_NODE_INSTANCE_KEY("flowNodeInstanceKey"),
  MESSAGE_SUBSCRIPTION_TYPE("messageSubscriptionType"),
  DATE_TIME("dateTime"),
  MESSAGE_NAME("messageName"),
  CORRELATION_KEY("correlationKey"),
  TENANT_ID("tenantId");

  private final String property;

  MessageSubscriptionColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<MessageSubscriptionEntity> getEntityClass() {
    return MessageSubscriptionEntity.class;
  }
}
