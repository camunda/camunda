/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.util;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OperateOpensearchProperties;
import io.camunda.operate.schema.indices.AbstractIndexDescriptor;
import io.camunda.operate.schema.opensearch.OpensearchSchemaManager;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("schemaManager")
@Conditional(OpensearchCondition.class)
@Profile("test")
public class TestOpensearchSchemaManager extends OpensearchSchemaManager
    implements TestSchemaManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestOpensearchSchemaManager.class);

  @Autowired
  public TestOpensearchSchemaManager(
      final OperateOpensearchProperties operateOpensearchProperties,
      final RichOpenSearchClient richOpenSearchClient,
      final List<TemplateDescriptor> templateDescriptors,
      final List<AbstractIndexDescriptor> indexDescriptors) {
    super(operateOpensearchProperties, richOpenSearchClient, indexDescriptors, templateDescriptors);
  }

  @Override
  public void deleteSchema() {
    final String prefix = operateOpensearchProperties.getIndexPrefix();
    LOGGER.info("Removing indices {}*", prefix);
    richOpenSearchClient.index().deleteIndicesWithRetries(prefix + "*");
    richOpenSearchClient.template().deleteTemplatesWithRetries(prefix + "*");
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
    operateOpensearchProperties.setCreateSchema(createSchema);
  }

  @Override
  public void setIndexPrefix(final String indexPrefix) {
    operateOpensearchProperties.setIndexPrefix(indexPrefix);
  }

  @Override
  public void setDefaultIndexPrefix() {
    operateOpensearchProperties.setDefaultIndexPrefix();
  }
}
