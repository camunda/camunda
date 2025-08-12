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
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.MigrationProperties;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
public class SchemaStartupIT extends AbstractSchemaIT {

  @Autowired public SchemaManager schemaManager;

  @Autowired public TestSchemaStartup schemaStartup;
  @Autowired public TestIndex testIndex;

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
}
