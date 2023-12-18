/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.es;

import static io.camunda.tasklist.util.CollectionUtil.filter;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.schema.IndexSchemaValidator;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import io.camunda.tasklist.schema.indices.MigrationRepositoryIndex;
import io.camunda.tasklist.schema.indices.TasklistWebSessionIndex;
import io.camunda.tasklist.schema.migration.ProcessorStep;
import io.camunda.tasklist.util.DatabaseTestExtension;
import io.camunda.tasklist.util.NoSqlHelper;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;

public class SchemaCreationIT extends TasklistIntegrationTest {

  @RegisterExtension @Autowired public DatabaseTestExtension databaseTestExtension;

  @Autowired private List<IndexDescriptor> indexDescriptors;
  @Autowired private IndexSchemaValidator indexSchemaValidator;
  @Autowired private NoSqlHelper noSqlHelper;

  @Test
  public void testIndexCreation() throws IOException {
    for (IndexDescriptor indexDescriptor : indexDescriptors) {
      assertIndexAndAlias(indexDescriptor.getFullQualifiedName(), indexDescriptor.getAlias());
    }

    // assert schema creation won't be performed for the second time
    assertThat(indexSchemaValidator.schemaExists()).isTrue();
  }

  @Test // ZTL-1007
  public void testMigrationStepsRepositoryFields() throws IOException {
    final IndexDescriptor migrationStepsIndexDescriptor =
        getIndexDescriptorBy(MigrationRepositoryIndex.INDEX_NAME);
    assertThat(migrationStepsIndexDescriptor.getVersion()).isEqualTo("1.1.0");
    assertThat(getFieldDescriptions(migrationStepsIndexDescriptor).keySet())
        .containsExactlyInAnyOrder(
            ProcessorStep.VERSION,
            "@type",
            "description",
            ProcessorStep.APPLIED,
            ProcessorStep.APPLIED_DATE,
            ProcessorStep.CREATED_DATE,
            ProcessorStep.CONTENT,
            ProcessorStep.INDEX_NAME,
            ProcessorStep.ORDER);
  }

  @Test // ZTL-1010
  public void testDynamicMappingsOfIndices() throws Exception {
    final IndexDescriptor sessionIndex =
        indexDescriptors.stream()
            .filter(
                indexDescriptor ->
                    indexDescriptor.getIndexName().equals(TasklistWebSessionIndex.INDEX_NAME))
            .findFirst()
            .orElseThrow();
    assertThatIndexHasDynamicMappingOf(sessionIndex, "true");

    final List<IndexDescriptor> strictMappingIndices =
        indexDescriptors.stream()
            .filter(
                indexDescriptor ->
                    !indexDescriptor.getIndexName().equals(TasklistWebSessionIndex.INDEX_NAME))
            .collect(Collectors.toList());

    for (IndexDescriptor indexDescriptor : strictMappingIndices) {
      assertThatIndexHasDynamicMappingOf(indexDescriptor, "strict");
    }
  }

  private Map<String, Object> getFieldDescriptions(IndexDescriptor indexDescriptor)
      throws IOException {
    return noSqlHelper.getFieldDescription(indexDescriptor);
  }

  private IndexDescriptor getIndexDescriptorBy(String name) {
    return filter(indexDescriptors, indexDescriptor -> indexDescriptor.getIndexName().equals(name))
        .get(0);
  }

  private void assertIndexAndAlias(String indexName, String aliasName) throws IOException {
    assertThat(noSqlHelper.indexHasAlias(indexName, aliasName)).isTrue();
  }

  private void assertThatIndexHasDynamicMappingOf(
      final IndexDescriptor indexDescriptor, String dynamicMapping) throws IOException {
    assertThat(noSqlHelper.isIndexDynamicMapping(indexDescriptor, dynamicMapping)).isTrue();
  }
}
