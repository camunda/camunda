/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static io.camunda.search.schema.utils.CamundaExporterITInvocationProvider.CONFIG_PREFIX;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.schema.SchemaTestUtil;
import io.camunda.exporter.utils.TestObjectMapper;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.schema.configuration.IndexConfiguration;
import io.camunda.search.schema.configuration.SearchEngineConfiguration;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.search.schema.opensearch.OpensearchEngineClient;
import io.camunda.search.schema.utils.CamundaExporterITInvocationProvider;
import io.camunda.search.schema.utils.SearchClientAdapter;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opensearch.client.opensearch._types.OpenSearchException;

@DisabledIfSystemProperty(
    named = "test.integration.opensearch.aws.url",
    matches = "^(?=\\s*\\S).*$",
    disabledReason = "Excluding from AWS OS IT CI")
@ExtendWith(CamundaExporterITInvocationProvider.class)
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

    when(indexTemplate.getFullQualifiedName())
        .thenReturn(CONFIG_PREFIX + "-template-index-qualified-name");
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

    Assertions.assertThat(updatedIndex.at("/mappings/properties/foo/type").asText())
        .isEqualTo("text");
    Assertions.assertThat(updatedIndex.at("/mappings/properties/bar/type").asText())
        .isEqualTo("keyword");
  }

  @TestTemplate
  void shouldInheritDefaultSettingsIfNoIndexSpecificSettings(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given
    final var indexSettings = new IndexConfiguration();
    indexSettings.setNumberOfReplicas(10);
    indexSettings.setNumberOfShards(10);
    final var properties = SearchEngineConfiguration.of(b -> b.index(indexSettings));

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

    Assertions.assertThat(retrievedIndex.at("/settings/index/number_of_replicas").asInt())
        .isEqualTo(10);
    Assertions.assertThat(retrievedIndex.at("/settings/index/number_of_shards").asInt())
        .isEqualTo(10);
  }

  @TestTemplate
  void shouldUseIndexSpecificSettingsIfSpecified(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given
    final var indexSettings = new IndexConfiguration();
    indexSettings.setNumberOfReplicas(10);
    indexSettings.setNumberOfShards(10);
    indexSettings.setReplicasByIndexName(Map.of("index_name", 5));
    indexSettings.setShardsByIndexName(Map.of("index_name", 5));
    final var properties = SearchEngineConfiguration.of(b -> b.index(indexSettings));

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

    Assertions.assertThat(retrievedIndex.at("/settings/index/number_of_replicas").asInt())
        .isEqualTo(5);
    Assertions.assertThat(retrievedIndex.at("/settings/index/number_of_shards").asInt())
        .isEqualTo(5);
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

    Assertions.assertThat(
            SchemaTestUtil.mappingsMatch(
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

    Assertions.assertThat(
            SchemaTestUtil.mappingsMatch(retrievedIndex.get("mappings"), "/mappings.json"))
        .isTrue();
    Assertions.assertThat(
            SchemaTestUtil.mappingsMatch(
                retrievedIndexTemplate.at("/index_template/template/mappings"), "/mappings.json"))
        .isTrue();
  }

  @TestTemplate
  void shouldUpdateSchemaMappingsCorrectlyIfCreateEnabled(
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

    schemaManager.startup();

    // when
    when(index.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");

    schemaManager.startup();

    // then
    final var retrievedIndex = searchClientAdapter.getIndexAsNode(index.getFullQualifiedName());
    final var retrievedIndexTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    Assertions.assertThat(
            SchemaTestUtil.mappingsMatch(
                retrievedIndex.get("mappings"), "/mappings-added-property.json"))
        .isTrue();
    Assertions.assertThat(
            SchemaTestUtil.mappingsMatch(
                retrievedIndexTemplate.at("/index_template/template/mappings"),
                "/mappings-added-property.json"))
        .isTrue();
  }

  @TestTemplate
  void shouldCreateNewSchemasIfNewIndexDescriptorAddedToExistingSchemas(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given
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
        .thenReturn(config.index().getPrefix() + "new_template_index_qualified_name");

    indices.add(newIndex);
    indexTemplates.add(newIndexTemplate);

    schemaManager.startup();

    // then
    final var retrievedNewIndex =
        searchClientAdapter.getIndexAsNode(newIndex.getFullQualifiedName());
    final var retrievedNewTemplate =
        searchClientAdapter.getIndexTemplateAsNode(newIndexTemplate.getTemplateName());

    Assertions.assertThat(
            SchemaTestUtil.mappingsMatch(
                retrievedNewIndex.get("mappings"), "/mappings-added-property.json"))
        .isTrue();
    Assertions.assertThat(
            SchemaTestUtil.mappingsMatch(
                retrievedNewTemplate.at("/index_template/template/mappings"),
                "/mappings-added-property.json"))
        .isTrue();
  }

  @TestTemplate
  void shouldNotPutAnySchemasIfCreatedDisabled(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter) {
    // given
    // config.setCreateSchema(false);

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
    // config.setCreateSchema(true);
    config.retention().setEnabled(true);
    config.retention().setPolicyName("policy_name");

    final var schemaManager =
        new SchemaManager(
            searchEngineClientFromConfig(config), Set.of(), Set.of(), config, objectMapper);

    schemaManager.startup();

    final var policy = searchClientAdapter.getPolicyAsNode("policy_name");

    Assertions.assertThat(policy.get("policy")).isNotNull();
  }

  @TestTemplate
  void shouldCreateIndexInAdditionToTemplateFromTemplateDescriptor(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // config.setCreateSchema(true);

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

    Assertions.assertThat(retrievedIndex.at("/settings/index/provided_name").asText())
        .isEqualTo(indexTemplate.getFullQualifiedName());
  }

  @TestTemplate
  void shouldAlsoUpdateCorrespondingIndexWhenIndexTemplateUpdated(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws IOException {
    // given
    // config.setCreateSchema(true);

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

    Assertions.assertThat(
            SchemaTestUtil.mappingsMatch(retrievedIndex.get("mappings"), currentMappingsFile))
        .isTrue();

    // when
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn(newMappingsFile);

    schemaManager.startup();

    // then
    final var updatedIndex =
        searchClientAdapter.getIndexAsNode(indexTemplate.getFullQualifiedName());

    Assertions.assertThat(
            SchemaTestUtil.mappingsMatch(updatedIndex.get("mappings"), newMappingsFile))
        .isTrue();
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

    Assertions.assertThat(initialTemplate.at(indexTemplateSettingsToBeAppended).asText())
        .isEqualTo("");
    Assertions.assertThat(initialMatchingIndex.at(indexSettingsToBeAppended).asText())
        .isEqualTo("");

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

    Assertions.assertThat(updatedTemplate.at(indexTemplateSettingsToBeAppended).asText())
        .isEqualTo("5s");
    Assertions.assertThat(updatedMatchingIndex.at(indexSettingsToBeAppended).asText())
        .isEqualTo("");
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

    Assertions.assertThat(initialTemplate.at(replicaSettingPath).asInt()).isEqualTo(0);
    Assertions.assertThat(initialTemplate.at(shardsSettingPath).asInt()).isEqualTo(1);

    // when
    config.index().setNumberOfReplicas(5);
    config.index().setNumberOfShards(5);

    schemaManager.startup();

    // then
    final var updatedTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    Assertions.assertThat(updatedTemplate.at(replicaSettingPath).asInt()).isEqualTo(5);
    Assertions.assertThat(updatedTemplate.at(shardsSettingPath).asInt()).isEqualTo(5);
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

    Assertions.assertThat(initialIndex.at(replicaSettingPath).asInt()).isEqualTo(0);
    Assertions.assertThat(initialIndex.at(shardsSettingPath).asInt()).isEqualTo(1);

    // when
    config.index().setNumberOfReplicas(5);
    config.index().setNumberOfShards(5);

    schemaManager.startup();

    // then
    final var updatedIndex = searchClientAdapter.getIndexAsNode(index.getFullQualifiedName());

    Assertions.assertThat(updatedIndex.at(replicaSettingPath).asInt()).isEqualTo(5);
    Assertions.assertThat(updatedIndex.at(shardsSettingPath).asInt()).isEqualTo(1);
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

    Assertions.assertThat(initialTemplate.at(replicaSettingPath).asInt()).isEqualTo(0);
    Assertions.assertThat(initialTemplate.at(shardsSettingPath).asInt()).isEqualTo(1);

    when(indexTemplate.getMappingsClasspathFilename())
        .thenReturn("/mappings-settings-replica-and-shards.json");

    config.index().setNumberOfReplicas(5);
    config.index().setNumberOfShards(5);

    schemaManager.startup();

    final var updatedTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    Assertions.assertThat(updatedTemplate.at(replicaSettingPath).asInt()).isEqualTo(5);
    Assertions.assertThat(updatedTemplate.at(shardsSettingPath).asInt()).isEqualTo(5);
  }

  @TestTemplate
  void shouldHaveCorrectSchemaUpdatesWithMultipleExporters(
      final SearchEngineConfiguration config, final SearchClientAdapter clientAdapter)
      throws Exception {
    /*
    // given
    final var exporter1 = createExporter(Set.of(index), Set.of(indexTemplate), config);
    final var exporter2 = createExporter(Set.of(index), Set.of(indexTemplate), config);

    when(index.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");

    // when
    exporter1.open(new ExporterTestController());
    exporter2.open(new ExporterTestController());

    // then
    final var retrievedIndex = clientAdapter.getIndexAsNode(index.getFullQualifiedName());
    final var retrievedIndexTemplate =
        clientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    assertThat(
            SchemaTestUtil.mappingsMatch(
                retrievedIndex.get("mappings"), "/mappings-added-property.json"))
        .isTrue();
    assertThat(
            SchemaTestUtil.mappingsMatch(
                retrievedIndexTemplate.at("/index_template/template/mappings"),
                "/mappings-added-property.json"))
        .isTrue();
     */
  }
}
