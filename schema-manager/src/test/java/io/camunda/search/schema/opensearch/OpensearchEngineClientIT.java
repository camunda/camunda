/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.opensearch;

import static io.camunda.search.schema.utils.SchemaTestUtil.createTestIndexDescriptor;
import static io.camunda.search.schema.utils.SchemaTestUtil.createTestTemplateDescriptor;
import static io.camunda.search.test.utils.SearchDBExtension.ENGINE_CLIENT_TEST_MARKERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.schema.IndexMappingProperty;
import io.camunda.search.schema.MappingSource;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.schema.exceptions.SearchEngineException;
import io.camunda.search.schema.opensearch.OpensearchEngineClient.ISMPolicyState;
import io.camunda.search.schema.utils.SchemaTestUtil;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.search.test.utils.TestObjectMapper;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest;

public class OpensearchEngineClientIT {

  @RegisterExtension private static final SearchDBExtension SEARCH_DB = SearchDBExtension.create();

  private static OpenSearchClient openSearchClient;
  private static ObjectMapper objectMapper;
  private static OpensearchEngineClient opensearchEngineClient;

  @BeforeAll
  public static void init() {
    openSearchClient = SEARCH_DB.osClient();
    objectMapper =
        ((JacksonJsonpMapper) openSearchClient._transport().jsonpMapper()).objectMapper();
    opensearchEngineClient = new OpensearchEngineClient(openSearchClient, objectMapper);
  }

  @Test
  void shouldCreateIndexNormally() throws IOException {
    // given
    final var descriptor =
        createTestIndexDescriptor("index_name-" + ENGINE_CLIENT_TEST_MARKERS, "/mappings.json");

    // when
    final var indexSettings = new IndexConfiguration();
    opensearchEngineClient.createIndex(descriptor, indexSettings);

    // then
    final var index =
        openSearchClient
            .indices()
            .get(req -> req.index(descriptor.getFullQualifiedName()))
            .get(descriptor.getFullQualifiedName());

    SchemaTestUtil.validateMappings(index.mappings(), "/mappings.json");

    assertThat(index.aliases().keySet()).isEqualTo(Set.of(descriptor.getAlias()));
    assertThat(index.settings().index().numberOfReplicas())
        .isEqualTo(indexSettings.getNumberOfReplicas().toString());
    assertThat(index.settings().index().numberOfShards())
        .isEqualTo(indexSettings.getNumberOfShards().toString());
  }

  @Test
  void shouldCreateIndexTemplate() throws IOException {
    // given
    final var template =
        createTestTemplateDescriptor("template_name", "/mappings-and-settings.json");

    // when
    final var expectedIndexSettings = new IndexConfiguration();
    opensearchEngineClient.createIndexTemplate(template, expectedIndexSettings, false);

    // then
    final var createdTemplate =
        openSearchClient
            .indices()
            .getIndexTemplate(req -> req.name(template.getTemplateName()))
            .indexTemplates();

    assertThat(createdTemplate.size()).isEqualTo(1);

    final var indexSettings =
        createdTemplate
            .getFirst()
            .indexTemplate()
            .template()
            .settings()
            .get("index")
            .toJson()
            .asJsonObject();

    assertThat(indexSettings.getString("number_of_shards"))
        .isEqualTo(expectedIndexSettings.getNumberOfShards().toString());
    assertThat(indexSettings.getString("number_of_replicas"))
        .isEqualTo(expectedIndexSettings.getNumberOfReplicas().toString());
    assertThat(indexSettings.getString("refresh_interval")).isEqualTo("2s");

    SchemaTestUtil.validateMappings(
        createdTemplate.getFirst().indexTemplate().template().mappings(),
        template.getMappingsClasspathFilename());
  }

  @Test
  void shouldNotThrowIfCreatingExistingTemplate() {
    // given
    final var indexTemplate = createTestTemplateDescriptor("template_name", "/mappings.json");

    final var settings = new IndexConfiguration();
    opensearchEngineClient.createIndexTemplate(indexTemplate, settings, true);

    // when, then
    assertThatNoException()
        .describedAs("Creating an already existing template should not throw")
        .isThrownBy(
            () -> opensearchEngineClient.createIndexTemplate(indexTemplate, settings, true));
  }

