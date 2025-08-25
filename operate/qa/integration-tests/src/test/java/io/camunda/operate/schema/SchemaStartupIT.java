/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.operate.JacksonConfig;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.IndexMapping.IndexMappingProperty;
import io.camunda.operate.schema.elasticsearch.ElasticsearchSchemaManager;
import io.camunda.operate.schema.indices.MigrationRepositoryIndex;
import io.camunda.operate.schema.migration.Migrator;
import io.camunda.operate.schema.migration.elasticsearch.ElasticsearchMigrationPlanFactory;
import io.camunda.operate.schema.migration.elasticsearch.ElasticsearchStepsRepository;
import io.camunda.operate.schema.opensearch.OpensearchMigrationPlanFactory;
import io.camunda.operate.schema.opensearch.OpensearchSchemaManager;
import io.camunda.operate.schema.opensearch.OpensearchStepsRepository;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.PostImporterQueueTemplate;
import io.camunda.operate.schema.util.TestIndex;
import io.camunda.operate.schema.util.TestSchemaStartup;
import io.camunda.operate.schema.util.TestTemplate;
import io.camunda.operate.schema.util.elasticsearch.ElasticsearchSchemaTestHelper;
import io.camunda.operate.schema.util.elasticsearch.TestElasticsearchConnector;
import io.camunda.operate.schema.util.opensearch.OpenSearchSchemaTestHelper;
import io.camunda.operate.schema.util.opensearch.TestOpenSearchConnector;
import io.camunda.operate.store.elasticsearch.ElasticsearchTaskStore;
import io.camunda.operate.store.opensearch.OpensearchTaskStore;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(
    classes = {
      IndexSchemaValidator.class,
      Migrator.class,
      ElasticsearchMigrationPlanFactory.class,
      ElasticsearchStepsRepository.class,
      ElasticsearchSchemaManager.class,
      ElasticsearchSchemaTestHelper.class,
      ElasticsearchTaskStore.class,
      TestElasticsearchConnector.class,
      OpensearchMigrationPlanFactory.class,
      OpensearchStepsRepository.class,
      OpensearchSchemaManager.class,
      OpenSearchSchemaTestHelper.class,
      OpensearchTaskStore.class,
      TestOpenSearchConnector.class,
      MigrationRepositoryIndex.class,
      ListViewTemplate.class,
      IncidentTemplate.class,
      PostImporterQueueTemplate.class,
      TestTemplate.class,
      TestIndex.class,
      MigrationProperties.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      TestSchemaStartup.class
    })
