/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.migration.FillPostImporterQueuePlan;
import io.camunda.operate.schema.migration.MigrationPlanFactory;
import io.camunda.operate.schema.migration.ReindexPlan;
import io.camunda.operate.schema.migration.ReindexWithQueryAndScriptPlan;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpensearchCondition.class)
public class OpensearchMigrationPlanFactory implements MigrationPlanFactory {

  private final OperateProperties operateProperties;
  private final MigrationProperties migrationProperties;
  private final ObjectMapper objectMapper;
  private final RichOpenSearchClient richOpenSearchClient;

  @Autowired
  public OpensearchMigrationPlanFactory(
      final OperateProperties operateProperties,
      final MigrationProperties migrationProperties,
      @Qualifier("operateObjectMapper") final ObjectMapper objectMapper,
      final RichOpenSearchClient richOpenSearchClient) {
    this.operateProperties = operateProperties;
    this.migrationProperties = migrationProperties;
    this.objectMapper = objectMapper;
    this.richOpenSearchClient = richOpenSearchClient;
  }

  @Override
  public FillPostImporterQueuePlan createFillPostImporterQueuePlan() {
    return new OpensearchFillPostImporterQueuePlan(
        richOpenSearchClient, objectMapper, operateProperties, migrationProperties);
  }

  @Override
  public ReindexPlan createReindexPlan() {
    return new OpensearchPipelineReindexPlan(richOpenSearchClient, migrationProperties);
  }

  @Override
  public ReindexWithQueryAndScriptPlan createReindexWithQueryAndScriptPlan() {
    return new OpensearchReindexWithQueryAndScriptPlan(richOpenSearchClient, migrationProperties);
  }
}
