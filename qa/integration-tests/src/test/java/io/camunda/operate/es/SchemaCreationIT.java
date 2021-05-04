/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.es;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import io.camunda.operate.management.ElsIndicesCheck;
import io.camunda.operate.schema.ElasticsearchSchemaManager;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.templates.EventTemplate;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.util.ElasticsearchTestRule;
import io.camunda.operate.util.OperateIntegrationTest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.client.indices.GetIndexTemplatesResponse;
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
  private ElasticsearchSchemaManager schemaManager;

  @Autowired
  private IncidentTemplate processInstanceTemplate;

  @Autowired
  private EventTemplate eventTemplate;

  @Autowired
  private List<IndexDescriptor> indexDescriptors;

  @Autowired
  private ElsIndicesCheck elsIndicesCheck;

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Test
  public void testIndexCreation() throws ExecutionException, InterruptedException, IOException {
    for (IndexDescriptor indexDescriptor: indexDescriptors) {
      assertIndexAndAlias(indexDescriptor.getFullQualifiedName(), indexDescriptor.getAlias());
    }

    //assert schema creation won't be performed for the second time
    assertThat(elsIndicesCheck.indicesArePresent()).isTrue();
  }

  private void assertTemplateOrder(String templateName, int templateOrder) throws IOException {
    final GetIndexTemplatesResponse getIndexTemplatesResponse =
      esClient.indices()
        .getIndexTemplate(new GetIndexTemplatesRequest(templateName), RequestOptions.DEFAULT);

    assertThat(getIndexTemplatesResponse.getIndexTemplates()).hasSize(1);
    assertThat(getIndexTemplatesResponse.getIndexTemplates().iterator().next().order()).isEqualTo(templateOrder);
  }

  private void assertIndexAndAlias(String indexName, String aliasName) throws InterruptedException, ExecutionException, IOException {
    final GetIndexResponse getIndexResponse =
      esClient.indices()
        .get(new GetIndexRequest(indexName), RequestOptions.DEFAULT);

    assertThat(getIndexResponse.getAliases()).hasSize(1);
    assertThat(getIndexResponse.getAliases().get(indexName).get(0).alias()).isEqualTo(aliasName);
  }

}
