/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.opensearch.OpensearchSchemaManager;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("schemaManager")
@Conditional(OpensearchCondition.class)
@Profile("test")
public class TestOperateOpenSearchSchemaManager extends OpensearchSchemaManager {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(TestOperateOpenSearchSchemaManager.class);

  public TestOperateOpenSearchSchemaManager(
      final OperateProperties operateProperties,
      final RichOpenSearchClient richOpenSearchClient,
      final List<IndexTemplateDescriptor> templateDescriptors,
      final List<IndexDescriptor> indexDescriptors,
      final ObjectMapper objectMapper) {
    super(
        operateProperties,
        richOpenSearchClient,
        templateDescriptors,
        indexDescriptors,
        objectMapper);
  }

  public void deleteSchema() {
    final String prefix = operateProperties.getElasticsearch().getIndexPrefix();
    LOGGER.info("Removing indices {}*", prefix);
    richOpenSearchClient.index().deleteIndicesWithRetries(prefix + "*");
    LOGGER.info("Removing templates {}*", prefix);
    richOpenSearchClient.template().deleteTemplatesWithRetries(prefix + "*");
  }

  public void setCreateSchema(final boolean createSchema) {
    operateProperties.getOpensearch().setCreateSchema(createSchema);
  }

  public void setIndexPrefix(final String indexPrefix) {
    operateProperties.getOpensearch().setIndexPrefix(indexPrefix);
  }

  public void setDefaultIndexPrefix() {
    operateProperties.getOpensearch().setDefaultIndexPrefix();
  }
}
