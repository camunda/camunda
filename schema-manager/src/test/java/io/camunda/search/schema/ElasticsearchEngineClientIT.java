/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static io.camunda.search.schema.utils.SchemaTestUtil.createTestIndexDescriptor;
import static io.camunda.search.schema.utils.SchemaTestUtil.createTestTemplateDescriptor;
import static io.camunda.search.schema.utils.SchemaTestUtil.validateMappings;
import static io.camunda.search.test.utils.SearchDBExtension.ENGINE_CLIENT_TEST_MARKERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.PutIndexTemplateRequest;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.search.schema.utils.SchemaTestUtil;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class ElasticsearchEngineClientIT {
  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  private static ElasticsearchClient elsClient;
  private static ElasticsearchEngineClient elsEngineClient;

  @BeforeAll
  public static void init() {
    // Create the low-level client
    final var config = new ConnectConfiguration();
    config.setUrl(CONTAINER.getHttpHostAddress());
    final var esConnector = new ElasticsearchConnector(config);
    elsClient = esConnector.createClient();
    elsEngineClient = new ElasticsearchEngineClient(elsClient, esConnector.objectMapper());
  }

  @BeforeEach
  public void refresh() throws IOException {
    elsClient.indices().delete(req -> req.index("*"));
    elsClient.indices().deleteIndexTemplate(req -> req.name("*"));
  }

  @Test
  void shouldPutMappingCorrectly() throws IOException {
    // given
    final var indexName = "test";
    final var indexAlias = "test_alias";
    elsClient
        .indices()
        .create(req -> req.index(indexName).aliases(indexAlias, a -> a.isWriteIndex(false)));

    final var descriptor = mock(IndexDescriptor.class);
    doReturn(indexAlias).when(descriptor).getAlias();

    final Set<IndexMappingProperty> newProperties = new HashSet<>();
    newProperties.add(new IndexMappingProperty("email", Map.of("type", "keyword")));

    // when
    elsEngineClient.putMapping(descriptor, newProperties);

    // then
    final var index = elsClient.indices().get(req -> req.index(indexName)).get(indexName);

    assertThat(index.mappings().properties().get("email").isKeyword()).isTrue();
  }

  @Test
  void shouldCreateIndexTemplateCorrectly() throws IOException {
    // given, when
    final var indexTemplate = createTestTemplateDescriptor("template_name", "/mappings.json");

    final var settings = new IndexConfiguration();
    elsEngineClient.createIndexTemplate(indexTemplate, settings, true);

    // then
    final var indexTemplates =
        elsClient
            .indices()
            .getIndexTemplate(req -> req.name(indexTemplate.getTemplateName()))
            .indexTemplates();

    assertThat(indexTemplates.size()).isEqualTo(1);
    validateMappings(
        indexTemplates.getFirst().indexTemplate().template().mappings(), "/mappings.json");
  }

  @Test
  void shouldNotThrowIfTryingToCreateExistingTemplate() {
    // given
    final var indexTemplate = createTestTemplateDescriptor("template_name", "/mappings.json");

    final var settings = new IndexConfiguration();
    elsEngineClient.createIndexTemplate(indexTemplate, settings, true);

    // when, then
    assertThatNoException()
        .describedAs("Creating an already existing template should not throw")
        .isThrownBy(() -> elsEngineClient.createIndexTemplate(indexTemplate, settings, true));
  }

  @Test
  void shouldCreateIndexCorrectly() throws IOException {
    // given
    final var descriptor = createTestIndexDescriptor("index_name", "/mappings.json");

    // when
    elsEngineClient.createIndex(descriptor, new IndexConfiguration());

    // then
    final var index =
        elsClient
            .indices()
            .get(req -> req.index(descriptor.getFullQualifiedName()))
            .get(descriptor.getFullQualifiedName());

    validateMappings(index.mappings(), "/mappings.json");
  }

  @Test
  void shouldRetrieveAllIndexMappingsWithImplementationAgnosticReturnType() {
    final var index1 = createTestIndexDescriptor("index_name", "/mappings-complex-property.json");
    final var index2 = createTestIndexDescriptor("index_dynamic", "/mappings-default-dynamic.json");

    elsEngineClient.createIndex(index1, new IndexConfiguration());
    elsEngineClient.createIndex(index2, new IndexConfiguration());

    final var mappings = elsEngineClient.getMappings("*", MappingSource.INDEX);

    assertThat(mappings.size()).isEqualTo(2);
    assertThat(mappings.get(index1.getFullQualifiedName()).dynamic()).isEqualTo("strict");
    assertThat(mappings.get(index2.getFullQualifiedName()).dynamic()).isEqualTo("true");

    assertThat(mappings.get(index1.getFullQualifiedName()).properties())
        .containsExactlyInAnyOrder(
            new IndexMappingProperty.Builder()
                .name("hello")
                .typeDefinition(
                    Map.of("type", "text", "index", false, "eager_global_ordinals", true))
                .build(),
            new IndexMappingProperty.Builder()
                .name("world")
                .typeDefinition(Map.of("type", "keyword"))
                .build());
    assertThat(mappings.get(index2.getFullQualifiedName()).properties())
        .containsExactlyInAnyOrder(
            new IndexMappingProperty.Builder()
                .name("hello")
                .typeDefinition(Map.of("type", "text"))
                .build());
  }

  @Test
  void shouldNotThrowErrorIfRetrievingMappingsWhereOnlySubsetOfIndicesExist() {
    // given
    final var index = createTestIndexDescriptor("index_name", "/mappings.json");

    elsEngineClient.createIndex(index, new IndexConfiguration());

    // when, tnen
    assertThatNoException()
        .isThrownBy(
            () ->
                elsEngineClient.getMappings(
                    index.getFullQualifiedName() + "*,foo*", MappingSource.INDEX));
  }

  @Test
  void shouldRetrieveAllIndexTemplateMappingsWithImplementationAgnosticReturnType() {
    final var template =
        createTestTemplateDescriptor("template_name", "/mappings-complex-property.json");

    elsEngineClient.createIndexTemplate(template, new IndexConfiguration(), true);

    final var templateMappings =
        elsEngineClient.getMappings(template.getTemplateName(), MappingSource.INDEX_TEMPLATE);

    assertThat(templateMappings.size()).isEqualTo(1);
    assertThat(templateMappings.get(template.getTemplateName()).properties())
        .containsExactlyInAnyOrder(
            new IndexMappingProperty.Builder()
                .name("hello")
                .typeDefinition(
                    Map.of("type", "text", "index", false, "eager_global_ordinals", true))
                .build(),
            new IndexMappingProperty.Builder()
                .name("world")
                .typeDefinition(Map.of("type", "keyword"))
                .build());
  }

  @Test
  void shouldCreateIndexTemplateIfSourceFileContainsSettings() throws IOException {
    final var template =
        createTestTemplateDescriptor("template_name", "/mappings-and-settings.json");

    elsEngineClient.createIndexTemplate(template, new IndexConfiguration(), true);

    final var createdTemplate =
        elsClient
            .indices()
            .getIndexTemplate(req -> req.name(template.getTemplateName()))
            .indexTemplates();

    assertThat(createdTemplate.size()).isEqualTo(1);
    assertThat(
            createdTemplate
                .getFirst()
                .indexTemplate()
                .template()
                .settings()
                .index()
                .refreshInterval()
                .time())
        .isEqualTo("2s");
  }

  @Test
  void shouldUpdateSettingsWithPutSettingsRequest() throws IOException {
    final var index = createTestIndexDescriptor("index_name", "/mappings.json");

    elsEngineClient.createIndex(index, new IndexConfiguration());

    final Map<String, String> newSettings = Map.of("index.lifecycle.name", "test");
    elsEngineClient.putSettings(List.of(index), newSettings);

    final var indices = elsClient.indices().get(req -> req.index(index.getFullQualifiedName()));

    assertThat(indices.result().size()).isEqualTo(1);
    assertThat(
            indices
                .result()
                .get(index.getFullQualifiedName())
                .settings()
                .index()
                .lifecycle()
                .name())
        .isEqualTo("test");
  }

  @Test
  void shouldSetReplicasAndShardsFromConfigurationDuringIndexCreation() throws IOException {
    final var index = createTestIndexDescriptor("index_name", "/mappings.json");

    final var settings = new IndexConfiguration();
    settings.setNumberOfReplicas(5);
    settings.setNumberOfShards(10);
    elsEngineClient.createIndex(index, settings);

    final var indices = elsClient.indices().get(req -> req.index(index.getFullQualifiedName()));

    assertThat(indices.result().size()).isEqualTo(1);
    assertThat(
            indices
                .result()
                .get(index.getFullQualifiedName())
                .settings()
                .index()
                .numberOfReplicas())
        .isEqualTo("5");
    assertThat(
            indices.result().get(index.getFullQualifiedName()).settings().index().numberOfShards())
        .isEqualTo("10");
  }

  @Test
  void shouldCreateIndexLifeCyclePolicy() throws IOException {
    elsEngineClient.putIndexLifeCyclePolicy("policy_name", "20d");

    final var policy = elsClient.ilm().getLifecycle(req -> req.name("policy_name"));

    assertThat(policy.result().size()).isEqualTo(1);
    assertThat(policy.result().get("policy_name").policy().phases().delete().minAge().time())
        .isEqualTo("20d");
    assertThat(policy.result().get("policy_name").policy().phases().delete().actions()).isNotNull();
  }

  @Test
  void shouldAccountForAllPropertyFieldsWhenGetMappings() {
    final var index = createTestIndexDescriptor("index_name", "/mappings-complex-property.json");

    elsEngineClient.createIndex(index, new IndexConfiguration());

    final var mappings = elsEngineClient.getMappings("*", MappingSource.INDEX);
    assertThat(mappings.size()).isEqualTo(1);

    final var createdIndexMappings = mappings.get(index.getFullQualifiedName());
    final Map<String, Object> complexProperty =
        (Map<String, Object>) createdIndexMappings.toMap().get("hello");
    assertThat(complexProperty.get("type")).isEqualTo("text");
    assertThat(complexProperty.get("index")).isEqualTo(false);
    assertThat(complexProperty.get("eager_global_ordinals")).isEqualTo(true);
  }

  @Test
  void shouldNotIssuePutIndexTemplateWhenSettingsUnchanged() throws IOException {
    // given
    final var template = createTestTemplateDescriptor("template_no_change", "/mappings.json");
    final var initialSettings = new IndexConfiguration();
    initialSettings.setNumberOfReplicas(0);
    initialSettings.setNumberOfShards(1);
    initialSettings.setTemplatePriority(50);

    final var indicesSpy = spy(elsClient.indices());
    final var clientSpy = spy(elsClient);
    doReturn(indicesSpy).when(clientSpy).indices();
    final var engineClient =
        new ElasticsearchEngineClient(clientSpy, TestObjectMapper.objectMapper());

    engineClient.createIndexTemplate(template, initialSettings, true);
    reset(indicesSpy); // ignore create

    // when
    engineClient.updateIndexTemplateSettings(template, initialSettings);

    // then
    verify(indicesSpy, never()).putIndexTemplate(any(PutIndexTemplateRequest.class));
  }

  @Test
  void shouldIssuePutIndexTemplateWhenSettingsChanged() throws IOException {
    // given
    final var template = createTestTemplateDescriptor("template_change", "/mappings.json");
    final var initialSettings = new IndexConfiguration();
    initialSettings.setNumberOfReplicas(0);
    initialSettings.setNumberOfShards(1);

    final var indicesSpy = spy(elsClient.indices());
    final var clientSpy = spy(elsClient);
    doReturn(indicesSpy).when(clientSpy).indices();
    final var engineClient =
        new ElasticsearchEngineClient(clientSpy, TestObjectMapper.objectMapper());

    engineClient.createIndexTemplate(template, initialSettings, true);
    reset(indicesSpy); // ignore create

    final var updated = new IndexConfiguration();
    updated.setNumberOfReplicas(2); // change
    updated.setNumberOfShards(1); // same

    // when
    engineClient.updateIndexTemplateSettings(template, updated);

    // then
    verify(indicesSpy, times(1)).putIndexTemplate(any(PutIndexTemplateRequest.class));
  }

  @Test
  void shouldIssuePutIndexTemplateWhenTemplatePriorityChanged() throws IOException {
    // given
    final var template = createTestTemplateDescriptor("template_change", "/mappings.json");
    final var initialSettings = new IndexConfiguration();
    initialSettings.setNumberOfReplicas(0);
    initialSettings.setNumberOfShards(1);
    initialSettings.setTemplatePriority(50);

    final var indicesSpy = spy(elsClient.indices());
    final var clientSpy = spy(elsClient);
    doReturn(indicesSpy).when(clientSpy).indices();
    final var engineClient =
        new ElasticsearchEngineClient(clientSpy, TestObjectMapper.objectMapper());

    engineClient.createIndexTemplate(template, initialSettings, true);
    reset(indicesSpy); // ignore create

    final var updated = new IndexConfiguration();
    updated.setNumberOfReplicas(0); // same
    updated.setNumberOfShards(1); // same
    updated.setTemplatePriority(100); // change

    // when
    engineClient.updateIndexTemplateSettings(template, updated);

    // then
    verify(indicesSpy, times(1)).putIndexTemplate(any(PutIndexTemplateRequest.class));
  }

  @Test
  void shouldCreateIndexWithMetaNormally() throws IOException {
    // given
    final var descriptor =
        createTestIndexDescriptor(
            "index_name-" + ENGINE_CLIENT_TEST_MARKERS, "/mappings_with_meta.json");

    // when
    final var indexSettings = new IndexConfiguration();
    elsEngineClient.createIndex(descriptor, indexSettings);

    // then
    final var index =
        elsClient
            .indices()
            .get(req -> req.index(descriptor.getFullQualifiedName()))
            .get(descriptor.getFullQualifiedName());

    SchemaTestUtil.validateMappings(index.mappings(), "/mappings_with_meta.json");

    assertThat(index.mappings().meta()).isNotEmpty().containsKey("test_key");
    assertThat(index.mappings().meta().get("test_key").to(String.class)).isEqualTo("test_value");
    assertThat(index.aliases().keySet()).isEqualTo(Set.of(descriptor.getAlias()));
    assertThat(index.settings().index().numberOfReplicas())
        .isEqualTo(indexSettings.getNumberOfReplicas().toString());
    assertThat(index.settings().index().numberOfShards())
        .isEqualTo(indexSettings.getNumberOfShards().toString());
  }

  @Test
  void shouldUpdateIndexMeta() throws IOException {
    // given
    final var descriptor =
        createTestIndexDescriptor("index_name-" + ENGINE_CLIENT_TEST_MARKERS, "/mappings.json");
    final var indexSettings = new IndexConfiguration();
    elsEngineClient.createIndex(descriptor, indexSettings);

    // when
    elsEngineClient.putIndexMeta(
        descriptor.getFullQualifiedName(),
        Map.of("string_key", "string_value", "bool_key", true, "int_key", 42));

    // then
    final var index =
        elsClient
            .indices()
            .get(req -> req.index(descriptor.getFullQualifiedName()))
            .get(descriptor.getFullQualifiedName());

    SchemaTestUtil.validateMappings(index.mappings(), "/mappings.json");

    assertThat(index.mappings().meta())
        .isNotEmpty()
        .containsKeys("string_key", "bool_key", "int_key");
    assertThat(index.mappings().meta().get("string_key").to(String.class))
        .isEqualTo("string_value");
    assertThat(index.mappings().meta().get("bool_key").to(Boolean.class)).isTrue();
    assertThat(index.mappings().meta().get("int_key").to(Integer.class)).isEqualTo(42);
    assertThat(index.aliases().keySet()).isEqualTo(Set.of(descriptor.getAlias()));
    assertThat(index.settings().index().numberOfReplicas())
        .isEqualTo(indexSettings.getNumberOfReplicas().toString());
    assertThat(index.settings().index().numberOfShards())
        .isEqualTo(indexSettings.getNumberOfShards().toString());
  }
}
