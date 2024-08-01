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
import io.camunda.operate.connect.ElasticsearchClientProvider;
import io.camunda.operate.connect.OpensearchClientProvider;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.IndexMapping.IndexMappingProperty;
import io.camunda.operate.schema.elasticsearch.ElasticsearchSchemaManager;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.opensearch.OpensearchSchemaManager;
import io.camunda.operate.schema.util.SchemaTestHelper;
import io.camunda.operate.schema.util.TestDynamicIndex;
import io.camunda.operate.schema.util.TestIndex;
import io.camunda.operate.schema.util.TestTemplate;
import io.camunda.operate.schema.util.elasticsearch.ElasticsearchSchemaTestHelper;
import io.camunda.operate.schema.util.opensearch.OpenSearchSchemaTestHelper;
import io.camunda.operate.store.elasticsearch.ElasticsearchTaskStore;
import io.camunda.operate.store.opensearch.OpensearchTaskStore;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(
    classes = {
      IndexSchemaValidator.class,
      TestIndex.class,
      TestTemplate.class,
      TestDynamicIndex.class,
      ElasticsearchSchemaManager.class,
      ElasticsearchClientProvider.class,
      ElasticsearchTaskStore.class,
      ElasticsearchSchemaTestHelper.class,
      OpensearchSchemaManager.class,
      OpensearchClientProvider.class,
      OpensearchTaskStore.class,
      OpenSearchSchemaTestHelper.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class
    })
@SpringBootTest(properties = {"spring.profiles.active="})
public class IndexSchemaValidatorIT extends AbstractSchemaIT {

  @Autowired public IndexSchemaValidator validator;

  @Autowired public SchemaManager schemaManager;
  @Autowired public SchemaTestHelper schemaHelper;

  @Autowired public TestIndex testIndex;
  @Autowired public TestDynamicIndex testDynamicIndex;
  @Autowired public TestTemplate testTemplate;

  @BeforeEach
  public void createDefault() {
    schemaManager.createDefaults();
  }

  @AfterEach
  public void dropSchema() {
    schemaHelper.dropSchema();
  }

  @Test
  public void shouldValidateDynamicIndexWithAddedProperty() {
    // Create a dynamic index and insert data
    schemaManager.createIndex(
        testDynamicIndex,
        "/schema/elasticsearch/create/index/operate-testdynamicindex-property-removed.json");
    final Map<String, Object> subDocument =
        Map.of(
            "requestedUrl",
            "test",
            "SPRING_SECURITY_CONTEXT",
            "test",
            "SPRING_SECURITY_SAVED_REQUEST",
            "test");
    final Map<String, Object> document = Map.of("propA", "test", "propC", subDocument);
    clientTestHelper.createDocument(testDynamicIndex.getFullQualifiedName(), "1", document);

    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // Only property B shows up in the diff, and no exception is thrown due to the data adding
    // fields dynamically in propC
    assertThat(indexDiff)
        .containsExactly(
            entry(
                testDynamicIndex,
                Set.of(
                    new IndexMappingProperty()
                        .setName("propB")
                        .setTypeDefinition(Map.of("type", "text")))));
  }

  @Test
  public void shouldValidateDynamicIndexWithDataAddingFields() {
    // Create a dynamic index and insert data
    schemaManager.createIndex(
        testDynamicIndex, "/schema/elasticsearch/create/index/operate-testdynamicindex.json");
    final Map<String, Object> subDocument =
        Map.of(
            "requestedUrl",
            "test",
            "SPRING_SECURITY_CONTEXT",
            "test",
            "SPRING_SECURITY_SAVED_REQUEST",
            "test");
    final Map<String, Object> document =
        Map.of("propA", "test", "propB", "test", "propC", subDocument);
    clientTestHelper.createDocument(testDynamicIndex.getFullQualifiedName(), "1", document);

    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // No diff should show and no exception thrown due to fields dynamically added from propC data
    assertThat(indexDiff).isEmpty();
  }

  @Test
  public void shouldIgnoreMissingIndexes() {
    // given an empty schema

    // when
    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // then
    // no exception was thrown and:
    assertThat(indexDiff).isEmpty();
  }

  @Test
  public void shouldValidateAnUpToDateSchema() {
    // given
    schemaManager.createSchema();

    // when
    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // then
    assertThat(indexDiff).isEmpty();
  }

