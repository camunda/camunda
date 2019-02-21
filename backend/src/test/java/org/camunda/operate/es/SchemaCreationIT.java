/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es;

import java.util.concurrent.ExecutionException;
import org.camunda.operate.es.schema.templates.EventTemplate;
import org.camunda.operate.es.schema.templates.WorkflowInstanceTemplate;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.elasticsearch.action.admin.indices.get.GetIndexResponse;
import org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.script.mustache.SearchTemplateRequestBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;

public class SchemaCreationIT extends OperateIntegrationTest {

  @Autowired
  private TransportClient esClient;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ElasticsearchSchemaManager elasticsearchSchemaManager;

  @Autowired
  private WorkflowInstanceTemplate workflowInstanceTemplate;

  @Autowired
  private EventTemplate eventTemplate;

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Test
  public void testIndexCreation() throws ExecutionException, InterruptedException {
    assertIndexAndAlias(operateProperties.getElasticsearch().getWorkflowIndexName(), operateProperties.getElasticsearch().getWorkflowAlias());
    assertIndexAndAlias(operateProperties.getElasticsearch().getWorkflowInstanceIndexName(), workflowInstanceTemplate.getAlias());
    assertIndexAndAlias(operateProperties.getElasticsearch().getEventIndexName(), eventTemplate.getAlias());
    assertIndexAndAlias(operateProperties.getElasticsearch().getImportPositionIndexName(), operateProperties.getElasticsearch().getImportPositionAlias());

    assertTemplateOrder(workflowInstanceTemplate.getTemplateName(), 30);
    assertTemplateOrder(eventTemplate.getTemplateName(), 30);

    //assert schema creation won't be performed for the second time
    assertThat(elasticsearchSchemaManager.initializeSchema()).isFalse();
  }

  private void assertTemplateOrder(String templateName, int templateOrder) {
    final GetIndexTemplatesResponse getIndexTemplatesResponse =
      esClient.admin().indices()
        .prepareGetTemplates(templateName)
        .get();

    assertThat(getIndexTemplatesResponse.getIndexTemplates()).hasSize(1);
    assertThat(getIndexTemplatesResponse.getIndexTemplates().iterator().next().getOrder()).isEqualTo(templateOrder);
  }

  private void assertIndexAndAlias(String indexName, String aliasName) throws InterruptedException, ExecutionException {
    final GetIndexResponse getIndexResponse =
      esClient.admin().indices()
        .prepareGetIndex().addIndices(indexName
      ).execute().get();

    assertThat(getIndexResponse.getAliases()).hasSize(1);
    assertThat(getIndexResponse.getAliases().valuesIt().next().get(0).getAlias()).isEqualTo(aliasName);
  }

}
