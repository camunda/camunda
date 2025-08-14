/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.util.opensearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.IndexMapping;
import io.camunda.operate.schema.IndexMapping.IndexMappingProperty;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.indices.AbstractIndexDescriptor;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.schema.util.SchemaTestHelper;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch.indices.GetIndexTemplateRequest;
import org.opensearch.client.opensearch.indices.GetIndexTemplateResponse;
import org.opensearch.client.opensearch.indices.PutIndicesSettingsRequest;
import org.opensearch.client.opensearch.indices.get_index_template.IndexTemplate;
import org.opensearch.client.opensearch.indices.get_index_template.IndexTemplateItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;

@Conditional(OpensearchCondition.class)
public class OpenSearchSchemaTestHelper implements SchemaTestHelper {

  @Autowired private SchemaManager schemaManager;

  @Autowired private RichOpenSearchClient openSearchClient;

  @Autowired private OpenSearchClient lowLevelOpenSearchClient;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private OperateProperties properties;

  @Override
  public void dropSchema() {
    final String openSearchObjectPrefix = properties.getOpensearch().getIndexPrefix() + "-*";
    final Set<String> indexesToDelete = schemaManager.getIndexNames(openSearchObjectPrefix);
    if (!indexesToDelete.isEmpty()) {
      // fails if there are no matching indexes
      setReadOnly(openSearchObjectPrefix, false);
    }

    schemaManager.deleteIndicesFor(openSearchObjectPrefix);
    schemaManager.deleteTemplatesFor(openSearchObjectPrefix);
  }

  @Override
  public IndexMapping getTemplateMappings(final TemplateDescriptor template) {
    try {
      final String templateName = template.getTemplateName();

      final GetIndexTemplateRequest request =
          new GetIndexTemplateRequest.Builder().name(templateName).build();

      final GetIndexTemplateResponse indexTemplateResponse =
          lowLevelOpenSearchClient.indices().getIndexTemplate(request);
      final List<IndexTemplateItem> indexTemplates = indexTemplateResponse.indexTemplates();

      if (indexTemplates.isEmpty()) {
        return null;
      } else if (indexTemplates.size() > 1) {
        throw new OperateRuntimeException(
            String.format(
                "Found more than one template matching name %s. Expected one.", templateName));
      }

      final IndexTemplate indexTemplate = indexTemplates.get(0).indexTemplate();
      final Map<String, Property> properties = indexTemplate.template().mappings().properties();

      return new IndexMapping()
          .setIndexName(templateName)
          .setProperties(
              properties.entrySet().stream().map(this::mapProperty).collect(Collectors.toSet()));
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
    final PutIndicesSettingsRequest updateSettingsRequest =
        new PutIndicesSettingsRequest.Builder()
            .index(indexName)
            .settings(b -> b.blocksReadOnly(readOnly))
            .build();

    try {
      openSearchClient.index().putSettings(updateSettingsRequest);
    } catch (final IOException e) {
      throw new OperateRuntimeException(e);
    }
  }

  @Override
  public Map<String, String> getComponentTemplateSettings(final String componentTemplateName) {
    return Map.of(); // TODO
  }

  protected IndexMappingProperty mapProperty(final Entry<String, Property> property)
      throws OperateRuntimeException {

    final String propertyAsJson = openSearchClient.index().toJsonString(property.getValue());

    final Map<String, Object> propertyAsMap;
    try {
      propertyAsMap =
          objectMapper.readValue(propertyAsJson, new TypeReference<HashMap<String, Object>>() {});
    } catch (final JsonProcessingException e) {
      throw new OperateRuntimeException(e);
    }

    return new IndexMappingProperty().setName(property.getKey()).setTypeDefinition(propertyAsMap);
  }
}