  @Test
  void shouldPutMappingCorrectly() throws IOException {
    // given
    final var descriptor = createTestIndexDescriptor("index_name", "/mappings.json");
    opensearchEngineClient.createIndex(descriptor, new IndexConfiguration());

    final Set<IndexMappingProperty> newProperties = new HashSet<>();
    newProperties.add(new IndexMappingProperty("email", Map.of("type", "keyword")));
    newProperties.add(new IndexMappingProperty("age", Map.of("type", "integer")));

    // when
    opensearchEngineClient.putMapping(descriptor, newProperties);

    // then
    final var indices =
        openSearchClient
            .indices()
            .get(req -> req.index(descriptor.getFullQualifiedName()))
            .result();

    assertThat(indices.size()).isEqualTo(1);
    final var properties = indices.get(descriptor.getFullQualifiedName()).mappings().properties();

    assertThat(properties.get("email").isKeyword()).isTrue();
    assertThat(properties.get("age").isInteger()).isTrue();
  }

  @Test
  void shouldRetrieveAllIndexMappingsWithImplementationAgnosticReturnType() {
    // given
    final var index =
        createTestIndexDescriptor(
            "index_name_" + ENGINE_CLIENT_TEST_MARKERS, "/mappings-complex-property.json");

    opensearchEngineClient.createIndex(index, new IndexConfiguration());

    // when
    final var mappings =
        opensearchEngineClient.getMappings(index.getFullQualifiedName(), MappingSource.INDEX);

    // then
    assertThat(mappings.size()).isEqualTo(1);
    assertThat(mappings.get(index.getFullQualifiedName()).dynamic()).isEqualTo("strict");

    assertThat(mappings.get(index.getFullQualifiedName()).properties())
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
  void shouldRetrieveAllIndexTemplateMappingsWithImplementationAgnosticReturnType() {
    // given
    final var template =
        createTestTemplateDescriptor(
            "template_name_" + ENGINE_CLIENT_TEST_MARKERS, "/mappings-complex-property.json");

    opensearchEngineClient.createIndexTemplate(template, new IndexConfiguration(), true);

    // when
    final var templateMappings =
        opensearchEngineClient.getMappings(
            template.getTemplateName(), MappingSource.INDEX_TEMPLATE);

    // then
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
  void shouldNotThrowErrorIfRetrievingMappingsWhereOnlySubsetOfIndicesExist() {
    // given
    final var index = createTestIndexDescriptor("index_name", "/mappings.json");

    opensearchEngineClient.createIndex(index, new IndexConfiguration());

    // when, then
    assertThatNoException()
        .isThrownBy(
            () ->
                opensearchEngineClient.getMappings(
                    index.getFullQualifiedName() + "*,foo*", MappingSource.INDEX));
  }

  @Test
  void shouldUpdateSettingsWithPutSettingsRequest() throws IOException {
    // given
    final var index = createTestIndexDescriptor("index_name", "/mappings.json");

    opensearchEngineClient.createIndex(index, new IndexConfiguration());

    // when
    final Map<String, String> newSettings = Map.of("index.refresh_interval", "5s");
    opensearchEngineClient.putSettings(List.of(index), newSettings);

    // then
    final var indices =
        openSearchClient.indices().get(req -> req.index(index.getFullQualifiedName()));

    assertThat(indices.result().size()).isEqualTo(1);
    assertThat(
            indices
                .result()
                .get(index.getFullQualifiedName())
                .settings()
                .index()
                .refreshInterval()
                .time())
        .isEqualTo("5s");
  }

  @Test
  @DisabledIfSystemProperty(
      named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "Excluding from AWS OS IT CI - policies not allowed for shared DBs")
  void shouldCreateIndexLifeCyclePolicy() throws IOException {
    // given, when
    opensearchEngineClient.putIndexLifeCyclePolicy("policy_name", "20d");

    // then
    final var req =
        Requests.builder().method("GET").endpoint("/_plugins/_ism/policies/policy_name").build();
    try (final var response = openSearchClient.generic().execute(req)) {
      assertThat(response.getStatus()).isEqualTo(200);
      assertThat(
              TestObjectMapper.objectMapper()
                  .readTree(response.getBody().get().body())
                  .get("policy")
                  .get("states")
                  .get(0)
                  .get("transitions")
                  .get(0)
                  .get("conditions")
                  .get("min_index_age")
                  .asText())
          .isEqualTo("20d");
    }
  }

  @Test
  @DisabledIfSystemProperty(
      named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "Excluding from AWS OS IT CI - policies not allowed for shared DBs")
  void shouldReturnEmptyISMPolicyStateIfIndexLifeCyclePolicyNotFound() throws IOException {
    // when
    final ISMPolicyState ismPolicyState =
        opensearchEngineClient.getCurrentISMPolicyState("unknown_policy_name");

    // then: verify the empty state is returned
    assertThat(ismPolicyState.exists()).isFalse();
  }

  @Test
  @DisabledIfSystemProperty(
      named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "Excluding from AWS OS IT CI - policies not allowed for shared DBs")
  void shouldAlwaysUpdateIndexLifeCyclePolicyEvenIfExistingHasSameValue() throws IOException {
    // given
    opensearchEngineClient.putIndexLifeCyclePolicy("always_update_ism_policy_name", "20d");

    // then: policy state after first creation
    final ISMPolicyState policyStateAfterCreation =
        opensearchEngineClient.getCurrentISMPolicyState("always_update_ism_policy_name");

    // then: verify state after creation
    assertThat(policyStateAfterCreation.exists()).isTrue();
    assertThat(getPolicyMinAge("always_update_ism_policy_name")).isEqualTo("20d");

    // when: update ISM with same parameters
    assertThatNoException()
        .isThrownBy(
            () ->
                opensearchEngineClient.putIndexLifeCyclePolicy(
                    "always_update_ism_policy_name", "20d"));

    // then: policy state after first creation
    final ISMPolicyState policyStateAfterUpdate =
        opensearchEngineClient.getCurrentISMPolicyState("always_update_ism_policy_name");

    // then: state seq no should increment, but others should remain the same
    assertThat(policyStateAfterUpdate.exists()).isTrue();
    assertThat(policyStateAfterUpdate.primaryTerm())
        .isEqualTo(policyStateAfterCreation.primaryTerm());
    assertThat(policyStateAfterUpdate.seqNo()).isGreaterThan(policyStateAfterCreation.seqNo());
    assertThat(getPolicyMinAge("always_update_ism_policy_name")).isEqualTo("20d");
  }

  @Test
  void shouldFailIfIndexStateManagementPolicyInvalid() {
    // given, when, then
    assertThatThrownBy(
            () -> opensearchEngineClient.putIndexLifeCyclePolicy("policy_name", "test123"))
        .isInstanceOf(SearchEngineException.class)
        .hasMessageContaining(
            "Creating index state management policy [policy_name] with min_deletion_age [test123] failed.");
  }

  @Test
  void shouldNotIssuePutIndexTemplateWhenSettingsUnchanged() throws IOException {
    // given
    final var template = createTestTemplateDescriptor("template_no_change", "/mappings.json");
    final var initialSettings = new IndexConfiguration();
    initialSettings.setNumberOfReplicas(0);
    initialSettings.setNumberOfShards(1);
    initialSettings.setTemplatePriority(50);

    final var indicesSpy = spy(openSearchClient.indices());
    final var clientSpy = spy(openSearchClient);
    doReturn(indicesSpy).when(clientSpy).indices();
    final var engineClient = new OpensearchEngineClient(clientSpy, TestObjectMapper.objectMapper());

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

    final var indicesSpy = spy(openSearchClient.indices());
    final var clientSpy = spy(openSearchClient);
    doReturn(indicesSpy).when(clientSpy).indices();
    final var engineClient = new OpensearchEngineClient(clientSpy, TestObjectMapper.objectMapper());

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

    final var indicesSpy = spy(openSearchClient.indices());
    final var clientSpy = spy(openSearchClient);
    doReturn(indicesSpy).when(clientSpy).indices();
    final var engineClient = new OpensearchEngineClient(clientSpy, TestObjectMapper.objectMapper());

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
    opensearchEngineClient.createIndex(descriptor, indexSettings);

    // then
    final var index =
        openSearchClient
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
    opensearchEngineClient.createIndex(descriptor, indexSettings);

    // when
    opensearchEngineClient.putIndexMeta(
        descriptor.getFullQualifiedName(),
        Map.of("string_key", "string_value", "bool_key", true, "int_key", 42));

    // then
    final var index =
        openSearchClient
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

  private String getPolicyMinAge(final String policyName) throws IOException {
    final var request =
        Requests.builder().method("GET").endpoint("_plugins/_ism/policies/" + policyName).build();

    final var policyJsonNode =
        objectMapper.readTree(openSearchClient.generic().execute(request).getBody().get().body());

    return policyJsonNode
        .path("policy")
        .path("states")
        .path(0)
        .path("transitions")
        .path(0)
        .path("conditions")
        .get("min_index_age")
        .asText();
  }
}