  @Test
  public void shouldDetectAnAddedIndexProperty() {
    // given
    // a schema that has a missing field
    schemaManager.createIndex(
        testIndex, "/schema/elasticsearch/create/index/operate-testindex-property-removed.json");

    // when
    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // then
    assertThat(indexDiff)
        .containsExactly(
            entry(
                testIndex,
                Set.of(
                    new IndexMappingProperty()
                        .setName("propC")
                        .setTypeDefinition(Map.of("type", "keyword")))));
  }

  @Test
  public void shouldDetectAnAddedIndexPropertyOnTwoIndicesWithMissingField() {
    // given
    // a schema with two indices that has a missing field
    schemaManager.createIndex(
        testIndex, "/schema/elasticsearch/create/index/operate-testindex-property-removed.json");
    schemaHelper.createIndex(
        testIndex,
        testIndex.getFullQualifiedName() + "2024-01-01",
        "/schema/elasticsearch/create/index/operate-testindex-property-removed.json");

    // when
    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // then
    assertThat(indexDiff)
        .containsExactly(
            entry(
                testIndex,
                Set.of(
                    new IndexMappingProperty()
                        .setName("propC")
                        .setTypeDefinition(Map.of("type", "keyword")))));
  }

  @Test
  public void shouldDetectAnAddedIndexPropertyOnTwoIndicesOnlyOneMissingField() {
    // given
    // a schema with two indices that has a missing field
    schemaManager.createIndex(
        testIndex, "/schema/elasticsearch/create/index/operate-testindex-property-removed.json");
    schemaHelper.createIndex(
        testIndex,
        testIndex.getFullQualifiedName() + "2024-01-01",
        "/schema/elasticsearch/create/index/operate-testindex.json");

    // when
    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // then
    assertThat(indexDiff)
        .containsExactly(
            entry(
                testIndex,
                Set.of(
                    new IndexMappingProperty()
                        .setName("propC")
                        .setTypeDefinition(Map.of("type", "keyword")))));
  }

  @Test
  public void shouldDetectAmbiguousIndexDifference() {
    // given
    // a schema with two indices that has a missing field
    schemaManager.createIndex(
        testIndex, "/schema/elasticsearch/create/index/operate-testindex-property-removed.json");
    schemaHelper.createIndex(
        testIndex,
        testIndex.getFullQualifiedName() + "2024-01-01",
        "/schema/elasticsearch/create/index/operate-testindex-another-property-removed.json");

    // when/then
    assertThatThrownBy(() -> validator.validateIndexMappings())
        .isInstanceOf(OperateRuntimeException.class)
        .hasMessageContaining(
            "Ambiguous schema update. First bring runtime and date indices to one schema.");
  }

  @Test
  public void shouldIgnoreARemovedIndexProperty() {
    // given
    // a schema that has a added field
    schemaManager.createIndex(
        testIndex, "/schema/elasticsearch/create/index/operate-testindex-property-added.json");

    // when
    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // then
    assertThat(indexDiff).isEmpty();
  }

  @Test
  public void shouldDetectChangedIndexMappingParameters() {
    // given
    // a schema that has a added field
    schemaManager.createIndex(
        testIndex, "/schema/elasticsearch/create/index/operate-testindex-nullvalue.json");

    // when/then
    assertThatThrownBy(() -> validator.validateIndexMappings())
        .isInstanceOf(OperateRuntimeException.class)
        .hasMessageContaining(
            "Not supported index changes are introduced. Data migration is required.");
  }

  @Test
  public void shouldDetectAnAddedTemplateProperty() {
    // given
    // a schema that has a missing field
    schemaManager.createTemplate(
        testTemplate,
        "/schema/elasticsearch/create/template/operate-testtemplate-property-removed.json");

    // when
    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // then
    assertThat(indexDiff)
        .containsExactly(
            entry(
                testTemplate,
                Set.of(
                    new IndexMappingProperty()
                        .setName("propC")
                        .setTypeDefinition(Map.of("type", "keyword")))));
  }

  @Test
  public void shouldIgnoreARemovedTemplateProperty() {
    // given
    // a schema that has an added field
    schemaManager.createTemplate(
        testTemplate,
        "/schema/elasticsearch/create/template/operate-testtemplate-property-added.json");

    // when
    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // then
    assertThat(indexDiff).isEmpty();
  }
}
