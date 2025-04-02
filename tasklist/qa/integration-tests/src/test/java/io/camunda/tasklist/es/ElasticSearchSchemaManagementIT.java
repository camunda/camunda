/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.es;

import static io.camunda.webapps.schema.descriptors.ComponentNames.TASK_LIST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.schema.IndexMapping;
import io.camunda.tasklist.schema.IndexMapping.IndexMappingProperty;
import io.camunda.tasklist.schema.IndexSchemaValidator;
import io.camunda.tasklist.schema.manager.SchemaManager;
import io.camunda.tasklist.util.NoSqlHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.tasklist.TasklistIndexDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.test.annotation.DirtiesContext;

/**
 * ApplicationContext associated with this test gets dirty {@link
 * #replaceIndexDescriptorsInValidator(Set)} and should therefore be closed and removed from the
 * context cache.
 */
@DirtiesContext
@Conditional(ElasticSearchCondition.class)
public class ElasticSearchSchemaManagementIT extends TasklistZeebeIntegrationTest {

  private static final String ORIGINAL_SCHEMA_PATH =
      "/tasklist-test-elasticsearch-schema-manager.json";
  private static final String INDEX_NAME = "test";
  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private List<IndexDescriptor> indexDescriptors;
  @Autowired private RetryElasticsearchClient retryElasticsearchClient;
  @Autowired private IndexSchemaValidator indexSchemaValidator;
  @Autowired private NoSqlHelper noSqlHelper;
  @Autowired private SchemaManager schemaManager;

  private final String originalSchemaContent = readSchemaContent();

  private final IndexDescriptor testIndex = createIndexDescriptor();

  public ElasticSearchSchemaManagementIT() throws Exception {}

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isElasticSearch());
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
                  .getIndexSettingsFor(
                      indexDescriptor.getFullQualifiedName(),
                      RetryElasticsearchClient.NUMBERS_OF_REPLICA)
                  .get(RetryElasticsearchClient.NUMBERS_OF_REPLICA))
          .isEqualTo(String.valueOf(initialNumberOfReplicas));
    }

    tasklistProperties.getElasticsearch().setNumberOfReplicas(modifiedNumberOfReplicas);

    assertThat(indexSchemaValidator.isValidNumberOfReplicas()).isFalse();

    schemaManager.createSchema();

    for (final IndexDescriptor indexDescriptor : indexDescriptors) {
      assertThat(
              noSqlHelper.indexHasAlias(
                  indexDescriptor.getFullQualifiedName(), indexDescriptor.getAlias()))
          .isTrue();
      assertThat(
              retryElasticsearchClient
                  .getIndexSettingsFor(
                      indexDescriptor.getFullQualifiedName(),
                      RetryElasticsearchClient.NUMBERS_OF_REPLICA)
                  .get(RetryElasticsearchClient.NUMBERS_OF_REPLICA))
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
    replaceIndexDescriptorsInValidator(Collections.singleton(testIndex));
    schemaManager.createIndex(testIndex);

    // Update file with new field
    final var originalSchemaContent = readSchemaContent();

    updateSchemaContent(
        originalSchemaContent.replace(
            "\"properties\": {", "\"properties\": {\n    \"prop2\": { \"type\": \"keyword\" },"));

    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        indexSchemaValidator.validateIndexMappings();

    // when
    schemaManager.updateSchema(indexDiff);

    // then
    final String indexName = testIndex.getFullQualifiedName();
    final Map<String, IndexMapping> indexMappings = schemaManager.getIndexMappings(indexName);

    restoreOriginalSchemaContent();

    final IndexMapping expectedIndexMapping =
        new IndexMapping()
            .setIndexName(indexName)
            .setDynamic("strict")
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

  private IndexDescriptor createIndexDescriptor() {
    return new TasklistIndexDescriptor("", true) {
      @Override
      public String getFullQualifiedName() {
        return getFullIndexName();
      }

      @Override
      public String getAlias() {
        return getFullQualifiedName() + "alias";
      }

      @Override
      public String getMappingsClasspathFilename() {
        return ORIGINAL_SCHEMA_PATH;
      }

      @Override
      public String getAllVersionsIndexNameRegexPattern() {
        return getFullIndexName() + "*";
      }

      @Override
      public String getVersion() {
        return "1.0.0";
      }

      @Override
      public String getIndexPrefix() {
        return getIndexPrefixForTest();
      }

      @Override
      public String getIndexName() {
        return INDEX_NAME;
      }
    };
  }

  private String getFullIndexName() {
    return getIndexPrefixForTest() + TASK_LIST + "-" + INDEX_NAME;
  }

  @NotNull
  private String getIndexPrefixForTest() {
    return AbstractIndexDescriptor.formatIndexPrefix(schemaManager.getIndexPrefix());
  }

  private void updateSchemaContent(final String content) throws Exception {
    Files.write(
        Paths.get(getClass().getResource(ORIGINAL_SCHEMA_PATH).toURI()),
        content.getBytes(),
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  private String readSchemaContent() throws Exception {
    return new String(
        Files.readAllBytes(Paths.get(getClass().getResource(ORIGINAL_SCHEMA_PATH).toURI())));
  }

  private void restoreOriginalSchemaContent() throws Exception {
    Files.write(
        Paths.get(getClass().getResource(ORIGINAL_SCHEMA_PATH).toURI()),
        originalSchemaContent.getBytes(),
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  private void replaceIndexDescriptorsInValidator(final Set<IndexDescriptor> newIndexDescriptors)
      throws NoSuchFieldException, IllegalAccessException {
    final Field field = indexSchemaValidator.getClass().getDeclaredField("indexDescriptors");
    field.setAccessible(true);
    field.set(indexSchemaValidator, newIndexDescriptors);
  }
}
