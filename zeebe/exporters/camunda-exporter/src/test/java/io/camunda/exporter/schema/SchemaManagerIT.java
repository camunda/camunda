/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import static io.camunda.exporter.schema.SchemaTestUtil.elsIndexTemplateToNode;
import static io.camunda.exporter.schema.SchemaTestUtil.elsIndexToNode;
import static io.camunda.exporter.schema.SchemaTestUtil.mappingsMatch;
import static io.camunda.exporter.schema.SchemaTestUtil.opensearchIndexTemplateToNode;
import static io.camunda.exporter.schema.SchemaTestUtil.opensearchIndexToNode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
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
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class SchemaManagerIT {

  private static final ExporterConfiguration CONFIG = new ExporterConfiguration();
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
            CONFIG.getIndex().getPrefix() + "qualified_name",
            "alias",
            "index_name",
            "/mappings.json");

    when(indexTemplate.getFullQualifiedName())
        .thenReturn(CONFIG.getIndex().getPrefix() + "template_index_qualified_name");
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
      CONFIG.getConnect().setUrl(CONTAINER.getHttpHostAddress());
      elsClient = new ElasticsearchConnector(CONFIG.getConnect()).createClient();

      searchEngineClient = new ElasticsearchEngineClient(elsClient);
    }

    @Test
    void shouldInheritDefaultSettingsIfNoIndexSpecificSettings() throws Exception {
      SchemaManagerIT.this.shouldInheritDefaultSettingsIfNoIndexSpecificSettings(
          () -> getIndexAsNode(index.getFullQualifiedName()), searchEngineClient);
    }

    @Test
    void shouldUseIndexSpecificSettingsIfSpecified() throws Exception {
      SchemaManagerIT.this.shouldUseIndexSpecificSettingsIfSpecified(
          () -> getIndexAsNode(index.getFullQualifiedName()), searchEngineClient);
    }

    @Test
    void shouldOverwriteIndexTemplateIfMappingsFileChanged() throws Exception {
      SchemaManagerIT.this.shouldOverwriteIndexTemplateIfMappingsFileChanged(
          () -> getIndexTemplateAsNode(indexTemplate.getTemplateName()), searchEngineClient);
    }

    @Test
    void shouldAppendToIndexMappingsWithNewProperties() throws Exception {
      SchemaManagerIT.this.shouldAppendToIndexMappingsWithNewProperties(
          () -> getIndexAsNode(index.getFullQualifiedName()), searchEngineClient);
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

    public JsonNode getIndexAsNode(final String indexName) throws IOException {
      final var updatedIndex = elsClient.indices().get(req -> req.index(indexName)).get(indexName);

      return elsIndexToNode(updatedIndex);
    }

    public JsonNode getIndexTemplateAsNode(final String templateName) throws IOException {
      final var template =
          elsClient
              .indices()
              .getIndexTemplate(req -> req.name(templateName))
              .indexTemplates()
              .getFirst();

      return elsIndexTemplateToNode(template);
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
      opensearchClient.indices().delete(req -> req.index("*"));
      opensearchClient.indices().deleteIndexTemplate(req -> req.name("*"));
    }

    @BeforeAll
    public static void init() {
      // Create the low-level client
      CONFIG.getConnect().setUrl(CONTAINER.getHttpHostAddress());
      opensearchClient = new OpensearchConnector(CONFIG.getConnect()).createClient();

      searchEngineClient = new OpensearchEngineClient(opensearchClient);
    }

    @Test
    void shouldInheritDefaultSettingsIfNoIndexSpecificSettings() throws Exception {
      SchemaManagerIT.this.shouldInheritDefaultSettingsIfNoIndexSpecificSettings(
          () -> getIndexAsNode(index.getFullQualifiedName()), searchEngineClient);
    }

    @Test
    void shouldUseIndexSpecificSettingsIfSpecified() throws Exception {
      SchemaManagerIT.this.shouldUseIndexSpecificSettingsIfSpecified(
          () -> getIndexAsNode(index.getFullQualifiedName()), searchEngineClient);
    }

    @Test
    void shouldOverwriteIndexTemplateIfMappingsFileChanged() throws Exception {

      SchemaManagerIT.this.shouldOverwriteIndexTemplateIfMappingsFileChanged(
          () -> getIndexTemplateAsNode(indexTemplate.getTemplateName()), searchEngineClient);
    }

    @Test
    void shouldAppendToIndexMappingsWithNewProperties() throws Exception {
      SchemaManagerIT.this.shouldAppendToIndexMappingsWithNewProperties(
          () -> getIndexAsNode(index.getFullQualifiedName()), searchEngineClient);
    }
    }

    private JsonNode getIndexAsNode(final String indexName) throws IOException {
      final var updatedIndex =
          opensearchClient.indices().get(req -> req.index(indexName)).get(indexName);

      return opensearchIndexToNode(updatedIndex);
    }

    private JsonNode getIndexTemplateAsNode(final String templateName) throws IOException {
      final var template =
          opensearchClient
              .indices()
              .getIndexTemplate(req -> req.name(templateName))
              .indexTemplates()
              .getFirst();

      return opensearchIndexTemplateToNode(template);
    }
  }
}
