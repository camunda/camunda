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

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.schema.IndexMapping;
import io.camunda.tasklist.schema.IndexMapping.IndexMappingProperty;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import io.camunda.tasklist.schema.manager.SchemaManager;
import io.camunda.tasklist.schema.templates.TemplateDescriptor;
import io.camunda.tasklist.util.ElasticsearchHelper;
import io.camunda.tasklist.util.ElasticsearchTestExtension;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.util.TestApplication;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {
      TestApplication.class,
      TestIndexDescriptorConfiguration.class,
      TestTemplateDescriptorConfiguration.class
    },
    properties = {
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
      TasklistProperties.PREFIX + "importer.jobType = testJobType",
      "camunda.webapps.enabled = true",
      "camunda.webapps.default-app = tasklist",
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ElasticSearchSchemaManagementIT extends TasklistZeebeIntegrationTest {

  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private List<IndexDescriptor> indexDescriptors;
  @Autowired private List<TemplateDescriptor> templateDescriptors;
  @Autowired private RetryElasticsearchClient retryElasticsearchClient;
  @Autowired private IndexSchemaValidatorElasticSearch indexSchemaValidator;
  @Autowired private ElasticsearchHelper noSqlHelper;
  @Autowired private SchemaManager schemaManager;
  @Autowired private TestIndexDescriptor testIndexDescriptor;
  @Autowired private TestTemplateDescriptor testTemplateDescriptor;

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
                  .getIndexSettingsFor(indexDescriptor.getFullQualifiedName(), NUMBERS_OF_REPLICA)
                  .get(NUMBERS_OF_REPLICA))
          .isEqualTo(String.valueOf(initialNumberOfReplicas));
    }

    tasklistProperties.getElasticsearch().setNumberOfReplicas(modifiedNumberOfReplicas);

    assertThat(indexSchemaValidator.validateIndexConfiguration()).isFalse();

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
        retryElasticsearchClient.getComponentTemplateProperties(
            componentTemplateName, NUMBERS_OF_REPLICA);
    assertThat(initialSettings.get(NUMBERS_OF_REPLICA))
        .isEqualTo(String.valueOf(initialNumberOfReplicas));

    tasklistProperties.getElasticsearch().setNumberOfReplicas(modifiedNumberOfReplicas);

    assertThat(indexSchemaValidator.validateIndexConfiguration()).isFalse();

    schemaManager.createSchema();

    // Verify modified component template replica settings
    final var modifiedSettings =
        retryElasticsearchClient.getComponentTemplateProperties(
            componentTemplateName, NUMBERS_OF_REPLICA);
    assertThat(modifiedSettings.get(NUMBERS_OF_REPLICA))
        .isEqualTo(String.valueOf(modifiedNumberOfReplicas));
  }

  @Test
  public void shouldChangeNumberOfShardsForComponentTemplate() {
    final int initialNumberOfShards = 2;
    final int modifiedNumberOfShards = 3;

    tasklistProperties.getElasticsearch().setNumberOfShards(initialNumberOfShards);
    schemaManager.createSchema();

    // Verify initial component template shard settings
    final String componentTemplateName = schemaManager.getComponentTemplateName();
    final var initialSettings =
        retryElasticsearchClient.getComponentTemplateProperties(
            componentTemplateName, NUMBERS_OF_SHARDS);
    assertThat(initialSettings.get(NUMBERS_OF_SHARDS))
        .isEqualTo(String.valueOf(initialNumberOfShards));

    tasklistProperties.getElasticsearch().setNumberOfShards(modifiedNumberOfShards);

    assertThat(indexSchemaValidator.validateIndexConfiguration()).isFalse();

    schemaManager.createSchema();

    // Verify modified component template shard settings
    final Map<String, String> modifiedSettings =
        retryElasticsearchClient.getComponentTemplateProperties(
            componentTemplateName, NUMBERS_OF_SHARDS);
    assertThat(modifiedSettings.get(NUMBERS_OF_SHARDS))
        .isEqualTo(String.valueOf(modifiedNumberOfShards));
  }
}
