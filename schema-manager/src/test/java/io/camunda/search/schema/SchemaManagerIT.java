/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static io.camunda.search.schema.utils.SchemaTestUtil.createSchemaManager;
import static io.camunda.search.schema.utils.SchemaTestUtil.createTestIndexDescriptor;
import static io.camunda.search.schema.utils.SchemaTestUtil.createTestTemplateDescriptor;
import static io.camunda.search.schema.utils.SchemaTestUtil.mappingsMatch;
import static io.camunda.search.schema.utils.SchemaTestUtil.searchEngineClientFromConfig;
import static io.camunda.search.test.utils.SearchDBExtension.CUSTOM_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.schema.config.RetentionConfiguration;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.exceptions.SearchEngineException;
import io.camunda.search.schema.metrics.SchemaManagerMetrics;
import io.camunda.search.schema.utils.SchemaManagerITInvocationProvider;
import io.camunda.search.schema.utils.TestIndexDescriptor;
import io.camunda.search.schema.utils.TestTemplateDescriptor;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.zeebe.test.util.junit.RegressionTestTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opensearch.client.opensearch._types.OpenSearchException;

@DisabledIfSystemProperty(
    named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
    matches = "^(?=\\s*\\S).*$",
    disabledReason = "Excluding from AWS OS IT CI")
@ExtendWith(SchemaManagerITInvocationProvider.class)
public class SchemaManagerIT {

  private TestIndexDescriptor index;
  private TestTemplateDescriptor indexTemplate;
  private ObjectMapper objectMapper;

  @BeforeEach
  public void refresh() throws IOException {
    objectMapper = TestObjectMapper.objectMapper();
    indexTemplate = createTestTemplateDescriptor("template_name", "/mappings.json");
    index = createTestIndexDescriptor("index_name", "/mappings.json");
  }

