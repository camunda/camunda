/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.camunda.operate.es.schema.indices.IndexCreator;
import org.camunda.operate.es.schema.templates.EventTemplate;
import org.camunda.operate.es.schema.templates.IncidentTemplate;
import org.camunda.operate.es.schema.templates.TemplateCreator;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexResponse;
import org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexTemplatesRequest;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;

public class SchemaCreationIT extends OperateIntegrationTest {

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ElasticsearchSchemaManager elasticsearchSchemaManager;

  @Autowired
  private IncidentTemplate workflowInstanceTemplate;

  @Autowired
  private EventTemplate eventTemplate;

  @Autowired
  private List<IndexCreator> indexCreators;

  @Autowired
  private List<TemplateCreator> templateCreators;

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Test
  public void testIndexCreation() throws ExecutionException, InterruptedException, IOException {
    for (TemplateCreator templateCreator: templateCreators) {
      assertIndexAndAlias(templateCreator.getMainIndexName(), templateCreator.getAlias());
    }

    for (IndexCreator indexCreator: indexCreators) {
      assertIndexAndAlias(indexCreator.getIndexName(), indexCreator.getAlias());
    }

    assertTemplateOrder(workflowInstanceTemplate.getTemplateName(), 30);
    assertTemplateOrder(eventTemplate.getTemplateName(), 30);

    //assert schema creation won't be performed for the second time
    assertThat(elasticsearchSchemaManager.schemaAlreadyExists()).isTrue();
  }

  private void assertTemplateOrder(String templateName, int templateOrder) throws IOException {
    final GetIndexTemplatesResponse getIndexTemplatesResponse =
      esClient.indices()
        .getTemplate(new GetIndexTemplatesRequest(templateName), RequestOptions.DEFAULT);

    assertThat(getIndexTemplatesResponse.getIndexTemplates()).hasSize(1);
    assertThat(getIndexTemplatesResponse.getIndexTemplates().iterator().next().getOrder()).isEqualTo(templateOrder);
  }

  private void assertIndexAndAlias(String indexName, String aliasName) throws InterruptedException, ExecutionException, IOException {
    final GetIndexResponse getIndexResponse =
      esClient.indices()
        .get(new GetIndexRequest().indices(indexName), RequestOptions.DEFAULT);

    assertThat(getIndexResponse.getAliases()).hasSize(1);
    assertThat(getIndexResponse.getAliases().valuesIt().next().get(0).getAlias()).isEqualTo(aliasName);
  }

}
