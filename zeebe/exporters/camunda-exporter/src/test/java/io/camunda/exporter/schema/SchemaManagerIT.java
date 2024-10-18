/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import static io.camunda.exporter.schema.SchemaTestUtil.elsPolicyToNode;
import static io.camunda.exporter.schema.SchemaTestUtil.getElsIndexAsNode;
import static io.camunda.exporter.schema.SchemaTestUtil.getElsIndexTemplateAsNode;
import static io.camunda.exporter.schema.SchemaTestUtil.getOpensearchIndexAsNode;
import static io.camunda.exporter.schema.SchemaTestUtil.getOpensearchIndexTemplateAsNode;
import static io.camunda.exporter.schema.SchemaTestUtil.mappingsMatch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.exporter.schema.opensearch.OpensearchEngineClient;
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
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class SchemaManagerIT {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static ExporterConfiguration config = new ExporterConfiguration();
  private IndexDescriptor index;
  private IndexTemplateDescriptor indexTemplate;

  @BeforeEach
  public void refresh() throws IOException {
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

    config = new ExporterConfiguration();
  }

  private void shouldAppendToIndexMappingsWithNewProperties(
      final Callable<JsonNode> getUpdatedIndex, final SearchEngineClient searchEngineClient)
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
    final var updatedIndex = getUpdatedIndex.call();

    assertThat(updatedIndex.at("/mappings/properties/foo/type").asText()).isEqualTo("text");
    assertThat(updatedIndex.at("/mappings/properties/bar/type").asText()).isEqualTo("keyword");
  }

  private void shouldInheritDefaultSettingsIfNoIndexSpecificSettings(
      final Callable<JsonNode> getRetrievedIndex, final SearchEngineClient searchEngineClient)
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
    final var retrievedIndex = getRetrievedIndex.call();

    assertThat(retrievedIndex.at("/settings/index/number_of_replicas").asInt()).isEqualTo(10);
    assertThat(retrievedIndex.at("/settings/index/number_of_shards").asInt()).isEqualTo(10);
  }

  private void shouldUseIndexSpecificSettingsIfSpecified(
      final Callable<JsonNode> getRetrievedIndex, final SearchEngineClient searchEngineClient)
      throws Exception {
    // given
    final var properties = new ExporterConfiguration();
    properties.getIndex().setNumberOfReplicas(10);
    properties.getIndex().setNumberOfShards(10);
    properties.setReplicasByIndexName(Map.of("index_name", 5));
    properties.setShardsByIndexName(Map.of("index_name", 5));

    final var schemaManager =
        new SchemaManager(searchEngineClient, Set.of(index), Set.of(indexTemplate), properties);

    // when
    schemaManager.initialiseResources();

    // then
    final var retrievedIndex = getRetrievedIndex.call();

    assertThat(retrievedIndex.at("/settings/index/number_of_replicas").asInt()).isEqualTo(5);
    assertThat(retrievedIndex.at("/settings/index/number_of_shards").asInt()).isEqualTo(5);
  }

  private void shouldOverwriteIndexTemplateIfMappingsFileChanged(
      final Callable<JsonNode> getTemplate, final SearchEngineClient searchEngineClient)
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
    final var template = getTemplate.call();

    assertThat(
            mappingsMatch(
                template.at("/index_template/template/mappings"), "/mappings-added-property.json"))
        .isTrue();
  }

  void shouldCreateAllSchemasIfCreateEnabled(
      final Callable<JsonNode> getIndex,
      final Callable<JsonNode> getTemplate,
      final SearchEngineClient searchEngineClient)
      throws Exception {
    // given
    config.setCreateSchema(true);
    final var schemaManager =
        new SchemaManager(searchEngineClient, Set.of(index), Set.of(indexTemplate), config);

    // when
    schemaManager.startup();

    // then
    final var retrievedIndex = getIndex.call();
    final var retrievedIndexTemplate = getTemplate.call();

    assertThat(mappingsMatch(retrievedIndex.get("mappings"), "/mappings.json")).isTrue();
    assertThat(
            mappingsMatch(
                retrievedIndexTemplate.at("/index_template/template/mappings"), "/mappings.json"))
        .isTrue();
  }

  void shouldUpdateSchemasCorrectlyIfCreateEnabled(
      final Callable<JsonNode> getIndex,
      final Callable<JsonNode> getTemplate,
      final SearchEngineClient searchEngineClient)
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
    final var retrievedIndex = getIndex.call();
    final var retrievedIndexTemplate = getTemplate.call();

    assertThat(mappingsMatch(retrievedIndex.get("mappings"), "/mappings-added-property.json"))
        .isTrue();
    assertThat(
            mappingsMatch(
                retrievedIndexTemplate.at("/index_template/template/mappings"),
                "/mappings-added-property.json"))
        .isTrue();
  }

  void shouldCreateNewSchemasIfNewIndexDescriptorAddedToExistingSchemas(
      final Callable<JsonNode> getIndex,
      final Callable<JsonNode> getTemplate,
      final SearchEngineClient searchEngineClient)
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
    final var retrievedNewIndex = getIndex.call();
    final var retrievedNewTemplate = getTemplate.call();

    assertThat(mappingsMatch(retrievedNewIndex.get("mappings"), "/mappings-added-property.json"))
        .isTrue();
    assertThat(
            mappingsMatch(
                retrievedNewTemplate.at("/index_template/template/mappings"),
                "/mappings-added-property.json"))
        .isTrue();
  }

  void shouldNotPutAnySchemasIfCreatedDisabled(
      final Callable<JsonNode> getIndex,
      final Callable<JsonNode> getTemplate,
      final SearchEngineClient searchEngineClient) {
    // given
    config.setCreateSchema(false);

    final var schemaManager =
        new SchemaManager(searchEngineClient, Set.of(index), Set.of(indexTemplate), config);

    schemaManager.startup();

    // then
    assertThatThrownBy(getIndex::call)
        .isInstanceOfAny(ElasticsearchException.class, OpenSearchException.class)
        .hasMessageContaining("no such index");
    assertThatThrownBy(getTemplate::call)
        .isInstanceOfAny(ElasticsearchException.class, OpenSearchException.class)
        .hasMessageContaining(String.format("[%s] not found", indexTemplate.getTemplateName()));
  }

  void shouldCreateLifeCyclePoliciesOnStartupIfEnabled(
      final Callable<JsonNode> getPolicy, final SearchEngineClient searchEngineClient)
      throws Exception {
    config.setCreateSchema(true);
    config.getRetention().setEnabled(true);
    config.getRetention().setPolicyName("policy_name");

    final var schemaManager = new SchemaManager(searchEngineClient, Set.of(), Set.of(), config);

    schemaManager.startup();

    final var policy = getPolicy.call();

    assertThat(policy.get("policy")).isNotNull();
  }

  void shouldCreateIndexInAdditionToTemplateFromTemplateDescriptor(
      final Callable<JsonNode> getIndex, final SearchEngineClient searchEngineClient)
      throws Exception {
    config.setCreateSchema(true);

    final var schemaManager =
        new SchemaManager(searchEngineClient, Set.of(), Set.of(indexTemplate), config);

    schemaManager.startup();

    final var retrievedIndex = getIndex.call();

    assertThat(retrievedIndex.at("/settings/index/provided_name").asText())
        .isEqualTo(indexTemplate.getFullQualifiedName());
  }

  @Nested
  class ElasticsearchSchemaManagerTest {
    private static ElasticsearchClient elsClient;
    private static SearchEngineClient searchEngineClient;

    @Container
    private static final ElasticsearchContainer CONTAINER =
        TestSupport.createDefeaultElasticsearchContainer();

    @BeforeEach
    public void beforeEach() throws IOException {
      elsClient.indices().delete(req -> req.index("*"));
      elsClient.indices().deleteIndexTemplate(req -> req.name("*"));
    }

    @BeforeAll
    public static void init() {
      config.getConnect().setUrl(CONTAINER.getHttpHostAddress());
      elsClient = new ElasticsearchConnector(config.getConnect()).createClient();

      searchEngineClient = new ElasticsearchEngineClient(elsClient);
    }

    @Test
    void shouldInheritDefaultSettingsIfNoIndexSpecificSettings() throws Exception {
      SchemaManagerIT.this.shouldInheritDefaultSettingsIfNoIndexSpecificSettings(
          () -> getElsIndexAsNode(index.getFullQualifiedName(), elsClient), searchEngineClient);
    }

    @Test
    void shouldUseIndexSpecificSettingsIfSpecified() throws Exception {
      SchemaManagerIT.this.shouldUseIndexSpecificSettingsIfSpecified(
          () -> getElsIndexAsNode(index.getFullQualifiedName(), elsClient), searchEngineClient);
    }

    @Test
    void shouldOverwriteIndexTemplateIfMappingsFileChanged() throws Exception {
      SchemaManagerIT.this.shouldOverwriteIndexTemplateIfMappingsFileChanged(
          () -> getElsIndexTemplateAsNode(indexTemplate.getTemplateName(), elsClient),
          searchEngineClient);
    }

    @Test
    void shouldAppendToIndexMappingsWithNewProperties() throws Exception {
      SchemaManagerIT.this.shouldAppendToIndexMappingsWithNewProperties(
          () -> getElsIndexAsNode(index.getFullQualifiedName(), elsClient), searchEngineClient);
    }

    @Test
    void shouldCreateAllSchemasIfCreateEnabled() throws Exception {
      SchemaManagerIT.this.shouldCreateAllSchemasIfCreateEnabled(
          () -> getElsIndexAsNode(index.getFullQualifiedName(), elsClient),
          () -> getElsIndexTemplateAsNode(indexTemplate.getTemplateName(), elsClient),
          searchEngineClient);
    }

    @Test
    void shouldUpdateAllSchemasIfUpdateEnabled() throws Exception {
      shouldUpdateSchemasCorrectlyIfCreateEnabled(
          () -> getElsIndexAsNode(index.getFullQualifiedName(), elsClient),
          () -> getElsIndexTemplateAsNode(indexTemplate.getTemplateName(), elsClient),
          searchEngineClient);
    }

    @Test
    void shouldCreateNewSchemasIfNewIndexDescriptorAddedToExistingSchemas() throws Exception {
      SchemaManagerIT.this.shouldCreateNewSchemasIfNewIndexDescriptorAddedToExistingSchemas(
          () -> getElsIndexAsNode("new_index_qualified", elsClient),
          () -> getElsIndexTemplateAsNode("new_template_name", elsClient),
          searchEngineClient);
    }

    @Test
    void shouldNotPutAnySchemasIfCreatedDisabled() {
      SchemaManagerIT.this.shouldNotPutAnySchemasIfCreatedDisabled(
          () -> getElsIndexAsNode(index.getFullQualifiedName(), elsClient),
          () -> getElsIndexTemplateAsNode(indexTemplate.getTemplateName(), elsClient),
          searchEngineClient);
    }

    @Test
    void shouldCreateLifeCyclePoliciesOnStartupIfEnabled() throws Exception {
      SchemaManagerIT.this.shouldCreateLifeCyclePoliciesOnStartupIfEnabled(
          () -> getPolicy(config.getRetention().getPolicyName()), searchEngineClient);
    }

    @Test
    void shouldCreateIndexInAdditionToTemplateFromTemplateDescriptor() throws Exception {
      SchemaManagerIT.this.shouldCreateIndexInAdditionToTemplateFromTemplateDescriptor(
          () -> getElsIndexAsNode(indexTemplate.getFullQualifiedName(), elsClient),
          searchEngineClient);
    }

    @Test
    void shouldReadIndexMappingsFileCorrectly() {
      // given
      final var index =
          SchemaTestUtil.mockIndex("index_name", "alias", "index_name", "/mappings.json");

      // when
      final var indexMapping = IndexMapping.from(index, new ObjectMapper());

      // then
      assertThat(indexMapping.dynamic()).isEqualTo("strict");

      assertThat(indexMapping.properties())
          .containsExactlyInAnyOrder(
              new IndexMappingProperty.Builder()
                  .name("hello")
                  .typeDefinition(Map.of("type", "text"))
                  .build(),
              new IndexMappingProperty.Builder()
                  .name("world")
                  .typeDefinition(Map.of("type", "keyword"))
                  .build());
    }

    private JsonNode getPolicy(final String policyName) throws IOException {
      final var policy =
          elsClient.ilm().getLifecycle(req -> req.name(policyName)).result().get(policyName);

      return elsPolicyToNode(policy);
    }
  }

  @Nested
  class OpensearchSchemaManagerTest {
    @Container
    private static final OpensearchContainer<?> CONTAINER =
        TestSupport.createDefaultOpensearchContainer();

    private static OpenSearchClient opensearchClient;
    private static SearchEngineClient searchEngineClient;

    @BeforeEach
    public void beforeEach() throws IOException {
      opensearchClient.indices().delete(req -> req.index(config.getIndex().getPrefix() + "*"));
      opensearchClient.indices().deleteIndexTemplate(req -> req.name("*"));
    }

    @BeforeAll
    public static void init() {
      // Create the low-level client
      config.getConnect().setUrl(CONTAINER.getHttpHostAddress());
      opensearchClient = new OpensearchConnector(config.getConnect()).createClient();

      searchEngineClient = new OpensearchEngineClient(opensearchClient);
    }

    @Test
    void shouldInheritDefaultSettingsIfNoIndexSpecificSettings() throws Exception {
      SchemaManagerIT.this.shouldInheritDefaultSettingsIfNoIndexSpecificSettings(
          () -> getOpensearchIndexAsNode(index.getFullQualifiedName(), opensearchClient),
          searchEngineClient);
    }

    @Test
    void shouldUseIndexSpecificSettingsIfSpecified() throws Exception {
      SchemaManagerIT.this.shouldUseIndexSpecificSettingsIfSpecified(
          () -> getOpensearchIndexAsNode(index.getFullQualifiedName(), opensearchClient),
          searchEngineClient);
    }

    @Test
    void shouldOverwriteIndexTemplateIfMappingsFileChanged() throws Exception {

      SchemaManagerIT.this.shouldOverwriteIndexTemplateIfMappingsFileChanged(
          () -> getOpensearchIndexTemplateAsNode(indexTemplate.getTemplateName(), opensearchClient),
          searchEngineClient);
    }

    @Test
    void shouldAppendToIndexMappingsWithNewProperties() throws Exception {
      SchemaManagerIT.this.shouldAppendToIndexMappingsWithNewProperties(
          () -> getOpensearchIndexAsNode(index.getFullQualifiedName(), opensearchClient),
          searchEngineClient);
    }

    @Test
    void shouldCreateAllSchemasIfCreateEnabled() throws Exception {
      SchemaManagerIT.this.shouldCreateAllSchemasIfCreateEnabled(
          () -> getOpensearchIndexAsNode(index.getFullQualifiedName(), opensearchClient),
          () -> getOpensearchIndexTemplateAsNode(indexTemplate.getTemplateName(), opensearchClient),
          searchEngineClient);
    }

    @Test
    void shouldUpdateAllSchemasIfUpdateEnabled() throws Exception {
      shouldUpdateSchemasCorrectlyIfCreateEnabled(
          () -> getOpensearchIndexAsNode(index.getFullQualifiedName(), opensearchClient),
          () -> getOpensearchIndexTemplateAsNode(indexTemplate.getTemplateName(), opensearchClient),
          searchEngineClient);
    }

    @Test
    void shouldCreateNewSchemasIfNewIndexDescriptorAddedToExistingSchemas() throws Exception {
      SchemaManagerIT.this.shouldCreateNewSchemasIfNewIndexDescriptorAddedToExistingSchemas(
          () -> getOpensearchIndexAsNode("new_index_qualified", opensearchClient),
          () -> getOpensearchIndexTemplateAsNode("new_template_name", opensearchClient),
          searchEngineClient);
    }

    @Test
    void shouldNotPutAnySchemasIfCreatedDisabled() {
      SchemaManagerIT.this.shouldNotPutAnySchemasIfCreatedDisabled(
          () -> getOpensearchIndexAsNode(index.getFullQualifiedName(), opensearchClient),
          () -> getOpensearchIndexTemplateAsNode(indexTemplate.getTemplateName(), opensearchClient),
          searchEngineClient);
    }

    @Test
    void shouldCreateLifeCyclePoliciesOnStartupIfEnabled() throws Exception {
      SchemaManagerIT.this.shouldCreateLifeCyclePoliciesOnStartupIfEnabled(
          () -> getPolicy(config.getRetention().getPolicyName()), searchEngineClient);
    }

    @Test
    void shouldCreateIndexInAdditionToTemplateFromTemplateDescriptor() throws Exception {
      SchemaManagerIT.this.shouldCreateIndexInAdditionToTemplateFromTemplateDescriptor(
          () -> getOpensearchIndexAsNode(indexTemplate.getFullQualifiedName(), opensearchClient),
          searchEngineClient);
    }

    private JsonNode getPolicy(final String policyName) throws IOException {
      final var request =
          Requests.builder().method("GET").endpoint("_plugins/_ism/policies/" + policyName).build();

      return MAPPER.readTree(opensearchClient.generic().execute(request).getBody().get().body());
    }
  }
}
