/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static io.camunda.search.schema.SchemaTestUtil.mappingsMatch;
import static io.camunda.search.schema.utils.SchemaManagerITInvocationProvider.CONFIG_PREFIX;
import static io.camunda.search.test.utils.SearchDBExtension.CUSTOM_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.schema.config.RetentionConfiguration;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.search.schema.opensearch.OpensearchEngineClient;
import io.camunda.search.schema.utils.SchemaManagerITInvocationProvider;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.zeebe.test.util.junit.RegressionTestTemplate;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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

  private IndexDescriptor index;
  private IndexTemplateDescriptor indexTemplate;
  private ObjectMapper objectMapper;

  @BeforeEach
  public void refresh() throws IOException {
    objectMapper = TestObjectMapper.objectMapper();
    indexTemplate =
        SchemaTestUtil.mockIndexTemplate(
            "index_name",
            "test*",
            "template_alias",
            Collections.emptyList(),
            CONFIG_PREFIX + "-template_name",
            "/mappings.json");

    index =
        SchemaTestUtil.mockIndex(
            CONFIG_PREFIX + "-qualified_name", "alias", "index_name", "/mappings.json");

    when(indexTemplate.getFullQualifiedName()).thenReturn(CONFIG_PREFIX + "-qualified_name");
  }

  private SearchEngineClient searchEngineClientFromConfig(final SearchEngineConfiguration config) {
    switch (config.connect().getTypeEnum()) {
      case ELASTICSEARCH -> {
        final var connector = new ElasticsearchConnector(config.connect());
        final var client = connector.createClient();
        objectMapper = connector.objectMapper();
        return new ElasticsearchEngineClient(client, objectMapper);
      }
      case OPENSEARCH -> {
        final var connector = new OpensearchConnector(config.connect());
        final var client = connector.createClient();
        objectMapper = connector.objectMapper();
        return new OpensearchEngineClient(client, objectMapper);
      }
      default ->
          throw new IllegalArgumentException(
              "Unknown connection type: " + config.connect().getTypeEnum());
    }
  }

  @TestTemplate
  void shouldAppendToIndexMappingsWithNewProperties(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given
    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config),
            Set.of(index),
            Set.of(),
            SearchEngineConfiguration.of(b -> b),
            objectMapper);

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
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");

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
    when(index.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");

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
    final var newIndex =
        SchemaTestUtil.mockIndex(
            "new_index_qualified", "new_alias", "new_index", "/mappings-added-property.json");
    final var newIndexTemplate =
        SchemaTestUtil.mockIndexTemplate(
            "new_template_name",
            "new_test*",
            "new_template_alias",
            Collections.emptyList(),
            "new_template_name",
            "/mappings-added-property.json");

    when(newIndexTemplate.getFullQualifiedName())
        .thenReturn(config.connect().getIndexPrefix() + "new_template_index_qualified_name");

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
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn(newMappingsFile);

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
    when(indexTemplate.getMappingsClasspathFilename())
        .thenReturn("/mappings-and-updated-settings.json");

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

    when(indexTemplate.getMappingsClasspathFilename())
        .thenReturn("/mappings-settings-replica-and-shards.json");

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
  void shouldHaveCorrectSchemaUpdatesWithMultipleExporters(
      final SearchEngineConfiguration config, final SearchClientAdapter clientAdapter)
      throws Exception {
    // given
    final var schemaManager1 = createSchemaManager(Set.of(index), Set.of(indexTemplate), config);
    final var schemaManager2 = createSchemaManager(Set.of(index), Set.of(indexTemplate), config);

    when(index.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");

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
  void shouldNotErrorIfOldSchemaManagerStartsWhileNewSchemaManagerHasAlreadyStarted(
      final SearchEngineConfiguration config, final SearchClientAdapter clientAdapter)
      throws Exception {
    // given
    final var updatedSchemaManager =
        createSchemaManager(Set.of(index), Set.of(indexTemplate), config);
    final var oldSchemaManager = createSchemaManager(Set.of(index), Set.of(indexTemplate), config);

    // when
    when(index.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");

    updatedSchemaManager.startup();

    when(index.getMappingsClasspathFilename()).thenReturn("/mappings.json");
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn("/mappings.json");

    oldSchemaManager.startup();

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
            newPrefix + "-camunda-mapping-8.8.0_",
            newPrefix + "-camunda-role-8.8.0_",
            newPrefix + "-camunda-tenant-8.8.0_",
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
            newPrefix + "-tasklist-draft-task-variable-8.3.0_",
            newPrefix + "-tasklist-form-8.4.0_",
            newPrefix + "-tasklist-metric-8.3.0_",
            newPrefix + "-tasklist-task-8.5.0_",
            newPrefix + "-tasklist-task-variable-8.3.0_",
            newPrefix + "-tasklist-import-position-8.2.0_",
            newPrefix + "-tasklist-user-1.4.0_");
  }

  private SchemaManager createSchemaManager(
      final Collection<IndexDescriptor> indexDescriptors,
      final Collection<IndexTemplateDescriptor> templateDescriptors,
      final SearchEngineConfiguration config) {
    return new SchemaManager(
        searchEngineClientFromConfig(config),
        indexDescriptors,
        templateDescriptors,
        config,
        objectMapper);
  }
}
