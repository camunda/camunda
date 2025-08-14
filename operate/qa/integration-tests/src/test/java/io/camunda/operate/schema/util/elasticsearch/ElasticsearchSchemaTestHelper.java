/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.util.elasticsearch;

import static io.camunda.operate.schema.IndexMapping.IndexMappingProperty.createIndexMappingProperty;
import static io.camunda.operate.schema.SchemaManager.NO_REPLICA;
import static io.camunda.operate.store.elasticsearch.RetryElasticsearchClient.DEFAULT_SHARDS;
import static io.camunda.operate.store.elasticsearch.RetryElasticsearchClient.NUMBERS_OF_REPLICA;
import static io.camunda.operate.store.elasticsearch.RetryElasticsearchClient.NUMBERS_OF_SHARDS;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.IndexMapping;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.indices.AbstractIndexDescriptor;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.schema.util.SchemaTestHelper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetComponentTemplatesRequest;
import org.elasticsearch.client.indices.GetComposableIndexTemplateRequest;
import org.elasticsearch.cluster.metadata.ComposableIndexTemplate;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;

@Conditional(ElasticsearchCondition.class)
public class ElasticsearchSchemaTestHelper implements SchemaTestHelper {

  @Autowired private SchemaManager schemaManager;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private OperateProperties properties;

  @Override
  public void dropSchema() {
    final String elasticsearchObjectPrefix = properties.getElasticsearch().getIndexPrefix() + "-*";
    final Set<String> indexesToDelete = schemaManager.getIndexNames(elasticsearchObjectPrefix);
    if (!indexesToDelete.isEmpty()) {
      // fails if there are no matching indexes
      setReadOnly(elasticsearchObjectPrefix, false);
    }

    schemaManager.deleteIndicesFor(elasticsearchObjectPrefix);
    schemaManager.deleteTemplatesFor(elasticsearchObjectPrefix);
  }

  @Override
  public IndexMapping getTemplateMappings(final TemplateDescriptor template) {
    try {
      final String templateName = template.getTemplateName();
      final Map<String, ComposableIndexTemplate> indexTemplates =
          esClient
              .indices()
              .getIndexTemplate(
                  new GetComposableIndexTemplateRequest(templateName), RequestOptions.DEFAULT)
              .getIndexTemplates();

      if (indexTemplates.isEmpty()) {
        return null;
      } else if (indexTemplates.size() > 1) {
        throw new OperateRuntimeException(
            String.format(
                "Found more than one template matching name %s. Expected one.", templateName));
      }

      final Map<String, Object> mappingMetadata =
          (Map<String, Object>)
              objectMapper
                  .readValue(
                      indexTemplates.get(templateName).template().mappings().toString(),
                      new TypeReference<HashMap<String, Object>>() {})
                  .get("properties");
      return new IndexMapping()
          .setIndexName(templateName)
          .setProperties(
              mappingMetadata.entrySet().stream()
                  .map(p -> createIndexMappingProperty(p))
                  .collect(Collectors.toSet()));
    } catch (final ElasticsearchException e) {
      if (e.status().equals(RestStatus.NOT_FOUND)) {
        return null;
      }
      throw e;
    } catch (final IOException e) {
      throw new OperateRuntimeException(e);
    }
  }

  @Override
  public void createIndex(
      final IndexDescriptor indexDescriptor,
      final String indexName,
      final String indexSchemaFilename) {
    schemaManager.createIndex(
        new AbstractIndexDescriptor() {
          @Override
          public String getIndexName() {
            return indexDescriptor.getIndexName();
          }

          @Override
          public String getFullQualifiedName() {
            return indexName;
          }
        },
        indexSchemaFilename);
  }

  @Override
  public void setReadOnly(final String indexName, final boolean readOnly) {
    final UpdateSettingsRequest updateSettingsRequest =
        new UpdateSettingsRequest()
            .indices(indexName)
            .settings(Map.of("index.blocks.read_only", readOnly));
    try {
      esClient.indices().putSettings(updateSettingsRequest, RequestOptions.DEFAULT);
    } catch (final IOException e) {
      throw new OperateRuntimeException(e);
    }
  }

  @Override
  public Map<String, String> getComponentTemplateSettings(final String componentTemplateName) {
    final Settings settings;
    try {
      settings =
          esClient
              .cluster()
              .getComponentTemplate(
                  new GetComponentTemplatesRequest(componentTemplateName), RequestOptions.DEFAULT)
              .getComponentTemplates()
              .get(0)
              .template()
              .settings();
    } catch (final IOException e) {
      throw new OperateRuntimeException(e);
    }
    return Map.of(
        NUMBERS_OF_REPLICA,
        settings.get(NUMBERS_OF_REPLICA, NO_REPLICA),
        NUMBERS_OF_SHARDS,
        settings.get(NUMBERS_OF_SHARDS, DEFAULT_SHARDS));
  }
}
