/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.es;

import static io.camunda.operate.util.CollectionUtil.filter;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.management.ElsIndicesCheck;
import io.camunda.operate.schema.ElasticsearchSchemaManager;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.indices.MigrationRepositoryIndex;
import io.camunda.operate.schema.indices.OperateWebSessionIndex;
import io.camunda.operate.schema.migration.ProcessorStep;
import io.camunda.operate.schema.templates.EventTemplate;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.util.ElasticsearchTestRule;
import io.camunda.operate.util.OperateIntegrationTest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.cluster.metadata.MappingMetadata;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class SchemaCreationIT extends OperateIntegrationTest {

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();
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

  @Test
  public void testIndexCreation() throws ExecutionException, InterruptedException, IOException {
    for (IndexDescriptor indexDescriptor : indexDescriptors) {
      assertIndexAndAlias(indexDescriptor.getFullQualifiedName(), indexDescriptor.getAlias());
    }

    //assert schema creation won't be performed for the second time
    assertThat(elsIndicesCheck.indicesArePresent()).isTrue();
  }

  @Test //OPE-1310
  public void testMigrationStepsRepositoryFields() throws IOException {
    IndexDescriptor migrationStepsIndexDescriptor = getIndexDescriptorBy(
        MigrationRepositoryIndex.INDEX_NAME);
    assertThat(migrationStepsIndexDescriptor.getVersion()).isEqualTo("1.1.0");
    assertThat(getFieldDescriptions(migrationStepsIndexDescriptor).keySet())
        .containsExactlyInAnyOrder(
            ProcessorStep.VERSION, "@type", "description",
            ProcessorStep.APPLIED, ProcessorStep.APPLIED_DATE,
            ProcessorStep.CREATED_DATE, ProcessorStep.CONTENT,
            ProcessorStep.INDEX_NAME, ProcessorStep.ORDER);
  }

  @Test //OPE-1308
  public void testDynamicMappingsOfIndices() throws Exception {
    IndexDescriptor sessionIndex = indexDescriptors.stream()
        .filter(indexDescriptor -> indexDescriptor.getIndexName().equals(
            OperateWebSessionIndex.INDEX_NAME)).findFirst().orElseThrow();
    assertThatIndexHasDynamicMappingOf(sessionIndex, "true");

    List<IndexDescriptor> strictMappingIndices = indexDescriptors.stream()
        .filter(indexDescriptor -> !indexDescriptor.getIndexName()
            .equals(OperateWebSessionIndex.INDEX_NAME)).collect(Collectors.toList());

    for (IndexDescriptor indexDescriptor : strictMappingIndices) {
      assertThatIndexHasDynamicMappingOf(indexDescriptor, "strict");
    }
  }

  private Map<String, Object> getFieldDescriptions(IndexDescriptor indexDescriptor)
      throws IOException {
    Map<String, MappingMetadata> mappings = esClient.indices().get(
        new GetIndexRequest(indexDescriptor.getFullQualifiedName()), RequestOptions.DEFAULT)
        .getMappings();
    Map<String, Object> source = mappings.get(indexDescriptor.getFullQualifiedName())
        .getSourceAsMap();
    return (Map<String, Object>) source.get("properties");
  }

  private IndexDescriptor getIndexDescriptorBy(String name) {
    return filter(indexDescriptors, indexDescriptor -> indexDescriptor.getIndexName().equals(name))
        .get(0);
  }

  private void assertThatIndexHasDynamicMappingOf(IndexDescriptor indexDescriptor,
      String dynamicMapping)
      throws IOException {
    Map<String, MappingMetadata> mappings = esClient.indices()
        .get(new GetIndexRequest(indexDescriptor.getFullQualifiedName()), RequestOptions.DEFAULT)
        .getMappings();
    MappingMetadata mappingMetadata = mappings.get(indexDescriptor.getFullQualifiedName());
    assertThat(mappingMetadata.getSourceAsMap()).containsEntry("dynamic", dynamicMapping);
  }

  private void assertIndexAndAlias(String indexName, String aliasName) throws IOException {
    final GetIndexResponse getIndexResponse =
        esClient.indices()
            .get(new GetIndexRequest(indexName), RequestOptions.DEFAULT);

    assertThat(getIndexResponse.getAliases()).hasSize(1);
    assertThat(getIndexResponse.getAliases().get(indexName).get(0).alias()).isEqualTo(aliasName);
  }
}