  @TestTemplate
  void shouldAppendToIndexMappingsWithNewProperties(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given
    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config), Set.of(index), Set.of(), config, objectMapper);

    schemaManager.initialiseResources();

    // when
    final var newProperties = new HashSet<IndexMappingProperty>();
    newProperties.add(new IndexMappingProperty("foo", Map.of("type", "text")));
    newProperties.add(new IndexMappingProperty("bar", Map.of("type", "keyword")));

    final Map<IndexDescriptor, Collection<IndexMappingProperty>> schemasToChange =
        Map.of(index, newProperties);

    schemaManager.updateSchemaMappings(schemasToChange);

    // then
    final var updatedIndex = searchClientAdapter.getIndexAsNode(index.getFullQualifiedName());

    assertThat(updatedIndex.at("/mappings/properties/foo/type").asText()).isEqualTo("text");
    assertThat(updatedIndex.at("/mappings/properties/bar/type").asText()).isEqualTo("keyword");
  }

  @TestTemplate
  void shouldInheritDefaultSettingsIfNoIndexSpecificSettings(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given
    final var properties = SearchEngineConfiguration.of(b -> b);
    properties.index().setNumberOfReplicas(10);
    properties.index().setNumberOfShards(10);

    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(index),
            Set.of(indexTemplate),
            properties,
            objectMapper);

    // when
    schemaManager.initialiseResources();

    // then
    final var retrievedIndex = searchClientAdapter.getIndexAsNode(index.getFullQualifiedName());

    assertThat(retrievedIndex.at("/settings/index/number_of_replicas").asInt()).isEqualTo(10);
    assertThat(retrievedIndex.at("/settings/index/number_of_shards").asInt()).isEqualTo(10);
  }

  @TestTemplate
  void shouldUseIndexSpecificSettingsIfSpecified(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given
    final var properties = SearchEngineConfiguration.of(b -> b);
    properties.index().setNumberOfReplicas(10);
    properties.index().setNumberOfShards(10);
    properties.index().setReplicasByIndexName(Map.of("index_name", 5));
    properties.index().setShardsByIndexName(Map.of("index_name", 5));

    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(index),
            Set.of(indexTemplate),
            properties,
            objectMapper);

    // when
    schemaManager.initialiseResources();

    // then
    final var retrievedIndex = searchClientAdapter.getIndexAsNode(index.getFullQualifiedName());

    assertThat(retrievedIndex.at("/settings/index/number_of_replicas").asInt()).isEqualTo(5);
    assertThat(retrievedIndex.at("/settings/index/number_of_shards").asInt()).isEqualTo(5);
  }

  @TestTemplate
  void shouldOverwriteIndexTemplateIfMappingsFileChanged(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given
    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(),
            Set.of(indexTemplate),
            SearchEngineConfiguration.of(b -> b),
            objectMapper);

    schemaManager.initialiseResources();

    // when
    indexTemplate.setMappingsClasspathFilename("/mappings-added-property.json");

    final Map<IndexDescriptor, Collection<IndexMappingProperty>> schemasToChange =
        Map.of(indexTemplate, Set.of());
    schemaManager.updateSchemaMappings(schemasToChange);

    // then
    final var template =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    assertThat(
            mappingsMatch(
                template.at("/index_template/template/mappings"), "/mappings-added-property.json"))
        .isTrue();
  }

  @TestTemplate
  void shouldCreateAllSchemasIfCreateEnabled(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given
    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(index),
            Set.of(indexTemplate),
            config,
            objectMapper);

    // when
    schemaManager.startup();

    // then
    final var retrievedIndex = searchClientAdapter.getIndexAsNode(index.getFullQualifiedName());
    final var retrievedIndexTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    assertThat(mappingsMatch(retrievedIndex.get("mappings"), "/mappings.json")).isTrue();
    assertThat(
            mappingsMatch(
                retrievedIndexTemplate.at("/index_template/template/mappings"), "/mappings.json"))
        .isTrue();
  }

  @TestTemplate
  void shouldUpdateSchemaMappingsCorrectlyIfCreateEnabled(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given
    config.schemaManager().setCreateSchema(true);
    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(index),
            Set.of(indexTemplate),
            config,
            objectMapper);

    schemaManager.startup();

    // when
    index.setMappingsClasspathFilename("/mappings-added-property.json");
    indexTemplate.setMappingsClasspathFilename("/mappings-added-property.json");

    schemaManager.startup();

    // then
    final var retrievedIndex = searchClientAdapter.getIndexAsNode(index.getFullQualifiedName());
    final var retrievedIndexTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    assertThat(mappingsMatch(retrievedIndex.get("mappings"), "/mappings-added-property.json"))
        .isTrue();
    assertThat(
            mappingsMatch(
                retrievedIndexTemplate.at("/index_template/template/mappings"),
                "/mappings-added-property.json"))
        .isTrue();
  }

  @TestTemplate
  void shouldCreateNewSchemasIfNewIndexDescriptorAddedToExistingSchemas(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given
    config.schemaManager().setCreateSchema(true);
    final var indices = new HashSet<IndexDescriptor>();
    final var indexTemplates = new HashSet<IndexTemplateDescriptor>();

    indices.add(index);
    indexTemplates.add(indexTemplate);

    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config), indices, indexTemplates, config, objectMapper);

    schemaManager.startup();

    // when
    final var newIndex = createTestIndexDescriptor("new_index", "/mappings-added-property.json");
    final var newIndexTemplate =
        createTestTemplateDescriptor("new_template_name", "/mappings-added-property.json");
    indices.add(newIndex);
    indexTemplates.add(newIndexTemplate);
    schemaManager.startup();

    // then
    final var retrievedNewIndex =
        searchClientAdapter.getIndexAsNode(newIndex.getFullQualifiedName());
    final var retrievedNewTemplate =
        searchClientAdapter.getIndexTemplateAsNode(newIndexTemplate.getTemplateName());

    assertThat(mappingsMatch(retrievedNewIndex.get("mappings"), "/mappings-added-property.json"))
        .isTrue();
    assertThat(
            mappingsMatch(
                retrievedNewTemplate.at("/index_template/template/mappings"),
                "/mappings-added-property.json"))
        .isTrue();
  }

  @TestTemplate
  void shouldNotPutAnySchemasIfCreatedDisabled(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter) {
    // given
    config.schemaManager().setCreateSchema(false);

    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(index),
            Set.of(indexTemplate),
            config,
            objectMapper);

    schemaManager.startup();

    // then
    assertThatThrownBy(() -> searchClientAdapter.getIndexAsNode(index.getFullQualifiedName()))
        .isInstanceOfAny(ElasticsearchException.class, OpenSearchException.class)
        .hasMessageContaining("no such index");
    assertThatThrownBy(
            () -> searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName()))
        .isInstanceOfAny(ElasticsearchException.class, OpenSearchException.class)
        .hasMessageContaining(String.format("[%s] not found", indexTemplate.getTemplateName()));
  }

  @TestTemplate
  void shouldCreateLifeCyclePoliciesOnStartupIfEnabled(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws IOException {
    config.schemaManager().setCreateSchema(true);
    config.retention().setEnabled(true);
    config.retention().setPolicyName("policy_name");

    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config), Set.of(), Set.of(), config, objectMapper);

    schemaManager.startup();

    final var policy = searchClientAdapter.getPolicyAsNode("policy_name");

    assertThat(policy.get("policy")).isNotNull();
  }

  @TestTemplate
  void shouldCreateIndexInAdditionToTemplateFromTemplateDescriptor(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    config.schemaManager().setCreateSchema(true);

    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(),
            Set.of(indexTemplate),
            config,
            objectMapper);

    schemaManager.startup();

    final var retrievedIndex =
        searchClientAdapter.getIndexAsNode(indexTemplate.getFullQualifiedName());

    assertThat(retrievedIndex.at("/settings/index/provided_name").asText())
        .isEqualTo(indexTemplate.getFullQualifiedName());
  }

  @TestTemplate
  void shouldAlsoUpdateCorrespondingIndexWhenIndexTemplateUpdated(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws IOException {
    // given
    config.schemaManager().setCreateSchema(true);

    final var currentMappingsFile = index.getMappingsClasspathFilename();
    final var newMappingsFile = "/mappings-added-property.json";

    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(),
            Set.of(indexTemplate),
            config,
            objectMapper);

    schemaManager.startup();

    final var retrievedIndex =
        searchClientAdapter.getIndexAsNode(indexTemplate.getFullQualifiedName());

    assertThat(mappingsMatch(retrievedIndex.get("mappings"), currentMappingsFile)).isTrue();

    // when
    indexTemplate.setMappingsClasspathFilename(newMappingsFile);

    schemaManager.startup();

    // then
    final var updatedIndex =
        searchClientAdapter.getIndexAsNode(indexTemplate.getFullQualifiedName());

    assertThat(mappingsMatch(updatedIndex.get("mappings"), newMappingsFile)).isTrue();
  }

  @TestTemplate
  void shouldUpdateSettingsForIndexTemplatesButNotUpdateIndexSettingsWhenSchemaChanges(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws IOException {
    // given
    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(),
            Set.of(indexTemplate),
            config,
            objectMapper);

    schemaManager.startup();

    final var indexTemplateSettingsToBeAppended =
        "/index_template/template/settings/index/refresh_interval";
    final var indexSettingsToBeAppended = "/settings/index/refresh_interval";

    final var initialTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());
    final var initialMatchingIndex =
        searchClientAdapter.getIndexAsNode(indexTemplate.getFullQualifiedName());

    assertThat(initialTemplate.at(indexTemplateSettingsToBeAppended).asText()).isEqualTo("");
    assertThat(initialMatchingIndex.at(indexSettingsToBeAppended).asText()).isEqualTo("");

    // when
    indexTemplate.setMappingsClasspathFilename("/mappings-and-updated-settings.json");

    // change index template schema to have new updated settings and trigger update
    schemaManager.startup();

    // then
    final var updatedTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());
    final var updatedMatchingIndex =
        searchClientAdapter.getIndexAsNode(indexTemplate.getFullQualifiedName());

    assertThat(updatedTemplate.at(indexTemplateSettingsToBeAppended).asText()).isEqualTo("5s");
    assertThat(updatedMatchingIndex.at(indexSettingsToBeAppended).asText()).isEqualTo("");
  }

  @TestTemplate
  void shouldUpdateIndexTemplateWithNewReplicaAndShardCount(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws IOException {
    // given
    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(),
            Set.of(indexTemplate),
            config,
            objectMapper);

    schemaManager.startup();

    final var replicaSettingPath = "/index_template/template/settings/index/number_of_replicas";
    final var shardsSettingPath = "/index_template/template/settings/index/number_of_shards";

    final var initialTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    assertThat(initialTemplate.at(replicaSettingPath).asInt()).isEqualTo(0);
    assertThat(initialTemplate.at(shardsSettingPath).asInt()).isEqualTo(1);

    // when
    config.index().setNumberOfReplicas(5);
    config.index().setNumberOfShards(5);

    schemaManager.startup();

    // then
    final var updatedTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    assertThat(updatedTemplate.at(replicaSettingPath).asInt()).isEqualTo(5);
    assertThat(updatedTemplate.at(shardsSettingPath).asInt()).isEqualTo(5);
  }

  @TestTemplate
  void shouldUpdateIndexWithNewReplicaCountButNotNewShardCount(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws IOException {
    // given
    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config), Set.of(index), Set.of(), config, objectMapper);

    schemaManager.startup();

    final var replicaSettingPath = "/settings/index/number_of_replicas";
    final var shardsSettingPath = "/settings/index/number_of_shards";

    final var initialIndex = searchClientAdapter.getIndexAsNode(index.getFullQualifiedName());

    assertThat(initialIndex.at(replicaSettingPath).asInt()).isEqualTo(0);
    assertThat(initialIndex.at(shardsSettingPath).asInt()).isEqualTo(1);

    // when
    config.index().setNumberOfReplicas(5);
    config.index().setNumberOfShards(5);

    schemaManager.startup();

    // then
    final var updatedIndex = searchClientAdapter.getIndexAsNode(index.getFullQualifiedName());

    assertThat(updatedIndex.at(replicaSettingPath).asInt()).isEqualTo(5);
    assertThat(updatedIndex.at(shardsSettingPath).asInt()).isEqualTo(1);
  }

  @TestTemplate
  void shouldIsSchemaReadyForUseReturnTrueWhenAllIndicesAndTemplatesAreCreated(
      final SearchEngineConfiguration config, final SearchClientAdapter ignored) {
    // given
    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(index),
            Set.of(indexTemplate),
            config,
            objectMapper);

    schemaManager.startup();

    // when, then
    assertThat(schemaManager.isSchemaReadyForUse()).isTrue();
  }

  @TestTemplate
  void shouldIsSchemaReadyForUseReturnFalseWhenARuntimeTemplatedIndexIsMissing(
      final SearchEngineConfiguration config, final SearchClientAdapter ignored) {
    // given
    final SearchEngineClient searchEngineClient = searchEngineClientFromConfig(config);
    final var schemaManager =
        new SchemaManager(
            searchEngineClient, Set.of(index), Set.of(indexTemplate), config, objectMapper);

    schemaManager.startup();

    // delete the templated runtime index
    searchEngineClient.deleteIndex(indexTemplate.getFullQualifiedName());

    // when, then
    assertThat(schemaManager.isSchemaReadyForUse()).isFalse();
  }

  @TestTemplate
  void shouldIsSchemaReadyForUseReturnFalseWhenTemplateHasDifferentMapping(
      final SearchEngineConfiguration config, final SearchClientAdapter ignored) {
    // given
    final SearchEngineClient searchEngineClient = searchEngineClientFromConfig(config);
    final var schemaManager =
        new SchemaManager(
            searchEngineClient, Set.of(), Set.of(indexTemplate), config, objectMapper);

    schemaManager.startup();

    // update the index template with a different mapping
    indexTemplate.setMappingsClasspathFilename("/mappings-added-property.json");

    // when, then
    assertThat(schemaManager.isSchemaReadyForUse()).isFalse();
  }

  @TestTemplate
  void shouldUseReplicaAndShardFromConfigIfConflictingWithValuesInJsonSchema(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws IOException {
    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(),
            Set.of(indexTemplate),
            config,
            objectMapper);

    schemaManager.startup();

    final var replicaSettingPath = "/index_template/template/settings/index/number_of_replicas";
    final var shardsSettingPath = "/index_template/template/settings/index/number_of_shards";

    final var initialTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    assertThat(initialTemplate.at(replicaSettingPath).asInt()).isEqualTo(0);
    assertThat(initialTemplate.at(shardsSettingPath).asInt()).isEqualTo(1);

    indexTemplate.setMappingsClasspathFilename("/mappings-settings-replica-and-shards.json");

    config.index().setNumberOfReplicas(5);
    config.index().setNumberOfShards(5);

    schemaManager.startup();

    final var updatedTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    assertThat(updatedTemplate.at(replicaSettingPath).asInt()).isEqualTo(5);
    assertThat(updatedTemplate.at(shardsSettingPath).asInt()).isEqualTo(5);
  }

  @TestTemplate
  void shouldCreateCorrespondingIndexIfIndexTemplateAlreadyExists(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws IOException {
    // given
    final var searchEngineClient = searchEngineClientFromConfig(config);

    searchEngineClient.createIndexTemplate(indexTemplate, new IndexConfiguration(), true);

    searchClientAdapter.refresh();

    // when
    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(),
            Set.of(indexTemplate),
            config,
            objectMapper);

    schemaManager.startup();

    // then
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThatNoException()
                    .isThrownBy(
                        () ->
                            searchClientAdapter.getIndexAsNode(
                                indexTemplate.getFullQualifiedName())));
  }

  @RegressionTestTemplate("https://github.com/camunda/camunda/issues/26056")
  void shouldNotHaveValidationIssuesWithTheSameIndices(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter) {
    config.schemaManager().setCreateSchema(true);

    final var indexDescriptors =
        new IndexDescriptors(
            config.connect().getIndexPrefix(), config.connect().getTypeEnum().isElasticSearch());

    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            indexDescriptors.indices(),
            indexDescriptors.templates(),
            config,
            objectMapper);

    schemaManager.startup();
    assertThatNoException().isThrownBy(schemaManager::startup);
  }

  @TestTemplate
  void shouldStartDifferentSchemaManagersWithRetention(
      final SearchEngineConfiguration config, final SearchClientAdapter ignored) {
    // given
    final RetentionConfiguration retention = config.retention();
    retention.setEnabled(true);
    retention.setPolicyName("shouldOpenDifferentPartitionsWithRetention");

    final var schemaManager1 =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(),
            Set.of(indexTemplate),
            config,
            objectMapper);

    final var schemaManager2 =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(),
            Set.of(indexTemplate),
            config,
            objectMapper);

    // when
    final var future = CompletableFuture.runAsync(() -> schemaManager1.startup());

    // then
    assertThatNoException().isThrownBy(() -> schemaManager2.startup());
    Awaitility.await("Schema manager one has been run successfully")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(future).isNotCompletedExceptionally();
              assertThat(future).isCompleted();
            });
  }

  @TestTemplate
  @DisabledIfSystemProperty(
      named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "Ineligible test for AWS OS integration")
  void shouldHaveCorrectSchemaUpdatesWithMultipleRuns(
      final SearchEngineConfiguration config, final SearchClientAdapter clientAdapter)
      throws Exception {
    // given
    final var schemaManager1 = createSchemaManager(Set.of(index), Set.of(indexTemplate), config);
    final var schemaManager2 = createSchemaManager(Set.of(index), Set.of(indexTemplate), config);

    index.setMappingsClasspathFilename("/mappings-added-property.json");
    indexTemplate.setMappingsClasspathFilename("/mappings-added-property.json");

    // when
    schemaManager1.startup();
    schemaManager2.startup();

    // then
    final var retrievedIndex = clientAdapter.getIndexAsNode(index.getFullQualifiedName());
    final var retrievedIndexTemplate =
        clientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    assertThat(mappingsMatch(retrievedIndex.get("mappings"), "/mappings-added-property.json"))
        .isTrue();
    assertThat(
            mappingsMatch(
                retrievedIndexTemplate.at("/index_template/template/mappings"),
                "/mappings-added-property.json"))
        .isTrue();
  }

  @TestTemplate
  @DisabledIfSystemProperty(
      named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "Ineligible test for AWS OS integration")
  void shouldHaveCorrectSchemaUpdatesWithConcurrentRuns(
      final SearchEngineConfiguration config, final SearchClientAdapter clientAdapter)
      throws Exception {
    // given
    final var schemaManager1 = createSchemaManager(Set.of(index), Set.of(indexTemplate), config);
    final var schemaManager2 = createSchemaManager(Set.of(index), Set.of(indexTemplate), config);

    index.setMappingsClasspathFilename("/mappings-added-property.json");
    indexTemplate.setMappingsClasspathFilename("/mappings-added-property.json");

    // when
    schemaManager1.startup();
    schemaManager2.startup();

    // then
    final var retrievedIndex = clientAdapter.getIndexAsNode(index.getFullQualifiedName());
    final var retrievedIndexTemplate =
        clientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    assertThat(mappingsMatch(retrievedIndex.get("mappings"), "/mappings-added-property.json"))
        .isTrue();
    assertThat(
            mappingsMatch(
                retrievedIndexTemplate.at("/index_template/template/mappings"),
                "/mappings-added-property.json"))
        .isTrue();
  }

  @TestTemplate
  void shouldUpdateTemplateIndicesWithNewMapping(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws IOException {
    // given
    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(),
            Set.of(indexTemplate),
            config,
            objectMapper);

    schemaManager.startup();

    final String runtimeIndexName = indexTemplate.getFullQualifiedName();
    final var initialRuntimeIndex = searchClientAdapter.getIndexAsNode(runtimeIndexName);
    assertThat(mappingsMatch(initialRuntimeIndex.get("mappings"), "/mappings.json")).isTrue();

    final String archiveIndexName = indexTemplate.getIndexPattern().replace("*", "-archived");
    searchClientAdapter.index("123", archiveIndexName, Map.of("hello", "foo", "world", "bar"));
    final var initialArchiveIndex = searchClientAdapter.getIndexAsNode(archiveIndexName);
    assertThat(mappingsMatch(initialArchiveIndex.get("mappings"), "/mappings.json")).isTrue();

    // when
    indexTemplate.setMappingsClasspathFilename("/mappings-added-property.json");
    schemaManager.startup();

    // then
    final var updatedRuntimeIndex = searchClientAdapter.getIndexAsNode(runtimeIndexName);
    assertThat(mappingsMatch(updatedRuntimeIndex.get("mappings"), "/mappings-added-property.json"))
        .isTrue();

    final var updatedArchiveIndex = searchClientAdapter.getIndexAsNode(archiveIndexName);
    assertThat(mappingsMatch(updatedArchiveIndex.get("mappings"), "/mappings-added-property.json"))
        .isTrue();
  }

  @TestTemplate
  void shouldUpdateTemplateIndicesWithNewReplicaCount(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws IOException {
    // given
    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(),
            Set.of(indexTemplate),
            config,
            objectMapper);

    schemaManager.startup();

    final var replicaSettingPath = "/settings/index/number_of_replicas";
    final var shardsSettingPath = "/settings/index/number_of_shards";

    final String runtimeIndexName = indexTemplate.getFullQualifiedName();
    final var initialRuntimeIndex = searchClientAdapter.getIndexAsNode(runtimeIndexName);

    assertThat(initialRuntimeIndex.at(replicaSettingPath).asInt()).isEqualTo(0);
    assertThat(initialRuntimeIndex.at(shardsSettingPath).asInt()).isEqualTo(1);

    final String archiveIndexName = indexTemplate.getIndexPattern().replace("*", "-archived");
    searchClientAdapter.index("123", archiveIndexName, Map.of("hello", "foo", "world", "bar"));

    final var initialArchiveIndex = searchClientAdapter.getIndexAsNode(archiveIndexName);
    assertThat(initialArchiveIndex.at(replicaSettingPath).asInt()).isEqualTo(0);
    assertThat(initialArchiveIndex.at(shardsSettingPath).asInt()).isEqualTo(1);

    // when
    config.index().setNumberOfReplicas(5);
    config.index().setNumberOfShards(5);

    schemaManager.startup();

    // then
    final var updatedRuntimeIndex = searchClientAdapter.getIndexAsNode(runtimeIndexName);

    assertThat(updatedRuntimeIndex.at(replicaSettingPath).asInt()).isEqualTo(5);
    assertThat(updatedRuntimeIndex.at(shardsSettingPath).asInt()).isEqualTo(1);

    final var updatedArchiveIndex = searchClientAdapter.getIndexAsNode(archiveIndexName);

    assertThat(updatedArchiveIndex.at(replicaSettingPath).asInt()).isEqualTo(5);
    assertThat(updatedArchiveIndex.at(shardsSettingPath).asInt()).isEqualTo(1);
  }

  @TestTemplate
  void shouldCreateHarmonizedSchema(
      final SearchEngineConfiguration config, final SearchClientAdapter adapter)
      throws IOException {
    // given
    final var newPrefix =
        CUSTOM_PREFIX + RandomStringUtils.insecure().nextAlphabetic(9).toLowerCase();
    config.connect().setIndexPrefix(newPrefix);
    final var indexDescriptors =
        new IndexDescriptors(newPrefix, config.connect().getTypeEnum().isElasticSearch());
    final SchemaManager schemaManager =
        createSchemaManager(indexDescriptors.indices(), indexDescriptors.templates(), config);

    final var mappingsBeforeStart = adapter.getAllIndicesAsNode(newPrefix);
    assertThat(mappingsBeforeStart).isEmpty();

    // when
    schemaManager.startup();

    // then
    final var mappingsAfterOpen = adapter.getAllIndicesAsNode(newPrefix);
    assertThat(mappingsAfterOpen.keySet())
        // we verify the names hard coded on purpose
        // to make sure no index will be accidentally dropped, names are changed or added
        .containsExactlyInAnyOrder(
            newPrefix + "-camunda-authorization-8.8.0_",
            newPrefix + "-camunda-group-8.8.0_",
            newPrefix + "-camunda-mapping-rule-8.8.0_",
            newPrefix + "-camunda-role-8.8.0_",
            newPrefix + "-camunda-tenant-8.8.0_",
            newPrefix + "-camunda-usage-metric-8.8.0_",
            newPrefix + "-camunda-usage-metric-tu-8.8.0_",
            newPrefix + "-camunda-user-8.8.0_",
            newPrefix + "-camunda-web-session-8.8.0_",
            newPrefix + "-operate-batch-operation-1.0.0_",
            newPrefix + "-operate-decision-8.3.0_",
            newPrefix + "-operate-decision-instance-8.3.0_",
            newPrefix + "-operate-decision-requirements-8.3.0_",
            newPrefix + "-operate-event-8.3.0_",
            newPrefix + "-operate-flownode-instance-8.3.1_",
            newPrefix + "-operate-import-position-8.3.0_",
            newPrefix + "-operate-incident-8.3.1_",
            newPrefix + "-operate-list-view-8.3.0_",
            newPrefix + "-operate-metric-8.3.0_",
            newPrefix + "-operate-message-8.5.0_",
            newPrefix + "-operate-operation-8.4.1_",
            newPrefix + "-operate-post-importer-queue-8.3.0_",
            newPrefix + "-operate-process-8.3.0_",
            newPrefix + "-operate-sequence-flow-8.3.0_",
            newPrefix + "-operate-variable-8.3.0_",
            newPrefix + "-operate-job-8.6.0_",
            newPrefix + "-operate-user-1.2.0_",
            newPrefix + "-tasklist-draft-task-variable-8.3.0_",
            newPrefix + "-tasklist-form-8.4.0_",
            newPrefix + "-tasklist-metric-8.3.0_",
            newPrefix + "-tasklist-task-8.5.0_",
            newPrefix + "-tasklist-task-variable-8.3.0_",
            newPrefix + "-tasklist-import-position-8.2.0_");
  }

  @TestTemplate
  void shouldRecordSchemaInitTimerMetric(
      final SearchEngineConfiguration config, final SearchClientAdapter ignored) {
    // given
    final var registry = new SimpleMeterRegistry();
    final var schemaManager =
        new SchemaManager(
                searchEngineClientFromConfig(config), Set.of(index), Set.of(), config, objectMapper)
            .withMetrics(new SchemaManagerMetrics(registry));

    // when
    schemaManager.startup();

    // then
    final var measuredTime = registry.find("camunda.schema.init.time").timer();
    assertThat(measuredTime.count()).isEqualTo(1);
    assertThat(measuredTime.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0);
  }

  @TestTemplate
  void shouldNotRecordSchemaInitTimerMetricOnFailure(
      final SearchEngineConfiguration config, final SearchClientAdapter ignored) {
    // given
    final var registry = new SimpleMeterRegistry();
    // alter configuration to trigger failure
    config.connect().setUrl("http://bad-url");
    config.schemaManager().getRetry().setMaxRetries(1);
    final var schemaManager =
        new SchemaManager(
                searchEngineClientFromConfig(config), Set.of(index), Set.of(), config, objectMapper)
            .withMetrics(new SchemaManagerMetrics(registry));

    // when
    assertThatExceptionOfType(SearchEngineException.class)
        .isThrownBy(() -> schemaManager.startup());

    // then
    final var measuredTime = registry.find("camunda.schema.init.time").timer();
    assertThat(measuredTime.count()).isEqualTo(0);
    assertThat(measuredTime.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(0);
  }

  @TestTemplate
  void shouldSkipDynamicPropertyMappingsDifferences(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws IOException {
    // given
    // in Opensearch, "dynamic" field is stored String, while in Elasticsearch it is saved as
    // boolean this gives different results in diff comparison :(
    final var mappingsFileNamePrefix = config.connect().getTypeEnum().isOpenSearch() ? "/os" : "";
    final var indexTemplate =
        createTestTemplateDescriptor(
            "template_name", mappingsFileNamePrefix + "/mappings-dynamic-property.json");

    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(),
            Set.of(indexTemplate),
            config,
            objectMapper);

    schemaManager.startup();

    final var runtimeIndexName = indexTemplate.getFullQualifiedName();
    final var archiveIndexName1 = indexTemplate.getIndexPattern().replace("*", "-archived_1");
    final var archiveIndexName2 = indexTemplate.getIndexPattern().replace("*", "-archived_2");

    // index some data to the runtime and archive indices. "world" is a dynamic property
    searchClientAdapter.index(
        "123", runtimeIndexName, Map.of("hello", "a", "world", Map.of("header1", 1, "header2", 2)));
    searchClientAdapter.index(
        "123", archiveIndexName1, Map.of("hello", "a", "world", Map.of("header3", true)));
    searchClientAdapter.index("123", archiveIndexName2, Map.of("hello", "a"));

    var retrievedRuntimeIndex = searchClientAdapter.getIndexAsNode(runtimeIndexName);
    assertThat(retrievedRuntimeIndex.at("/mappings/properties/world/properties").toString())
        .isEqualTo("{\"header2\":{\"type\":\"long\"},\"header1\":{\"type\":\"long\"}}");
    var retrievedArchiveIndex1 = searchClientAdapter.getIndexAsNode(archiveIndexName1);
    assertThat(retrievedArchiveIndex1.at("/mappings/properties/world/properties").toString())
        .isEqualTo("{\"header3\":{\"type\":\"boolean\"}}");

    // when
    schemaManager.startup();

    // then
    // no exception should be thrown
    retrievedRuntimeIndex = searchClientAdapter.getIndexAsNode(runtimeIndexName);
    assertThat(retrievedRuntimeIndex.at("/mappings/properties/world/properties").toString())
        .isEqualTo("{\"header2\":{\"type\":\"long\"},\"header1\":{\"type\":\"long\"}}");
    retrievedArchiveIndex1 = searchClientAdapter.getIndexAsNode(archiveIndexName1);
    assertThat(retrievedArchiveIndex1.at("/mappings/properties/world/properties").toString())
        .isEqualTo("{\"header3\":{\"type\":\"boolean\"}}");

    // when
    // update mappings
    indexTemplate.setMappingsClasspathFilename(
        mappingsFileNamePrefix + "/mappings-dynamic-property-added.json");
    schemaManager.startup();

    // then
    // assert all indices have the updated mapping
    retrievedRuntimeIndex = searchClientAdapter.getIndexAsNode(runtimeIndexName);
    assertThat(retrievedRuntimeIndex.at("/mappings/properties/foo/type").asText())
        .isEqualTo("keyword");
    assertThat(retrievedRuntimeIndex.at("/mappings/properties/world/properties").toString())
        .isEqualTo("{\"header2\":{\"type\":\"long\"},\"header1\":{\"type\":\"long\"}}");
    retrievedArchiveIndex1 = searchClientAdapter.getIndexAsNode(archiveIndexName1);
    assertThat(retrievedArchiveIndex1.at("/mappings/properties/foo/type").asText())
        .isEqualTo("keyword");
    assertThat(retrievedArchiveIndex1.at("/mappings/properties/world/properties").toString())
        .isEqualTo("{\"header3\":{\"type\":\"boolean\"}}");
    final var retrievedArchiveIndex2 = searchClientAdapter.getIndexAsNode(archiveIndexName2);
    assertThat(retrievedArchiveIndex2.at("/mappings/properties/foo/type").asText())
        .isEqualTo("keyword");
  }

  @TestTemplate
  void shouldSetIndexTemplatePriorityWhenCreatingTemplate(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given
    config.index().setTemplatePriority(100);

    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(),
            Set.of(indexTemplate),
            config,
            objectMapper);

    // when
    schemaManager.startup();

    // then
    final var retrievedTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    assertThat(retrievedTemplate.at("/index_template/priority").asInt()).isEqualTo(100);
  }

  @TestTemplate
  void shouldUseDefaultPriorityWhenTemplatePriorityNotSet(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given
    // templatePriority is not set, should be null
    assertThat(config.index().getTemplatePriority()).isNull();

    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(),
            Set.of(indexTemplate),
            config,
            objectMapper);

    // when
    schemaManager.startup();

    // then
    final var retrievedTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    // When priority is not set, it should either be missing or null/0 depending on search engine
    final var priorityNode = retrievedTemplate.at("/index_template/priority");
    assertThat(priorityNode.isMissingNode()).isTrue();
  }

  @TestTemplate
  void shouldUpdateIndexTemplatePriorityWhenSettingsChange(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given - create template with initial priority
    config.index().setTemplatePriority(50);

    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(),
            Set.of(indexTemplate),
            config,
            objectMapper);

    schemaManager.startup();

    // verify initial priority is set
    var retrievedTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());
    assertThat(retrievedTemplate.at("/index_template/priority").asInt()).isEqualTo(50);

    // when - update template settings with new priority via startup
    config.index().setTemplatePriority(200);

    schemaManager.startup();

    // then - verify priority was updated
    retrievedTemplate = searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());
    assertThat(retrievedTemplate.at("/index_template/priority").asInt()).isEqualTo(200);
  }

  @TestTemplate
  void shoulUnsetIndexTemplatePriorityWhenSettingsIsRemoved(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given - create template with initial priority
    config.index().setTemplatePriority(50);

    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(),
            Set.of(indexTemplate),
            config,
            objectMapper);

    schemaManager.startup();

    // verify initial priority is set
    var retrievedTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());
    assertThat(retrievedTemplate.at("/index_template/priority").asInt()).isEqualTo(50);

    // when - unset template priority setting
    config.index().setTemplatePriority(null);

    schemaManager.startup();

    // then - verify priority was updated
    retrievedTemplate = searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());
    assertThat(retrievedTemplate.at("/index_template/priority").isMissingNode()).isTrue();
  }

  @TestTemplate
  void shouldPreservePriorityWhenUpdatingTemplateMappings(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given - create template with priority
    config.index().setTemplatePriority(150);

    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(),
            Set.of(indexTemplate),
            config,
            objectMapper);

    schemaManager.startup();

    // verify initial priority is set
    var retrievedTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());
    assertThat(retrievedTemplate.at("/index_template/priority").asInt()).isEqualTo(150);

    // when - update template mappings
    indexTemplate.setMappingsClasspathFilename("/mappings-added-property.json");
    schemaManager.startup();

    // then - priority should be preserved
    retrievedTemplate = searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());
    assertThat(retrievedTemplate.at("/index_template/priority").asInt()).isEqualTo(150);

    // and mapping should be updated
    assertThat(
            mappingsMatch(
                retrievedTemplate.at("/index_template/template/mappings"),
                "/mappings-added-property.json"))
        .isTrue();
  }
}
