/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.os;

import static io.camunda.webapps.schema.descriptors.ComponentNames.TASK_LIST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opensearch.client.opensearch._types.mapping.Property.Kind.Keyword;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.schema.IndexMapping;
import io.camunda.tasklist.schema.IndexMapping.IndexMappingProperty;
import io.camunda.tasklist.schema.IndexSchemaValidator;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import io.camunda.tasklist.schema.manager.OpenSearchSchemaManager;
import io.camunda.tasklist.util.NoSqlHelper;
import io.camunda.tasklist.util.OpenSearchTestExtension;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.util.TestApplication;
import io.camunda.tasklist.util.TestIndexDescriptor;
import io.camunda.tasklist.util.TestTemplateDescriptor;
import io.camunda.tasklist.util.apps.schema.TestIndexDescriptorConfiguration;
import io.camunda.tasklist.util.apps.schema.TestTemplateDescriptorConfiguration;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
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
public class OpenSearchSchemaManagementIT extends TasklistZeebeIntegrationTest {

  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private List<IndexDescriptor> indexDescriptors;
  @Autowired private RetryOpenSearchClient retryOpenSearchClient;
  @Autowired private IndexSchemaValidator indexSchemaValidator;
  @Autowired private NoSqlHelper noSqlHelper;
  @Autowired private OpenSearchSchemaManager schemaManager;
  @Autowired private TestIndexDescriptor testIndexDescriptor;
  @Autowired private TestTemplateDescriptor testTemplateDescriptor;

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isOpenSearch());
  }

  @Test
  public void shouldChangeNumberOfReplicas() throws IOException {
    final Integer initialNumberOfReplicas = 0;
    final Integer modifiedNumberOfReplicas = 1;

    tasklistProperties.getOpenSearch().setNumberOfReplicas(initialNumberOfReplicas);
    schemaManager.createSchema();

    for (final IndexDescriptor indexDescriptor : indexDescriptors) {
      assertThat(
              noSqlHelper.indexHasAlias(
                  indexDescriptor.getFullQualifiedName(), indexDescriptor.getAlias()))
          .isTrue();
      assertThat(
              retryOpenSearchClient
                  .getIndexSettingsFor(
                      indexDescriptor.getFullQualifiedName(),
                      RetryOpenSearchClient.NUMBERS_OF_REPLICA)
                  .numberOfReplicas())
          .isEqualTo(String.valueOf(initialNumberOfReplicas));
    }

    tasklistProperties.getOpenSearch().setNumberOfReplicas(modifiedNumberOfReplicas);

    assertThat(indexSchemaValidator.schemaExists()).isFalse();

    schemaManager.createSchema();

    for (final IndexDescriptor indexDescriptor : indexDescriptors) {
      assertThat(
              noSqlHelper.indexHasAlias(
                  indexDescriptor.getFullQualifiedName(), indexDescriptor.getAlias()))
          .isTrue();
      assertThat(
              retryOpenSearchClient
                  .getIndexSettingsFor(
                      indexDescriptor.getFullQualifiedName(),
                      RetryOpenSearchClient.NUMBERS_OF_REPLICA)
                  .numberOfReplicas())
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
    testIndexDescriptor.setSchemaClasspathFilename(
        TestIndexDescriptorConfiguration.getSchemaFilePath("tasklist-test-after.json"));
    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        indexSchemaValidator.validateIndexMappings();

    // when
    schemaManager.updateSchema(indexDiff);

    // then
    final String indexName = testIndexDescriptor.getFullQualifiedName();
    final Map<String, IndexMapping> indexMappings = schemaManager.getIndexMappings(indexName);

    assertThat(indexMappings)
        .containsExactly(
            entry(
                indexName,
                new IndexMapping()
                    .setIndexName(indexName)
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
                                .setTypeDefinition(Map.of("type", "keyword"))))));
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

    final TypeMapping indexTemplateMapping =
        OpenSearchTestExtension.class
            .cast(databaseTestExtension)
            .getIndexTemplateMapping(testTemplateDescriptor.getTemplateName());
    assertThat(indexTemplateMapping.dynamic().jsonValue()).isEqualTo("strict");
    assertThat(indexTemplateMapping.properties().keySet())
        .containsExactlyInAnyOrder("prop0", "prop1", "prop2");
    assertThat(
            indexTemplateMapping.properties().values().stream()
                .map(Property::_kind)
                .collect(Collectors.toSet()))
        .containsOnly(Keyword);
  }
}
