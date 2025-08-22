/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.es;

import static io.camunda.tasklist.es.RetryElasticsearchClient.NUMBERS_OF_REPLICA;
import static io.camunda.tasklist.es.RetryElasticsearchClient.NUMBERS_OF_SHARDS;
import static io.camunda.tasklist.util.apps.schema.TestIndexDescriptorConfiguration.getSchemaFilePath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.schema.IndexMapping;
import io.camunda.tasklist.schema.IndexMapping.IndexMappingProperty;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import io.camunda.tasklist.schema.manager.ElasticsearchSchemaManager;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.schema.templates.TemplateDescriptor;
import io.camunda.tasklist.util.DatabaseTestExtension;
import io.camunda.tasklist.util.ElasticsearchHelper;
import io.camunda.tasklist.util.ElasticsearchTestExtension;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.TestIndexDescriptor;
import io.camunda.tasklist.util.TestTemplateDescriptor;
import io.camunda.tasklist.util.apps.schema.TestIndexDescriptorConfiguration;
import io.camunda.tasklist.util.apps.schema.TestTemplateDescriptorConfiguration;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Import({TestIndexDescriptorConfiguration.class, TestTemplateDescriptorConfiguration.class})
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
public class ElasticSearchSchemaManagementIT extends TasklistIntegrationTest {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticSearchSchemaManagementIT.class);

  @RegisterExtension @Autowired public DatabaseTestExtension databaseTestExtension;
  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private List<IndexDescriptor> indexDescriptors;
  @Autowired private List<TemplateDescriptor> templateDescriptors;
  @Autowired private RetryElasticsearchClient retryElasticsearchClient;
  @Autowired private IndexSchemaValidatorElasticSearch indexSchemaValidator;
  @Autowired private ElasticsearchHelper noSqlHelper;
  @Autowired private ElasticsearchSchemaManager schemaManager;
  @Autowired private TestIndexDescriptor testIndexDescriptor;
  @Autowired private TestTemplateDescriptor testTemplateDescriptor;

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isElasticSearch());
  }

  @DynamicPropertySource
  static void setProperties(final DynamicPropertyRegistry registry) {
    // Disable schema creation for the test, as we want to manage it manually
    registry.add("camunda.tasklist.elasticsearch.createSchema", () -> false);
  }

  @Test
  public void shouldChangeNumberOfReplicas() throws IOException {
    final Integer initialNumberOfReplicas = 0;
    final Integer modifiedNumberOfReplicas = 1;

    tasklistProperties.getElasticsearch().setNumberOfReplicas(initialNumberOfReplicas);
    schemaManager.createSchema();

    for (final IndexDescriptor indexDescriptor : indexDescriptors) {
      assertThat(
              noSqlHelper.indexHasAlias(
                  indexDescriptor.getFullQualifiedName(), indexDescriptor.getAlias()))
          .isTrue();
      assertThat(
              retryElasticsearchClient
                  .getIndexSettingsFor(indexDescriptor.getFullQualifiedName(), NUMBERS_OF_REPLICA)
                  .get(NUMBERS_OF_REPLICA))
          .isEqualTo(String.valueOf(initialNumberOfReplicas));
    }

    tasklistProperties.getElasticsearch().setNumberOfReplicas(modifiedNumberOfReplicas);

    schemaManager.updateIndexSettings();

    for (final IndexDescriptor indexDescriptor : indexDescriptors) {
      assertThat(
              noSqlHelper.indexHasAlias(
                  indexDescriptor.getFullQualifiedName(), indexDescriptor.getAlias()))
          .isTrue();
      assertThat(
              retryElasticsearchClient
                  .getIndexSettingsFor(indexDescriptor.getFullQualifiedName(), NUMBERS_OF_REPLICA)
                  .get(NUMBERS_OF_REPLICA))
          .isEqualTo(String.valueOf(modifiedNumberOfReplicas));
    }
  }

  @Test
  public void shouldGetIndexMappings() throws IOException {
    schemaManager.createSchema();

    for (final IndexDescriptor indexDescriptor : indexDescriptors) {
      final Map<String, IndexMapping> indexMappings =
          schemaManager.getIndexMappings(indexDescriptor.getAlias());
      assertThat(indexMappings).isNotEmpty();
    }
  }

  @Test
  public void shouldGetExpectedIndexFields() throws IOException {
    for (final IndexDescriptor indexDescriptor : indexDescriptors) {
      final IndexMapping indexMapping = schemaManager.getExpectedIndexFields(indexDescriptor);
      assertThat(indexMapping).isNotNull();
      assertThat(indexMapping.getIndexName()).isEqualTo(indexDescriptor.getIndexName());
      assertThat(indexMapping.getProperties()).isNotEmpty();
    }
  }

  @Test
  public void shouldAddFieldToIndex() throws Exception {
    // given
    schemaManager.createSchema();
    testIndexDescriptor.setSchemaClasspathFilename(getSchemaFilePath("tasklist-test-after.json"));
    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        indexSchemaValidator.validateIndexMappings();

    // when
    schemaManager.updateSchema(indexDiff);

    // then
    final String indexName = testIndexDescriptor.getFullQualifiedName();
    final Map<String, IndexMapping> indexMappings = schemaManager.getIndexMappings(indexName);

    final IndexMapping expectedIndexMapping =
        new IndexMapping()
            .setIndexName(indexName)
            .setDynamic("strict")
            .setMetaProperties(Collections.emptyMap())
            .setProperties(
                Set.of(
                    new IndexMappingProperty()
                        .setName("prop2")
                        .setTypeDefinition(Map.of("type", "keyword")),
                    new IndexMappingProperty()
                        .setName("prop1")
                        .setTypeDefinition(Map.of("type", "keyword")),
                    new IndexMappingProperty()
                        .setName("prop0")
                        .setTypeDefinition(Map.of("type", "keyword"))));

    assertThat(indexMappings).contains(entry(indexName, expectedIndexMapping));
  }

  @Test
  public void shouldAddFieldToIndexTemplate() throws Exception {
    // given
    schemaManager.createSchema();
    testTemplateDescriptor.setSchemaClasspathFilename(
        TestTemplateDescriptorConfiguration.getSchemaFilePath("tasklist-test-template-after.json"));
    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        indexSchemaValidator.validateIndexMappings();

    // when
    schemaManager.updateSchema(indexDiff);

    final String indexName = testTemplateDescriptor.getFullQualifiedName();
    final Map<String, IndexMapping> indexMappings = schemaManager.getIndexMappings(indexName);

    // then
    assertThat(indexMappings)
        .containsExactly(
            entry(
                indexName,
                new IndexMapping()
                    .setIndexName(indexName)
                    .setDynamic("strict")
                    .setMetaProperties(Collections.emptyMap())
                    .setProperties(
                        Set.of(
                            new IndexMappingProperty()
                                .setName("prop0")
                                .setTypeDefinition(Map.of("type", "keyword")),
                            new IndexMappingProperty()
                                .setName("prop1")
                                .setTypeDefinition(Map.of("type", "keyword")),
                            new IndexMappingProperty()
                                .setName("prop2")
                                .setTypeDefinition(Map.of("type", "keyword"))))));

    final var indexTemplateMapping =
        ((ElasticsearchTestExtension) databaseTestExtension)
            .getIndexTemplateMapping(testTemplateDescriptor.getTemplateName());
    assertThat(indexTemplateMapping)
        .isEqualTo(
            new IndexMapping()
                .setDynamic("strict")
                .setProperties(
                    Set.of(
                        new IndexMappingProperty()
                            .setName("prop0")
                            .setTypeDefinition(Map.of("type", "keyword")),
                        new IndexMappingProperty()
                            .setName("prop1")
                            .setTypeDefinition(Map.of("type", "keyword")),
                        new IndexMappingProperty()
                            .setName("prop2")
                            .setTypeDefinition(Map.of("type", "keyword")))));
  }

  @Test
  public void shouldChangeNumberOfReplicasForComponentTemplate() {
    final int initialNumberOfReplicas = 1;
    final int modifiedNumberOfReplicas = 2;

    tasklistProperties.getElasticsearch().setNumberOfReplicas(initialNumberOfReplicas);
    schemaManager.createSchema();

    // Verify initial component template replica settings
    final String componentTemplateName = schemaManager.getComponentTemplateName();
    final var initialSettings =
        retryElasticsearchClient.getComponentTemplateSettings(componentTemplateName);
    assertThat(initialSettings.get(NUMBERS_OF_REPLICA))
        .isEqualTo(String.valueOf(initialNumberOfReplicas));

    tasklistProperties.getElasticsearch().setNumberOfReplicas(modifiedNumberOfReplicas);

    schemaManager.updateIndexSettings();

    // Verify updated component template replica settings
    final var updatedSettings =
        retryElasticsearchClient.getComponentTemplateSettings(componentTemplateName);
    assertThat(updatedSettings.get(NUMBERS_OF_REPLICA))
        .isEqualTo(String.valueOf(modifiedNumberOfReplicas));
  }

  @Test
  public void shouldChangeNumberOfShardsForComponentTemplate() {
    final int initialNumberOfShards = 1;
    final int modifiedNumberOfShards = 3;

    tasklistProperties.getElasticsearch().setNumberOfShards(initialNumberOfShards);
    schemaManager.createSchema();

    // Verify initial component template shard settings
    final String componentTemplateName = schemaManager.getComponentTemplateName();
    final var initialSettings =
        retryElasticsearchClient.getComponentTemplateSettings(componentTemplateName);
    assertThat(initialSettings.get(NUMBERS_OF_SHARDS))
        .isEqualTo(String.valueOf(initialNumberOfShards));

    tasklistProperties.getElasticsearch().setNumberOfShards(modifiedNumberOfShards);

    schemaManager.updateIndexSettings();

    // Verify updated component template shard settings
    final var updatedSettings =
        retryElasticsearchClient.getComponentTemplateSettings(componentTemplateName);
    assertThat(updatedSettings.get(NUMBERS_OF_SHARDS))
        .isEqualTo(String.valueOf(modifiedNumberOfShards));
  }

  @Test
  public void shouldUpdateReplicasForAllTaskIndicesCreatedFromTemplate() throws IOException {
    final Integer initialNumberOfReplicas = 0;
    final Integer modifiedNumberOfReplicas = 2;

    tasklistProperties.getElasticsearch().setNumberOfReplicas(initialNumberOfReplicas);
    schemaManager.createSchema();

    // Find the TaskTemplate from the template descriptors
    final TemplateDescriptor taskTemplate =
        templateDescriptors.stream()
            .filter(template -> "task".equals(template.getIndexName()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("TaskTemplate not found"));

    // Create multiple task indices from the template by indexing TaskEntity objects
    final String taskIndex1 = taskTemplate.getFullQualifiedName() + "-2025-01-01";
    final String taskIndex2 = taskTemplate.getFullQualifiedName() + "-2025-01-02";
    final String taskIndex3 = taskTemplate.getFullQualifiedName() + "-2025-01-03";
    final TaskEntity task1 = createSampleTaskEntity(1L);
    final TaskEntity task2 = createSampleTaskEntity(2L);
    final TaskEntity task3 = createSampleTaskEntity(3L);

    final ObjectMapper mapper = new ObjectMapper();

    retryElasticsearchClient.createOrUpdateDocument(
        taskIndex1, task1.getId(), mapper.convertValue(task1, Map.class));
    retryElasticsearchClient.createOrUpdateDocument(
        taskIndex2, task2.getId(), mapper.convertValue(task2, Map.class));
    retryElasticsearchClient.createOrUpdateDocument(
        taskIndex3, task3.getId(), mapper.convertValue(task3, Map.class));

    // Verify all task indices have the initial replica settings
    validateTaskIndicesReplicaSettings(taskTemplate.getAlias(), initialNumberOfReplicas);

    // Update replica settings
    tasklistProperties.getElasticsearch().setNumberOfReplicas(modifiedNumberOfReplicas);
    schemaManager.updateIndexSettings();

    // Verify all task indices (created from template) have updated replica settings
    validateTaskIndicesReplicaSettings(taskTemplate.getAlias(), modifiedNumberOfReplicas);
  }

  @Test
  public void shouldSetShardsAndReplicasInIndexTemplateFromPerIndexConfiguration() {
    final int numberOfReplicas = 2;
    final int numberOfShards = 3;

    tasklistProperties
        .getElasticsearch()
        .setNumberOfReplicasPerIndices(Map.of(TaskTemplate.INDEX_NAME, numberOfReplicas));
    tasklistProperties
        .getElasticsearch()
        .setNumberOfShardsPerIndex(Map.of(TaskTemplate.INDEX_NAME, numberOfShards));

    schemaManager.createSchema();

    // Find the TaskTemplate from the template descriptors
    final TemplateDescriptor taskTemplate =
        templateDescriptors.stream()
            .filter(template -> TaskTemplate.INDEX_NAME.equals(template.getIndexName()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("TaskTemplate not found"));

    // Verify that the index template has the correct shard and replica settings
    final var indexTemplate =
        retryElasticsearchClient.getIndexTemplate(taskTemplate.getTemplateName());
    final var templateSettings = indexTemplate.template().settings();

    assertThat(templateSettings.get(NUMBERS_OF_SHARDS))
        .as("Task template should have %d shards", numberOfShards)
        .isEqualTo(String.valueOf(numberOfShards));

    assertThat(templateSettings.get(NUMBERS_OF_REPLICA))
        .as("Task template should have %d replicas", numberOfReplicas)
        .isEqualTo(String.valueOf(numberOfReplicas));
  }

  @Test
  public void shouldUpdateShardsAndReplicasInIndexTemplateFromPerIndexConfiguration() {
    schemaManager.createSchema();

    // Find the TaskTemplate from the template descriptors
    final TemplateDescriptor taskTemplate =
        templateDescriptors.stream()
            .filter(template -> TaskTemplate.INDEX_NAME.equals(template.getIndexName()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("TaskTemplate not found"));

    // Verify initial template settings before update
    final var initialIndexTemplate =
        retryElasticsearchClient.getIndexTemplate(taskTemplate.getTemplateName());
    final var initialTemplateSettings = initialIndexTemplate.template().settings();

    // Assert initial settings are using default values (1 shard, 0 replicas by default)
    assertThat(initialTemplateSettings.get(NUMBERS_OF_SHARDS))
        .as("Task template should initially have default number of shards")
        .isEqualTo("1");

    assertThat(initialTemplateSettings.get(NUMBERS_OF_REPLICA))
        .as("Task template should initially have default number of replicas")
        .isEqualTo("0");

    final int updatedNumberOfReplicas = 2;
    final int updateNumberOfShards = 3;

    tasklistProperties
        .getElasticsearch()
        .setNumberOfReplicasPerIndices(Map.of(TaskTemplate.INDEX_NAME, updatedNumberOfReplicas));
    tasklistProperties
        .getElasticsearch()
        .setNumberOfShardsPerIndex(Map.of(TaskTemplate.INDEX_NAME, updateNumberOfShards));

    schemaManager.updateIndexSettings();

    // Verify that the index template has been updated with the correct shard and replica settings
    final var indexTemplate =
        retryElasticsearchClient.getIndexTemplate(taskTemplate.getTemplateName());
    final var templateSettings = indexTemplate.template().settings();

    assertThat(templateSettings.get(NUMBERS_OF_SHARDS))
        .as("Task template should have updated shards to %d", updateNumberOfShards)
        .isEqualTo(String.valueOf(updateNumberOfShards));

    assertThat(templateSettings.get(NUMBERS_OF_REPLICA))
        .as("Task template should have updated replicas to %d", updatedNumberOfReplicas)
        .isEqualTo(String.valueOf(updatedNumberOfReplicas));
  }

  private TaskEntity createSampleTaskEntity(final long taskKey) {
    return new TaskEntity().setId(String.valueOf(taskKey)).setKey(taskKey);
  }

  private void validateTaskIndicesReplicaSettings(
      final String taskAlias, final Integer expectedReplicas) throws IOException {
    final Set<String> taskIndices = retryElasticsearchClient.getIndexNames(taskAlias);
    assertThat(taskIndices).isNotEmpty();

    for (final String indexName : taskIndices) {
      assertThat(
              retryElasticsearchClient
                  .getIndexSettingsFor(indexName, NUMBERS_OF_REPLICA)
                  .get(NUMBERS_OF_REPLICA))
          .as("Task index %s should have %d replicas", indexName, expectedReplicas)
          .isEqualTo(String.valueOf(expectedReplicas));
    }

    LOGGER.info(
        "Validated replica settings for {} task indices under alias {}: expected={}, indices={}",
        taskIndices.size(),
        taskAlias,
        expectedReplicas,
        taskIndices);
  }
}
