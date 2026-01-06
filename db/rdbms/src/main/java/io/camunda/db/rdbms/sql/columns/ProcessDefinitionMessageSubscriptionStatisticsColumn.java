/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.ProcessDefinitionMessageSubscriptionStatisticsEntity;

public enum ProcessDefinitionMessageSubscriptionStatisticsColumn
    implements SearchColumn<ProcessDefinitionMessageSubscriptionStatisticsEntity> {
  PROCESS_DEFINITION_KEY("processDefinitionKey"),
  TENANT_ID("tenantId");

  private final String property;

  ProcessDefinitionMessageSubscriptionStatisticsColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<ProcessDefinitionMessageSubscriptionStatisticsEntity> getEntityClass() {
    return ProcessDefinitionMessageSubscriptionStatisticsEntity.class;
  }
}
