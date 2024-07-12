/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.elasticsearch.ElasticsearchSchemaManager;
import io.camunda.operate.schema.indices.AbstractIndexDescriptor;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("schemaManager")
@Conditional(ElasticsearchCondition.class)
@Profile("test")
public class TestElasticsearchSchemaManager extends ElasticsearchSchemaManager
    implements TestSchemaManager {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(TestElasticsearchSchemaManager.class);

  public TestElasticsearchSchemaManager(
      final RetryElasticsearchClient retryElasticsearchClient,
      final OperateProperties operateProperties,
      final List<AbstractIndexDescriptor> indexDescriptors,
      final List<TemplateDescriptor> templateDescriptors,
      final ObjectMapper objectMapper) {
    super(
        retryElasticsearchClient,
        operateProperties.getElasticsearch(),
        indexDescriptors,
        templateDescriptors,
        objectMapper);
  }

  @Override
  public void deleteSchema() {
    final String prefix = operateElasticsearchProperties.getIndexPrefix();
    LOGGER.info("Removing indices {}*", prefix);
    retryElasticsearchClient.deleteIndicesFor(prefix + "*");
    retryElasticsearchClient.deleteTemplatesFor(prefix + "*");
  }

  @Override
  public void deleteSchemaQuietly() {
    try {
      deleteSchema();
    } catch (final Exception t) {
      LOGGER.debug(t.getMessage());
    }
  }

  @Override
  public void setCreateSchema(final boolean createSchema) {
    operateElasticsearchProperties.setCreateSchema(createSchema);
  }

  @Override
  public void setIndexPrefix(final String indexPrefix) {
    operateElasticsearchProperties.setIndexPrefix(indexPrefix);
  }

  @Override
  public void setDefaultIndexPrefix() {
    operateElasticsearchProperties.setDefaultIndexPrefix();
  }
}