@SpringBootTest(properties = {"spring.profiles.active="})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class SchemaStartupIT extends AbstractSchemaIT {

  @Autowired public SchemaManager schemaManager;

  @Autowired public TestSchemaStartup schemaStartup;
  @Autowired public TestIndex testIndex;

  @Autowired private OperateProperties operateProperties;

  @Autowired private MigrationProperties migrationProperties;

  @Test
  public void shouldAddMissingFieldToExistingIndex() throws MigrationException {

    // given
    // a schema with an index missing a field
    schemaManager.createSchema();

    schemaManager.deleteIndicesFor(testIndex.getFullQualifiedName());
    schemaManager.createIndex(
        testIndex, "/schema/elasticsearch/create/index/operate-testindex-property-removed.json");

    // when
    schemaStartup.initializeSchemaOnDemand();

    // then
    // the index should have been updated
    final String indexName = testIndex.getFullQualifiedName();
    final Map<String, IndexMapping> indexMappings = schemaManager.getIndexMappings(indexName);

    assertThat(indexMappings)
        .containsExactly(
            entry(
                indexName,
                new IndexMapping()
                    .setIndexName(indexName)
                    .setDynamic("strict")
                    .setMetaProperties(Map.of())
                    .setProperties(
                        Set.of(
                            new IndexMappingProperty()
                                .setName("propB")
                                .setTypeDefinition(Map.of("type", "text")),
                            new IndexMappingProperty()
                                .setName("propA")
                                .setTypeDefinition(Map.of("type", "keyword")),
                            new IndexMappingProperty()
                                .setName("propC")
                                .setTypeDefinition(Map.of("type", "keyword"))))));
  }

  @Test
  public void shouldFailIfSchemaNotUpdateable() {

    // given
    // a schema with an index missing a field
    schemaManager.createSchema();

    schemaManager.deleteIndicesFor(testIndex.getFullQualifiedName());
    schemaManager.createIndex(
        testIndex, "/schema/elasticsearch/create/index/operate-testindex-nullvalue.json");

    // when / then
    assertThatThrownBy(() -> schemaStartup.initializeSchemaOnDemand())
        .isInstanceOf(OperateRuntimeException.class)
        .hasMessageContaining("Not supported index changes are introduced");
  }

  @Test
  public void shouldUpdateIndexSettingsWhenUpdateSchemaSettingsIsEnabled()
      throws MigrationException {
    // given
    final int initialReplicas = 0;
    final int updatedReplicas = 2;

    // Set initial replica count and enable schema settings update
    setNumberOfReplicas(initialReplicas);
    setUpdateSchemaSettings(true);
    migrationProperties.setMigrationEnabled(false);

    // Create initial schema
    schemaStartup.initializeSchemaOnDemand();

    // Verify initial replica settings
    final String indexName = testIndex.getFullQualifiedName();
    final Map<String, String> initialSettings =
        schemaManager.getIndexSettingsFor(indexName, SchemaManager.NUMBERS_OF_REPLICA);
    assertThat(initialSettings.get(SchemaManager.NUMBERS_OF_REPLICA))
        .isEqualTo(String.valueOf(initialReplicas));

    // when
    // Update replica count in properties and reinitialize schema
    setNumberOfReplicas(updatedReplicas);
    schemaStartup.initializeSchemaOnDemand();

    // then
    final Map<String, String> updatedSettings =
        schemaManager.getIndexSettingsFor(indexName, SchemaManager.NUMBERS_OF_REPLICA);
    assertThat(updatedSettings.get(SchemaManager.NUMBERS_OF_REPLICA))
        .isEqualTo(String.valueOf(updatedReplicas));
  }

  /**
   * @param useDefaultConfiguration when true, relies on default configuration where
   *     updateSchemaSettings is false; when false, explicitly sets updateSchemaSettings to false
   */
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldNotUpdateIndexSettingsWhenUpdateSchemaSettingsIsDisabled(
      final boolean useDefaultConfiguration) throws MigrationException {
    // given
    final int initialReplicas = 0;
    final int attemptedUpdatedReplicas = 2;

    // Set initial replica count and disable schema settings update
    setNumberOfReplicas(initialReplicas);
    if (!useDefaultConfiguration) {
      setUpdateSchemaSettings(false);
    }
    migrationProperties.setMigrationEnabled(false);

    // Create initial schema
    schemaStartup.initializeSchemaOnDemand();

    // Verify initial replica settings
    final String indexName = testIndex.getFullQualifiedName();
    final Map<String, String> initialSettings =
        schemaManager.getIndexSettingsFor(indexName, SchemaManager.NUMBERS_OF_REPLICA);
    assertThat(initialSettings.get(SchemaManager.NUMBERS_OF_REPLICA))
        .isEqualTo(String.valueOf(initialReplicas));

    // when
    // Update replica count in properties and reinitialize schema
    setNumberOfReplicas(attemptedUpdatedReplicas);
    schemaStartup.initializeSchemaOnDemand();

    // then
    // Settings should remain unchanged
    final Map<String, String> unchangedSettings =
        schemaManager.getIndexSettingsFor(indexName, SchemaManager.NUMBERS_OF_REPLICA);
    assertThat(unchangedSettings.get(SchemaManager.NUMBERS_OF_REPLICA))
        .isEqualTo(String.valueOf(initialReplicas));
  }

  private void setNumberOfReplicas(final int numberOfReplicas) {
    if (DatabaseInfo.isElasticsearch()) {
      operateProperties.getElasticsearch().setNumberOfReplicas(numberOfReplicas);
    } else {
      operateProperties.getOpensearch().setNumberOfReplicas(numberOfReplicas);
    }
  }

  private void setUpdateSchemaSettings(final boolean updateSchemaSettings) {
    if (DatabaseInfo.isElasticsearch()) {
      operateProperties.getElasticsearch().setUpdateSchemaSettings(updateSchemaSettings);
    } else {
      operateProperties.getOpensearch().setUpdateSchemaSettings(updateSchemaSettings);
    }
  }
}
