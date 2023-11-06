/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.elasticsearch;

import io.camunda.operate.management.IndicesCheck;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.indices.MigrationRepositoryIndex;
import io.camunda.operate.schema.indices.OperateWebSessionIndex;
import io.camunda.operate.schema.migration.ProcessorStep;
import io.camunda.operate.schema.templates.EventTemplate;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static io.camunda.operate.util.CollectionUtil.filter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

public class SchemaCreationIT extends OperateAbstractIT {

  @Rule
  public SearchTestRule searchTestRule = new SearchTestRule();
  @Autowired
  private TestSearchRepository testSearchRepository;
  @Autowired
  private SchemaManager schemaManager;
  @Autowired
  private IncidentTemplate processInstanceTemplate;
  @Autowired
  private EventTemplate eventTemplate;
  @Autowired
  private List<IndexDescriptor> indexDescriptors;
  @Autowired
  private IndicesCheck indicesCheck;

  @Test
  public void testIndexCreation() throws ExecutionException, InterruptedException, IOException {
    for (IndexDescriptor indexDescriptor : indexDescriptors) {
      assertIndexAndAlias(indexDescriptor.getFullQualifiedName(), indexDescriptor.getAlias());
    }

    //assert schema creation won't be performed for the second time
    assertThat(indicesCheck.indicesArePresent()).isTrue();
  }

  @Test //OPE-1310
  public void testMigrationStepsRepositoryFields() throws IOException {
    IndexDescriptor migrationStepsIndexDescriptor = getIndexDescriptorBy(
        MigrationRepositoryIndex.INDEX_NAME);
    assertThat(migrationStepsIndexDescriptor.getVersion()).isEqualTo("1.1.0");
    assertThat(testSearchRepository.getFieldNames(migrationStepsIndexDescriptor.getFullQualifiedName()))
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
    assertThatIndexHasDynamicMappingOf(sessionIndex, TestSearchRepository.DynamicMappingType.True);

    List<IndexDescriptor> strictMappingIndices = indexDescriptors.stream()
        .filter(indexDescriptor -> !indexDescriptor.getIndexName()
            .equals(OperateWebSessionIndex.INDEX_NAME)).collect(Collectors.toList());

    for (IndexDescriptor indexDescriptor : strictMappingIndices) {
      assertThatIndexHasDynamicMappingOf(indexDescriptor, TestSearchRepository.DynamicMappingType.Strict);
    }
  }

  private IndexDescriptor getIndexDescriptorBy(String name) {
    return filter(indexDescriptors, indexDescriptor -> indexDescriptor.getIndexName().equals(name))
        .get(0);
  }

  private void assertThatIndexHasDynamicMappingOf(IndexDescriptor indexDescriptor, TestSearchRepository.DynamicMappingType dynamicMappingType) throws IOException {
    assertTrue(testSearchRepository.hasDynamicMapping(indexDescriptor.getFullQualifiedName(), dynamicMappingType));
  }

  private void assertIndexAndAlias(String indexName, String aliasName) throws IOException {
    List<String> aliaseNames = testSearchRepository.getAliasNames(indexName);

    assertThat(aliaseNames).hasSize(1);
    assertThat(aliaseNames.get(0)).isEqualTo(aliasName);
  }
}
