/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.factories;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.DatabaseInfoProvider;
import io.camunda.operate.property.DatastoreProperties;
import io.camunda.operate.property.OperateElasticsearchProperties;
import io.camunda.operate.property.OperateOpensearchProperties;
import io.camunda.operate.schema.IndexSchemaValidator;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.SchemaStartup;
import io.camunda.operate.schema.elasticsearch.ElasticsearchSchemaManager;
import io.camunda.operate.schema.opensearch.OpensearchSchemaManager;
import io.camunda.operate.store.elasticsearch.ElasticsearchTaskStore;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;

public class SchemaManagerHolder {

  private static SchemaManagerHolder _instance;

  private SchemaStartup schemaStartup;
  private SchemaManager schemaManager;
  private IndexSchemaValidator indexSchemaValidator;

  public SchemaManagerHolder init(
      final DatabaseInfoProvider databaseInfoProvider,
      final DatastoreProperties datastoreProperties,
      final DatastoreClientHolder datastoreClientHolder,
      final ObjectMapper objectMapper) {
    // TODO synchronize?
    if (_instance != null) {
      // already initialized
      return _instance;
    }
    if (schemaManager == null) {
      switch (databaseInfoProvider.getCurrent()) {
        case Elasticsearch:
          final OperateElasticsearchProperties operateElasticsearchProperties =
              (OperateElasticsearchProperties) datastoreProperties;
          new IndexDescriptorHolder()
              .init(operateElasticsearchProperties.getIndexPrefix(), databaseInfoProvider);
          schemaManager =
              new ElasticsearchSchemaManager(
                  createRetryElasticsearchClient(datastoreClientHolder, objectMapper),
                  operateElasticsearchProperties,
                  IndexDescriptorHolder.getInstance().getIndexDescriptors(),
                  IndexDescriptorHolder.getInstance().getTemplateDescriptors(),
                  objectMapper);
          break;
        case Opensearch:
          final OperateOpensearchProperties operateOpensearchProperties =
              (OperateOpensearchProperties) datastoreProperties;
          new IndexDescriptorHolder()
              .init(operateOpensearchProperties.getIndexPrefix(), databaseInfoProvider);
          schemaManager =
              new OpensearchSchemaManager(
                  operateOpensearchProperties,
                  createRichOpensearchClient(),
                  IndexDescriptorHolder.getInstance().getIndexDescriptors(),
                  IndexDescriptorHolder.getInstance().getTemplateDescriptors());
          break;
      }
      indexSchemaValidator =
          new IndexSchemaValidator(
              IndexDescriptorHolder.getInstance().getIndexAndTemplateDescriptors(), schemaManager);
      // we won't have migrator in the future, so we don't need to have it in exporter
      schemaStartup = new SchemaStartup(schemaManager, indexSchemaValidator, datastoreProperties);
      _instance = this;
    }
    return this;
  }

  public static SchemaManagerHolder getInstance() {
    return _instance;
  }

  public SchemaManager getSchemaManager() {
    checkInitialized();
    return schemaManager;
  }

  public IndexSchemaValidator getIndexSchemaValidator() {
    checkInitialized();
    return indexSchemaValidator;
  }

  public SchemaStartup getSchemaStartup() {
    checkInitialized();
    return schemaStartup;
  }

  private void checkInitialized() {
    if (_instance == null) {
      new IllegalStateException("SchemaManagerHolder is not yet initialized.");
    }
  }

  private RichOpenSearchClient createRichOpensearchClient() {
    // TODO Opensearch
    return null;
  }

  private RetryElasticsearchClient createRetryElasticsearchClient(
      final DatastoreClientHolder datastoreClientHolder, final ObjectMapper objectMapper) {
    return new RetryElasticsearchClient(
        datastoreClientHolder.getEsClient(),
        createElasticsearchTaskStore(datastoreClientHolder, objectMapper),
        objectMapper);
  }

  private ElasticsearchTaskStore createElasticsearchTaskStore(
      final DatastoreClientHolder datastoreClientHolder, final ObjectMapper objectMapper) {
    return new ElasticsearchTaskStore(datastoreClientHolder.getEsClient(), objectMapper);
  }
}
