/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.CorrelatedMessageSubscriptionEntity;

public enum CorrelatedMessageSubscriptionSearchColumn
    implements SearchColumn<CorrelatedMessageSubscriptionEntity> {
  CORRELATION_KEY("correlationKey"),
  CORRELATION_TIME("correlationTime"),
  FLOW_NODE_ID("flowNodeId"),
  FLOW_NODE_INSTANCE_KEY("flowNodeInstanceKey"),
  MESSAGE_KEY("messageKey"),
  MESSAGE_NAME("messageName"),
  PARTITION_ID("partitionId"),
  PROCESS_DEFINITION_ID("processDefinitionId"),
  PROCESS_DEFINITION_KEY("processDefinitionKey"),
  PROCESS_INSTANCE_KEY("processInstanceKey"),
  SUBSCRIPTION_KEY("subscriptionKey"),
  TENANT_ID("tenantId");

  private final String property;

  CorrelatedMessageSubscriptionSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<CorrelatedMessageSubscriptionEntity> getEntityClass() {
    return CorrelatedMessageSubscriptionEntity.class;
  }
}
