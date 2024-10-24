/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import static io.camunda.exporter.schema.SchemaTestUtil.mappingsMatch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.exporter.schema.opensearch.OpensearchEngineClient;
import io.camunda.exporter.utils.SearchClientAdapter;
import io.camunda.exporter.utils.TestSupport;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class SchemaManagerIT {

  private static ExporterConfiguration config = new ExporterConfiguration();
  private static ElasticsearchClient elsClient;
  private static OpenSearchClient osClient;

  @Container
  private static final ElasticsearchContainer ELS_CONTAINER =
      TestSupport.createDefeaultElasticsearchContainer();

  @Container
  private static final OpensearchContainer<?> OPENSEARCH_CONTAINER =
      TestSupport.createDefaultOpensearchContainer();

  private IndexDescriptor index;
  private IndexTemplateDescriptor indexTemplate;

  @BeforeAll
  public static void init() {
    config.getConnect().setUrl(ELS_CONTAINER.getHttpHostAddress());
    elsClient = new ElasticsearchConnector(config.getConnect()).createClient();

    config.getConnect().setUrl(OPENSEARCH_CONTAINER.getHttpHostAddress());
    osClient = new OpensearchConnector(config.getConnect()).createClient();
  }

  @BeforeEach
  public void refresh() throws IOException {
    elsClient.indices().delete(req -> req.index("*"));
    elsClient.indices().deleteIndexTemplate(req -> req.name("*"));
    osClient.indices().delete(req -> req.index(config.getIndex().getPrefix() + "*"));
    osClient.indices().deleteIndexTemplate(req -> req.name("*"));
    config = new ExporterConfiguration();

    indexTemplate =
        SchemaTestUtil.mockIndexTemplate(
            "index_name",
            "test*",
            "template_alias",
            Collections.emptyList(),
            "template_name",
            "/mappings.json");

    index =
        SchemaTestUtil.mockIndex(
            config.getIndex().getPrefix() + "qualified_name",
            "alias",
            "index_name",
            "/mappings.json");

    when(indexTemplate.getFullQualifiedName())
        .thenReturn(config.getIndex().getPrefix() + "template_index_qualified_name");
  }

  static Stream<Arguments> providerParameters() {
    return Stream.of(
        Arguments.of(new SearchClientAdapter(elsClient), new ElasticsearchEngineClient(elsClient)),
        Arguments.of(new SearchClientAdapter(osClient), new OpensearchEngineClient(osClient)));
  }

  @ParameterizedTest
  @MethodSource("providerParameters")
  void shouldAppendToIndexMappingsWithNewProperties(
      final SearchClientAdapter searchClientAdapter, final SearchEngineClient searchEngineClient)
      throws Exception {
    // given
    final var schemaManager =
        new SchemaManager(searchEngineClient, Set.of(index), Set.of(), new ExporterConfiguration());

    schemaManager.initialiseResources();

    // when
    final var newProperties = new HashSet<IndexMappingProperty>();
    newProperties.add(new IndexMappingProperty("foo", Map.of("type", "text")));
    newProperties.add(new IndexMappingProperty("bar", Map.of("type", "keyword")));

    final Map<IndexDescriptor, Collection<IndexMappingProperty>> schemasToChange =
        Map.of(index, newProperties);

    schemaManager.updateSchema(schemasToChange);

    // then
    final var updatedIndex = searchClientAdapter.getIndexAsNode(index.getFullQualifiedName());

    assertThat(updatedIndex.at("/mappings/properties/foo/type").asText()).isEqualTo("text");
    assertThat(updatedIndex.at("/mappings/properties/bar/type").asText()).isEqualTo("keyword");
  }

  @ParameterizedTest
  @MethodSource("providerParameters")
  void shouldInheritDefaultSettingsIfNoIndexSpecificSettings(
      final SearchClientAdapter searchClientAdapter, final SearchEngineClient searchEngineClient)
      throws Exception {
    // given
    final var properties = new ExporterConfiguration();
    properties.getIndex().setNumberOfReplicas(10);
    properties.getIndex().setNumberOfShards(10);

    final var schemaManager =
        new SchemaManager(searchEngineClient, Set.of(index), Set.of(indexTemplate), properties);

    // when
    schemaManager.initialiseResources();

    // then
    final var retrievedIndex = searchClientAdapter.getIndexAsNode(index.getFullQualifiedName());

    assertThat(retrievedIndex.at("/settings/index/number_of_replicas").asInt()).isEqualTo(10);
    assertThat(retrievedIndex.at("/settings/index/number_of_shards").asInt()).isEqualTo(10);
  }

  @ParameterizedTest
  @MethodSource("providerParameters")
  void shouldUseIndexSpecificSettingsIfSpecified(
      final SearchClientAdapter searchClientAdapter, final SearchEngineClient searchEngineClient)
      throws Exception {
    // given
    final var properties = new ExporterConfiguration();
    properties.getIndex().setNumberOfReplicas(10);
    properties.getIndex().setNumberOfShards(10);
    properties.getIndex().setReplicasByIndexName(Map.of("index_name", 5));
    properties.getIndex().setShardsByIndexName(Map.of("index_name", 5));

    final var schemaManager =
        new SchemaManager(searchEngineClient, Set.of(index), Set.of(indexTemplate), properties);

    // when
    schemaManager.initialiseResources();

    // then
    final var retrievedIndex = searchClientAdapter.getIndexAsNode(index.getFullQualifiedName());

    assertThat(retrievedIndex.at("/settings/index/number_of_replicas").asInt()).isEqualTo(5);
    assertThat(retrievedIndex.at("/settings/index/number_of_shards").asInt()).isEqualTo(5);
  }

  @ParameterizedTest
  @MethodSource("providerParameters")
  private void shouldOverwriteIndexTemplateIfMappingsFileChanged(
      final SearchClientAdapter searchClientAdapter, final SearchEngineClient searchEngineClient)
      throws Exception {
    // given
    final var schemaManager =
        new SchemaManager(
            searchEngineClient, Set.of(), Set.of(indexTemplate), new ExporterConfiguration());

    schemaManager.initialiseResources();

    // when
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");

    final Map<IndexDescriptor, Collection<IndexMappingProperty>> schemasToChange =
        Map.of(indexTemplate, Set.of());
    schemaManager.updateSchema(schemasToChange);

    // then
    final var template =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    assertThat(
            mappingsMatch(
                template.at("/index_template/template/mappings"), "/mappings-added-property.json"))
        .isTrue();
  }

  @ParameterizedTest
  @MethodSource("providerParameters")
  void shouldCreateAllSchemasIfCreateEnabled(
      final SearchClientAdapter searchClientAdapter, final SearchEngineClient searchEngineClient)
      throws Exception {
    // given
    config.setCreateSchema(true);
    final var schemaManager =
        new SchemaManager(searchEngineClient, Set.of(index), Set.of(indexTemplate), config);

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

  @ParameterizedTest
  @MethodSource("providerParameters")
  void shouldUpdateSchemasCorrectlyIfCreateEnabled(
      final SearchClientAdapter searchClientAdapter, final SearchEngineClient searchEngineClient)
      throws Exception {
    // given
    config.setCreateSchema(true);
    final var schemaManager =
        new SchemaManager(searchEngineClient, Set.of(index), Set.of(indexTemplate), config);

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

  @ParameterizedTest
  @MethodSource("providerParameters")
  void shouldCreateNewSchemasIfNewIndexDescriptorAddedToExistingSchemas(
      final SearchClientAdapter searchClientAdapter, final SearchEngineClient searchEngineClient)
      throws Exception {
    // given
    config.setCreateSchema(true);
    final var indices = new HashSet<IndexDescriptor>();
    final var indexTemplates = new HashSet<IndexTemplateDescriptor>();

    indices.add(index);
    indexTemplates.add(indexTemplate);

    final var schemaManager =
        new SchemaManager(searchEngineClient, indices, indexTemplates, config);

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
        .thenReturn(config.getIndex().getPrefix() + "new_template_index_qualified_name");

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

  @ParameterizedTest
  @MethodSource("providerParameters")
  void shouldNotPutAnySchemasIfCreatedDisabled(
      final SearchClientAdapter searchClientAdapter, final SearchEngineClient searchEngineClient) {
    // given
    config.setCreateSchema(false);

    final var schemaManager =
        new SchemaManager(searchEngineClient, Set.of(index), Set.of(indexTemplate), config);

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

  @ParameterizedTest
  @MethodSource("providerParameters")
  void shouldCreateLifeCyclePoliciesOnStartupIfEnabled(
      final SearchClientAdapter searchClientAdapter, final SearchEngineClient searchEngineClient)
      throws Exception {
    config.setCreateSchema(true);
    config.getRetention().setEnabled(true);
    config.getRetention().setPolicyName("policy_name");

    final var schemaManager = new SchemaManager(searchEngineClient, Set.of(), Set.of(), config);

    schemaManager.startup();

    final var policy = searchClientAdapter.getPolicyAsNode("policy_name");

    assertThat(policy.get("policy")).isNotNull();
  }

  @ParameterizedTest
  @MethodSource("providerParameters")
  void shouldCreateIndexInAdditionToTemplateFromTemplateDescriptor(
      final SearchClientAdapter searchClientAdapter, final SearchEngineClient searchEngineClient)
      throws Exception {
    config.setCreateSchema(true);

    final var schemaManager =
        new SchemaManager(searchEngineClient, Set.of(), Set.of(indexTemplate), config);

    schemaManager.startup();

    final var retrievedIndex =
        searchClientAdapter.getIndexAsNode(indexTemplate.getFullQualifiedName());

    assertThat(retrievedIndex.at("/settings/index/provided_name").asText())
        .isEqualTo(indexTemplate.getFullQualifiedName());
  }
}
