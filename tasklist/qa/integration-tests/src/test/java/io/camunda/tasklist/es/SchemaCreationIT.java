/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.es;

import static io.camunda.tasklist.es.RetryElasticsearchClient.NUMBERS_OF_REPLICA;
import static io.camunda.tasklist.util.CollectionUtil.filter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestSchemaStartup;
import io.camunda.tasklist.qa.util.TestUtil;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class SchemaCreationIT extends TasklistIntegrationTest {

  @RegisterExtension @Autowired public DatabaseTestExtension databaseTestExtension;

  @Autowired private List<IndexDescriptor> indexDescriptors;
  @Autowired private IndexSchemaValidator indexSchemaValidator;
  @Autowired private NoSqlHelper noSqlHelper;
  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private RetryElasticsearchClient retryElasticsearchClient;
  @Autowired private TestSchemaStartup schemaStartup;

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isElasticSearch());
  }

  @Test
  public void testIndexCreation() throws IOException {
    for (final IndexDescriptor indexDescriptor : indexDescriptors) {
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

    for (final IndexDescriptor indexDescriptor : strictMappingIndices) {
      assertThatIndexHasDynamicMappingOf(indexDescriptor, "strict");
    }
  }

  @Test
  public void testReplicasUpdatedWhenUpdateSchemaSettingsIsTrue() throws Exception {
    // given
    tasklistProperties.getElasticsearch().setUpdateSchemaSettings(true);
    // Set a specific number of replicas in configuration
    final int configuredReplicas = 2;
    tasklistProperties.getElasticsearch().setNumberOfReplicas(configuredReplicas);

    // when
    // Schema startup should update the settings (trigger schema creation/update)
    schemaStartup.initializeSchemaOnDemand();

    // then
    // Assert that number of replicas is updated with configuration
    assertThat(indexDescriptors).isNotEmpty();
    for (final IndexDescriptor indexDescriptor : indexDescriptors) {
      final String replicasValue =
          retryElasticsearchClient
              .getIndexSettingsFor(indexDescriptor.getFullQualifiedName(), NUMBERS_OF_REPLICA)
              .get(NUMBERS_OF_REPLICA);
      assertThat(replicasValue).isEqualTo(String.valueOf(configuredReplicas));
    }
  }

  @Test
  public void testReplicasNotUpdatedWhenUpdateSchemaSettingsIsFalse() throws Exception {
    // given
    tasklistProperties.getElasticsearch().setUpdateSchemaSettings(false);
    // Set a specific number of replicas in configuration
    final int configuredReplicas = 3;
    tasklistProperties.getElasticsearch().setNumberOfReplicas(configuredReplicas);

    // when
    // Schema startup should update the settings (trigger schema creation/update)
    schemaStartup.initializeSchemaOnDemand();

    // then
    // Assert that number of replicas is updated with configuration
    assertThat(indexDescriptors).isNotEmpty();
    for (final IndexDescriptor indexDescriptor : indexDescriptors) {
      final String replicasValue =
          retryElasticsearchClient
              .getIndexSettingsFor(indexDescriptor.getFullQualifiedName(), NUMBERS_OF_REPLICA)
              .get(NUMBERS_OF_REPLICA);
      assertThat(replicasValue).isEqualTo("0");
    }
  }

  private Map<String, Object> getFieldDescriptions(final IndexDescriptor indexDescriptor)
      throws IOException {
    return noSqlHelper.getFieldDescription(indexDescriptor);
  }

  private IndexDescriptor getIndexDescriptorBy(final String name) {
    return filter(indexDescriptors, indexDescriptor -> indexDescriptor.getIndexName().equals(name))
        .get(0);
  }

  private void assertIndexAndAlias(final String indexName, final String aliasName)
      throws IOException {
    assertThat(noSqlHelper.indexHasAlias(indexName, aliasName)).isTrue();
  }

  private void assertThatIndexHasDynamicMappingOf(
      final IndexDescriptor indexDescriptor, final String dynamicMapping) throws IOException {
    assertThat(noSqlHelper.isIndexDynamicMapping(indexDescriptor, dynamicMapping)).isTrue();
  }
}
