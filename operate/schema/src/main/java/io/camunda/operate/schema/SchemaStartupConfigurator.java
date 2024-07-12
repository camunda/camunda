/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.DatabaseInfoProvider;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.elasticsearch.ElasticsearchSchemaManager;
import io.camunda.operate.schema.indices.AbstractIndexDescriptor;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.migration.Migrator;
import io.camunda.operate.schema.opensearch.OpensearchSchemaManager;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.store.elasticsearch.ElasticsearchTaskStore;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import io.camunda.operate.store.opensearch.OpensearchTaskStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import java.util.List;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;

@Configuration
public class SchemaStartupConfigurator {

  @Bean("schemaStartup")
  @DependsOn("databaseInfo")
  public SchemaWithMigrationStartup getSchemaStartup(
      final SchemaManager schemaManager,
      final IndexSchemaValidator schemaValidator,
      final OperateProperties operateProperties,
      final Migrator migrator,
      final MigrationProperties migrationProperties,
      final DatabaseInfoProvider databaseInfoProvider) {
    return new SchemaWithMigrationStartup(
        schemaManager,
        schemaValidator,
        databaseInfoProvider.isElasticsearch()
            ? operateProperties.getElasticsearch()
            : operateProperties.getOpensearch(),
        migrator,
        migrationProperties);
  }

  @Conditional(ElasticsearchCondition.class)
  @Bean("schemaManager")
  @Profile("!test")
  public SchemaManager getElasticsearchSchemaManager(
      final RetryElasticsearchClient retryElasticsearchClient,
      final OperateProperties operateProperties,
      final List<AbstractIndexDescriptor> indexDescriptors,
      final List<TemplateDescriptor> templateDescriptors,
      final ObjectMapper objectMapper) {
    return new ElasticsearchSchemaManager(
        retryElasticsearchClient,
        operateProperties.getElasticsearch(),
        indexDescriptors,
        templateDescriptors,
        objectMapper);
  }

  @Conditional(ElasticsearchCondition.class)
  @Bean("taskStore")
  public ElasticsearchTaskStore getElasticsearchTaskStore(
      final RestHighLevelClient esClient, final ObjectMapper objectMapper) {
    return new ElasticsearchTaskStore(esClient, objectMapper);
  }

  @Conditional(OpensearchCondition.class)
  @Bean("schemaManager")
  @Profile("!test")
  public SchemaManager getOpensearchSchemaManager(
      final RichOpenSearchClient richOpenSearchClient,
      final OperateProperties operateProperties,
      final List<AbstractIndexDescriptor> indexDescriptors,
      final List<TemplateDescriptor> templateDescriptors) {
    return new OpensearchSchemaManager(
        operateProperties.getOpensearch(),
        richOpenSearchClient,
        indexDescriptors,
        templateDescriptors);
  }

  @Conditional(OpensearchCondition.class)
  @Bean("taskStore")
  public OpensearchTaskStore getOpensearchTaskStore(
      final RichOpenSearchClient richOpenSearchClient) {
    return new OpensearchTaskStore(richOpenSearchClient);
  }

  @Conditional(ElasticsearchCondition.class)
  @Bean
  public RetryElasticsearchClient getRetryElasticsearchClient(
      final RestHighLevelClient esClient,
      final ElasticsearchTaskStore taskStore,
      final ObjectMapper objectMapper) {
    return new RetryElasticsearchClient(esClient, taskStore, objectMapper);
  }

  @Bean
  public IndexSchemaValidator getIndexSchemaValidator(
      final List<IndexDescriptor> indexDescriptors, final SchemaManager schemaManager) {
    return new IndexSchemaValidator(indexDescriptors, schemaManager);
  }
}
