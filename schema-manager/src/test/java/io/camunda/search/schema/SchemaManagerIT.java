/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static io.camunda.search.schema.utils.SchemaManagerITInvocationProvider.CONFIG_PREFIX;
import static io.camunda.search.schema.utils.SchemaTestUtil.createSchemaManager;
import static io.camunda.search.schema.utils.SchemaTestUtil.createTestIndexDescriptor;
import static io.camunda.search.schema.utils.SchemaTestUtil.createTestTemplateDescriptor;
import static io.camunda.search.schema.utils.SchemaTestUtil.mappingsMatch;
import static io.camunda.search.schema.utils.SchemaTestUtil.searchEngineClientFromConfig;
import static io.camunda.search.schema.utils.SchemaTestUtil.startupWithRetry;
import static io.camunda.search.test.utils.SearchDBExtension.CUSTOM_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.schema.config.RetentionConfiguration;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.exceptions.IndexSchemaValidationException;
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
import io.camunda.webapps.schema.descriptors.index.MetadataIndex;
import io.camunda.webapps.schema.descriptors.template.PersistentWebSessionTemplate;
import io.camunda.zeebe.test.util.junit.RegressionTestTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.testcontainers.Testcontainers;
import org.testcontainers.toxiproxy.ToxiproxyContainer;

@DisabledIfSystemProperty(
    named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
    matches = "^(?=\\s*\\S).*$",
    disabledReason = "Excluding from AWS OS IT CI")
@ExtendWith(SchemaManagerITInvocationProvider.class)
public class SchemaManagerIT {

  private TestIndexDescriptor index;
  private TestTemplateDescriptor indexTemplate;
  private MetadataIndex metadataIndex;
  private ObjectMapper objectMapper;
  private final List<AutoCloseable> closeables = new ArrayList<>();

  @BeforeEach
  public void refresh() throws IOException {
    metadataIndex = new MetadataIndex(CONFIG_PREFIX, true);
    objectMapper = TestObjectMapper.objectMapper();
    indexTemplate = createTestTemplateDescriptor("template_name", "/mappings.json");
    index = createTestIndexDescriptor("index_name", "/mappings.json");
  }

  @AfterEach
  public void cleanup() {
    closeables.forEach(
        closeable -> {
          try {
            closeable.close();
          } catch (final Exception e) {
            // ignore
          }
        });
    closeables.clear();
  }

  @TestTemplate
  void shouldAppendToIndexMappingsWithNewProperties(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given
    final var schemaManager =
        new SchemaManager(
            getSearchEngineClient(config),
            Set.of(index, metadataIndex),
            Set.of(),
            config,
            objectMapper);

    initialiseResources(schemaManager);

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
    config.index().setNumberOfReplicas(10);
    config.index().setNumberOfShards(10);

    final var schemaManager =
        new SchemaManager(
            getSearchEngineClient(config),
            Set.of(index, metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    // when
    initialiseResources(schemaManager);

    // then — replicas always follow global; shards on plain index/ descriptors are pinned to 1
    // by the descriptor default regardless of the global knob (templates still follow global)
    final var retrievedIndex = searchClientAdapter.getIndexAsNode(index.getFullQualifiedName());
    assertThat(retrievedIndex.at("/settings/index/number_of_replicas").asInt()).isEqualTo(10);
    assertThat(retrievedIndex.at("/settings/index/number_of_shards").asInt()).isEqualTo(1);

    final var retrievedTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());
    assertThat(
            retrievedTemplate
                .at("/index_template/template/settings/index/number_of_shards")
                .asInt())
        .isEqualTo(10);
  }

  @TestTemplate
  void shouldUseIndexSpecificSettingsIfSpecified(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given
    config.index().setNumberOfReplicas(10);
    config.index().setNumberOfShards(10);
    config.index().setReplicasByIndexName(Map.of("index_name", 5));
    config.index().setShardsByIndexName(Map.of("index_name", 5));

    final var schemaManager =
        new SchemaManager(
            getSearchEngineClient(config),
            Set.of(index, metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    // when
    initialiseResources(schemaManager);

    // then
    final var retrievedIndex = searchClientAdapter.getIndexAsNode(index.getFullQualifiedName());

    assertThat(retrievedIndex.at("/settings/index/number_of_replicas").asInt()).isEqualTo(5);
    assertThat(retrievedIndex.at("/settings/index/number_of_shards").asInt()).isEqualTo(5);
  }

  @TestTemplate
  void shouldReadShardCountsOfExistingIndicesOnly(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given
    config.index().setShardsByIndexName(Map.of(index.getIndexName(), 3));
    final var searchEngineClient = searchEngineClientFromConfig(config);
    final var schemaManager =
        new SchemaManager(
            searchEngineClient, Set.of(index, metadataIndex), Set.of(), config, objectMapper);
    initialiseResources(schemaManager);

    // when
    final var shardCounts =
        searchEngineClient.getNumberOfShards(
            Set.of(index.getFullQualifiedName(), CUSTOM_PREFIX + "-absent-index"));

    // then — an index that does not exist is absent from the result rather than an error, which is
    // what lets the startup check run before every index has necessarily been created
    assertThat(shardCounts).containsExactly(entry(index.getFullQualifiedName(), 3));
  }

  @TestTemplate
  void shouldOverwriteIndexTemplateIfMappingsFileChanged(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given
    final var schemaManager =
        new SchemaManager(
            getSearchEngineClient(config),
            Set.of(metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    initialiseResources(schemaManager);

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
            getSearchEngineClient(config),
            Set.of(index, metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    // when
    startupWithRetry(schemaManager, config);

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
            getSearchEngineClient(config),
            Set.of(index, metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    startupWithRetry(schemaManager, config);

    // when
    index.setMappingsClasspathFilename("/mappings-added-property.json");
    indexTemplate.setMappingsClasspathFilename("/mappings-added-property.json");

    startupWithRetry(schemaManager, config);

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

  @RegressionTestTemplate("https://github.com/camunda/camunda/issues/57256")
  void shouldUpdateTemplateMappingsWhenNoBackingIndexExists(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given
    config.schemaManager().setCreateSchema(true);
    final var searchEngineClient = getSearchEngineClient(config);
    final var schemaManager =
        new SchemaManager(
            searchEngineClient,
            Set.of(index, metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    startupWithRetry(schemaManager, config);

    // when - the runtime index backing the template is dropped, e.g. by an operator, leaving only
    // the template behind, and the descriptor's mapping is then upgraded
    searchEngineClient.deleteIndex(indexTemplate.getFullQualifiedName());
    searchClientAdapter.refresh();
    indexTemplate.setMappingsClasspathFilename("/mappings-added-property.json");

    startupWithRetry(schemaManager, config);

    // then - the template itself must reflect the new mapping, otherwise future indices created
    // off it (e.g. after a rollover) would mismatch the freshly re-created runtime index
    final var retrievedIndexTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

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

    indices.add(metadataIndex);
    indices.add(index);
    indexTemplates.add(indexTemplate);

    var schemaManager =
        new SchemaManager(
            getSearchEngineClient(config), indices, indexTemplates, config, objectMapper);

    startupWithRetry(schemaManager, config);

    // when
    final var newIndex = createTestIndexDescriptor("new_index", "/mappings-added-property.json");
    final var newIndexTemplate =
        createTestTemplateDescriptor("new_template_name", "/mappings-added-property.json");
    indices.add(newIndex);
    indexTemplates.add(newIndexTemplate);

    schemaManager =
        new SchemaManager(
            getSearchEngineClient(config), indices, indexTemplates, config, objectMapper);
    startupWithRetry(schemaManager, config);

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
            getSearchEngineClient(config),
            Set.of(index, metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    startupWithRetry(schemaManager, config);

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
  void shouldCreateLifeCyclePoliciesWithDefaultValuesOnStartupIfEnabled(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws IOException {
    // given
    config.schemaManager().setCreateSchema(true);
    config.retention().setEnabled(true);

    final var schemaManager =
        new SchemaManager(
            getSearchEngineClient(config), Set.of(metadataIndex), Set.of(), config, objectMapper);
    // when
    startupWithRetry(schemaManager, config);

    // then: verify that all configured retention policies were created with default values
    assertThat(searchClientAdapter.getPolicyAsNode("camunda-retention-policy"))
        .asInstanceOf(type(JsonNode.class)) // switch from IterableAssert -> ObjectAssert<JsonNode>
        .extracting(this::retentionMinAge)
        .isEqualTo("30d");

    assertThat(searchClientAdapter.getPolicyAsNode("camunda-usage-metrics-retention-policy"))
        .asInstanceOf(type(JsonNode.class))
        .extracting(this::retentionMinAge)
        .isEqualTo("730d");
  }

  @TestTemplate
  void shouldCreateLifeCyclePoliciesWithCustomValuesOnStartupIfEnabled(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws IOException {
    // given
    config.schemaManager().setCreateSchema(true);
    final RetentionConfiguration retention = config.retention();
    retention.setEnabled(true);
    retention.setPolicyName("custom-retention-policy");
    retention.setMinimumAge("88d");
    retention.setUsageMetricsPolicyName("custom-metrics-retention-policy");
    retention.setUsageMetricsMinimumAge("100d");

    final var schemaManager =
        new SchemaManager(
            getSearchEngineClient(config), Set.of(metadataIndex), Set.of(), config, objectMapper);
    // when
    startupWithRetry(schemaManager, config);

    // then: verify that all configured retention policies were created with custom values
    assertThat(searchClientAdapter.getPolicyAsNode("custom-retention-policy"))
        .asInstanceOf(type(JsonNode.class)) // switch from IterableAssert -> ObjectAssert<JsonNode>
        .extracting(this::retentionMinAge)
        .isEqualTo("88d");

    assertThat(searchClientAdapter.getPolicyAsNode("custom-metrics-retention-policy"))
        .asInstanceOf(type(JsonNode.class))
        .extracting(this::retentionMinAge)
        .isEqualTo("100d");
  }

  @TestTemplate
  void shouldCreateIndexInAdditionToTemplateFromTemplateDescriptor(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    config.schemaManager().setCreateSchema(true);

    final var schemaManager =
        new SchemaManager(
            getSearchEngineClient(config),
            Set.of(metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    startupWithRetry(schemaManager, config);

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
            getSearchEngineClient(config),
            Set.of(metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    startupWithRetry(schemaManager, config);

    final var retrievedIndex =
        searchClientAdapter.getIndexAsNode(indexTemplate.getFullQualifiedName());

    assertThat(mappingsMatch(retrievedIndex.get("mappings"), currentMappingsFile)).isTrue();

    // when
    indexTemplate.setMappingsClasspathFilename(newMappingsFile);

    startupWithRetry(schemaManager, config);

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
            getSearchEngineClient(config),
            Set.of(metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    startupWithRetry(schemaManager, config);

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
    startupWithRetry(schemaManager, config);

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
            getSearchEngineClient(config),
            Set.of(metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    startupWithRetry(schemaManager, config);

    final var replicaSettingPath = "/index_template/template/settings/index/number_of_replicas";
    final var shardsSettingPath = "/index_template/template/settings/index/number_of_shards";

    final var initialTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    assertThat(initialTemplate.at(replicaSettingPath).asInt()).isEqualTo(1);
    assertThat(initialTemplate.at(shardsSettingPath).asInt()).isEqualTo(1);

    // when
    config.index().setNumberOfReplicas(5);
    config.index().setNumberOfShards(5);

    startupWithRetry(schemaManager, config);

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
            getSearchEngineClient(config),
            Set.of(index, metadataIndex),
            Set.of(),
            config,
            objectMapper);

    startupWithRetry(schemaManager, config);

    final var replicaSettingPath = "/settings/index/number_of_replicas";
    final var shardsSettingPath = "/settings/index/number_of_shards";

    final var initialIndex = searchClientAdapter.getIndexAsNode(index.getFullQualifiedName());

    assertThat(initialIndex.at(replicaSettingPath).asInt()).isEqualTo(1);
    assertThat(initialIndex.at(shardsSettingPath).asInt()).isEqualTo(1);

    // when
    config.index().setNumberOfReplicas(5);
    config.index().setNumberOfShards(5);

    startupWithRetry(schemaManager, config);

    // then
    final var updatedIndex = searchClientAdapter.getIndexAsNode(index.getFullQualifiedName());

    assertThat(updatedIndex.at(replicaSettingPath).asInt()).isEqualTo(5);
    assertThat(updatedIndex.at(shardsSettingPath).asInt()).isEqualTo(1);
  }

  @TestTemplate
  void shouldUpdateLifeCyclePoliciesWithNewValuesOnRestartIfEnabled(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws IOException {
    // given
    config.schemaManager().setCreateSchema(true);
    final var retention = config.retention();
    retention.setEnabled(true);
    retention.setPolicyName("custom-retention-policy");
    retention.setMinimumAge("88d");
    retention.setUsageMetricsPolicyName("custom-metrics-retention-policy");
    retention.setUsageMetricsMinimumAge("100d");

    final var schemaManager =
        new SchemaManager(
            getSearchEngineClient(config), Set.of(metadataIndex), Set.of(), config, objectMapper);
    // when
    startupWithRetry(schemaManager, config);

    // then: verify that all configured retention policies were created with custom value
    assertThat(searchClientAdapter.getPolicyAsNode("custom-retention-policy"))
        .asInstanceOf(type(JsonNode.class)) // switch from IterableAssert -> ObjectAssert<JsonNode>
        .extracting(this::retentionMinAge)
        .isEqualTo("88d");

    assertThat(searchClientAdapter.getPolicyAsNode("custom-metrics-retention-policy"))
        .asInstanceOf(type(JsonNode.class))
        .extracting(this::retentionMinAge)
        .isEqualTo("100d");

    // when: update the retention configuration with new values and restart the schema manager
    retention.setMinimumAge("44d");
    retention.setUsageMetricsMinimumAge("50d");
    startupWithRetry(schemaManager, config);

    // then: verify that all configured retention policies were updated with new custom values
    assertThat(searchClientAdapter.getPolicyAsNode("custom-retention-policy"))
        .asInstanceOf(type(JsonNode.class)) // switch from IterableAssert -> ObjectAssert<JsonNode>
        .extracting(this::retentionMinAge)
        .isEqualTo("44d");
    assertThat(searchClientAdapter.getPolicyAsNode("custom-metrics-retention-policy"))
        .asInstanceOf(type(JsonNode.class))
        .extracting(this::retentionMinAge)
        .isEqualTo("50d");
  }

  @TestTemplate
  void shouldIsSchemaReadyForUseReturnTrueWhenAllIndicesAndTemplatesAreCreated(
      final SearchEngineConfiguration config, final SearchClientAdapter ignored) {
    // given
    final var schemaManager =
        new SchemaManager(
            getSearchEngineClient(config),
            Set.of(index, metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    startupWithRetry(schemaManager, config);

    // when, then
    assertThat(schemaManager.isSchemaReadyForUse()).isTrue();
  }

  @TestTemplate
  void shouldIsSchemaReadyForUseReturnFalseWhenARuntimeTemplatedIndexIsMissing(
      final SearchEngineConfiguration config, final SearchClientAdapter ignored) {
    // given
    final SearchEngineClient searchEngineClient = getSearchEngineClient(config);
    final var schemaManager =
        new SchemaManager(
            searchEngineClient,
            Set.of(index, metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    startupWithRetry(schemaManager, config);

    // delete the templated runtime index
    searchEngineClient.deleteIndex(indexTemplate.getFullQualifiedName());

    // when, then
    assertThat(schemaManager.isSchemaReadyForUse()).isFalse();
  }

  @TestTemplate
  void shouldIsSchemaReadyForUseReturnFalseWhenTemplateHasDifferentMapping(
      final SearchEngineConfiguration config, final SearchClientAdapter ignored) {
    // given
    final SearchEngineClient searchEngineClient = getSearchEngineClient(config);
    final var schemaManager =
        new SchemaManager(
            searchEngineClient, Set.of(metadataIndex), Set.of(indexTemplate), config, objectMapper);

    startupWithRetry(schemaManager, config);

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
            getSearchEngineClient(config),
            Set.of(metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    startupWithRetry(schemaManager, config);

    final var replicaSettingPath = "/index_template/template/settings/index/number_of_replicas";
    final var shardsSettingPath = "/index_template/template/settings/index/number_of_shards";

    final var initialTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    assertThat(initialTemplate.at(replicaSettingPath).asInt()).isEqualTo(1);
    assertThat(initialTemplate.at(shardsSettingPath).asInt()).isEqualTo(1);

    indexTemplate.setMappingsClasspathFilename("/mappings-settings-replica-and-shards.json");

    config.index().setNumberOfReplicas(5);
    config.index().setNumberOfShards(5);

    startupWithRetry(schemaManager, config);

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
    final var searchEngineClient = getSearchEngineClient(config);

    searchEngineClient.createIndexTemplate(indexTemplate, new IndexConfiguration(), true);

    searchClientAdapter.refresh();

    // when
    final var schemaManager =
        new SchemaManager(
            getSearchEngineClient(config),
            Set.of(metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    startupWithRetry(schemaManager, config);

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
            getSearchEngineClient(config),
            indexDescriptors.indices(),
            indexDescriptors.templates(),
            config,
            objectMapper);

    startupWithRetry(schemaManager, config);
    assertThatNoException().isThrownBy(() -> startupWithRetry(schemaManager, config));
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
            getSearchEngineClient(config),
            Set.of(metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    final var schemaManager2 =
        new SchemaManager(
            getSearchEngineClient(config),
            Set.of(metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    // when
    final var future = CompletableFuture.runAsync(() -> startupWithRetry(schemaManager1, config));

    // then
    assertThatNoException().isThrownBy(() -> startupWithRetry(schemaManager2, config));
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
    final var schemaManager1 =
        createSchemaManager(
            getSearchEngineClient(config),
            Set.of(index, metadataIndex),
            Set.of(indexTemplate),
            config);
    final var schemaManager2 =
        createSchemaManager(
            getSearchEngineClient(config),
            Set.of(index, metadataIndex),
            Set.of(indexTemplate),
            config);

    index.setMappingsClasspathFilename("/mappings-added-property.json");
    indexTemplate.setMappingsClasspathFilename("/mappings-added-property.json");

    // when
    startupWithRetry(schemaManager1, config);
    startupWithRetry(schemaManager2, config);

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
    final var schemaManager1 =
        createSchemaManager(
            getSearchEngineClient(config),
            Set.of(index, metadataIndex),
            Set.of(indexTemplate),
            config);
    final var schemaManager2 =
        createSchemaManager(
            getSearchEngineClient(config),
            Set.of(index, metadataIndex),
            Set.of(indexTemplate),
            config);

    index.setMappingsClasspathFilename("/mappings-added-property.json");
    indexTemplate.setMappingsClasspathFilename("/mappings-added-property.json");

    // when
    startupWithRetry(schemaManager1, config);
    startupWithRetry(schemaManager2, config);

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
            getSearchEngineClient(config),
            Set.of(metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    startupWithRetry(schemaManager, config);

    final String runtimeIndexName = indexTemplate.getFullQualifiedName();
    final var initialRuntimeIndex = searchClientAdapter.getIndexAsNode(runtimeIndexName);
    assertThat(mappingsMatch(initialRuntimeIndex.get("mappings"), "/mappings.json")).isTrue();

    final String archiveIndexName = indexTemplate.getIndexPattern().replace("*", "-archived");
    searchClientAdapter.index("123", archiveIndexName, Map.of("hello", "foo", "world", "bar"));
    final var initialArchiveIndex = searchClientAdapter.getIndexAsNode(archiveIndexName);
    assertThat(mappingsMatch(initialArchiveIndex.get("mappings"), "/mappings.json")).isTrue();

    // when
    indexTemplate.setMappingsClasspathFilename("/mappings-added-property.json");
    startupWithRetry(schemaManager, config);

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
            getSearchEngineClient(config),
            Set.of(metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    startupWithRetry(schemaManager, config);

    final var replicaSettingPath = "/settings/index/number_of_replicas";
    final var shardsSettingPath = "/settings/index/number_of_shards";

    final String runtimeIndexName = indexTemplate.getFullQualifiedName();
    final var initialRuntimeIndex = searchClientAdapter.getIndexAsNode(runtimeIndexName);

    assertThat(initialRuntimeIndex.at(replicaSettingPath).asInt()).isEqualTo(1);
    assertThat(initialRuntimeIndex.at(shardsSettingPath).asInt()).isEqualTo(1);

    final String archiveIndexName = indexTemplate.getIndexPattern().replace("*", "-archived");
    searchClientAdapter.index("123", archiveIndexName, Map.of("hello", "foo", "world", "bar"));

    final var initialArchiveIndex = searchClientAdapter.getIndexAsNode(archiveIndexName);
    assertThat(initialArchiveIndex.at(replicaSettingPath).asInt()).isEqualTo(1);
    assertThat(initialArchiveIndex.at(shardsSettingPath).asInt()).isEqualTo(1);

    // when
    config.index().setNumberOfReplicas(5);
    config.index().setNumberOfShards(5);

    startupWithRetry(schemaManager, config);

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
        createSchemaManager(
            getSearchEngineClient(config),
            indexDescriptors.indices(),
            indexDescriptors.templates(),
            config);

    final var mappingsBeforeStart = adapter.getAllIndicesAsNode(newPrefix);
    assertThat(mappingsBeforeStart).isEmpty();

    // when
    startupWithRetry(schemaManager, config);

    // then
    final var mappingsAfterOpen = adapter.getAllIndicesAsNode(newPrefix);
    assertThat(mappingsAfterOpen.keySet())
        // we verify the names hard coded on purpose
        // to make sure no index will be accidentally dropped, names are changed or added
        .containsExactlyInAnyOrder(
            newPrefix + "-camunda-wait-state-8.10.0_",
            newPrefix + "-camunda-job-metrics-batch-8.9.0_",
            newPrefix + "-camunda-authorization-8.8.0_",
            newPrefix + "-camunda-cluster-variable-8.9.0_",
            newPrefix + "-camunda-correlated-message-subscription-8.8.0_",
            newPrefix + "-camunda-global-listener-8.9.0_",
            newPrefix + "-camunda-group-8.8.0_",
            newPrefix + "-camunda-mapping-rule-8.8.0_",
            newPrefix + "-camunda-role-8.8.0_",
            newPrefix + "-camunda-tenant-8.8.0_",
            newPrefix + "-camunda-usage-metric-8.8.0_",
            newPrefix + "-camunda-usage-metric-tu-8.8.0_",
            newPrefix + "-camunda-user-8.8.0_",
            newPrefix + "-camunda-web-session-8.8.0_",
            newPrefix + "-camunda-audit-log-8.9.0_",
            newPrefix + "-camunda-audit-log-cleanup-8.9.0_",
            newPrefix + "-camunda-history-deletion-8.9.0_",
            newPrefix + "-camunda-agent-instance-8.10.0_",
            newPrefix + "-camunda-agent-history-8.10.0_",
            newPrefix + "-camunda-agent-definition-8.10.0_",
            newPrefix + "-camunda-deployed-resource-8.10.0_",
            newPrefix + "-operate-batch-operation-1.0.0_",
            newPrefix + "-operate-decision-8.3.0_",
            newPrefix + "-operate-decision-instance-8.3.0_",
            newPrefix + "-operate-decision-requirements-8.3.0_",
            newPrefix + "-operate-event-8.3.0_",
            newPrefix + "-operate-flownode-instance-8.3.1_",
            newPrefix + "-operate-incident-8.3.1_",
            newPrefix + "-operate-list-view-8.3.0_",
            newPrefix + "-operate-metadata-8.8.0_",
            newPrefix + "-operate-message-8.5.0_",
            newPrefix + "-operate-operation-8.4.1_",
            newPrefix + "-operate-post-importer-queue-8.3.0_",
            newPrefix + "-operate-process-8.3.0_",
            newPrefix + "-operate-sequence-flow-8.3.0_",
            newPrefix + "-operate-variable-8.3.0_",
            newPrefix + "-operate-job-8.6.0_",
            newPrefix + "-tasklist-draft-task-variable-8.3.0_",
            newPrefix + "-tasklist-form-8.4.0_",
            newPrefix + "-tasklist-task-8.8.0_",
            newPrefix + "-tasklist-task-variable-8.3.0_");
  }

  @TestTemplate
  void shouldRecordSchemaInitTimerMetric(
      final SearchEngineConfiguration config, final SearchClientAdapter ignored) {
    // given
    final var registry = new SimpleMeterRegistry();
    final var schemaManager =
        new SchemaManager(
            getSearchEngineClient(config),
            Set.of(index, metadataIndex),
            Set.of(),
            config,
            objectMapper,
            new SchemaManagerMetrics(registry));

    // when
    startupWithRetry(schemaManager, config);

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
            getSearchEngineClient(config),
            Set.of(index, metadataIndex),
            Set.of(),
            config,
            objectMapper,
            new SchemaManagerMetrics(registry));

    // when
    assertThatExceptionOfType(SearchEngineException.class)
        .isThrownBy(() -> startupWithRetry(schemaManager, config));

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
            getSearchEngineClient(config),
            Set.of(metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    startupWithRetry(schemaManager, config);

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
    startupWithRetry(schemaManager, config);

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
    startupWithRetry(schemaManager, config);

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
  void shouldDetectDifferencesWhenSchemaWasAutoCreatedWithDefaults(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws IOException {
    // given
    final var indexTemplate = createTestTemplateDescriptor("template_name", "/mappings.json");

    final var runtimeIndexName = indexTemplate.getFullQualifiedName();

    // index some data so ES/OS creates the index with a dynamic mapping and difference field
    // definitions
    searchClientAdapter.index("123", runtimeIndexName, Map.of("hello", "a", "world", "b"));

    final var schemaManager =
        new SchemaManager(
            getSearchEngineClient(config),
            Set.of(metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    // when
    // then
    assertThatThrownBy(() -> startupWithRetry(schemaManager, config))
        .isInstanceOf(IndexSchemaValidationException.class)
        .hasMessageContaining(
            "Index names: [custom-prefix-test-template_name-1.0.0_]. Unsupported index changes have been introduced. Data migration is required.");
  }

  @TestTemplate
  void shouldSetIndexTemplatePriorityWhenCreatingTemplate(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given
    config.index().setTemplatePriority(100);

    final var schemaManager =
        new SchemaManager(
            getSearchEngineClient(config),
            Set.of(metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    // when
    startupWithRetry(schemaManager, config);

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
            getSearchEngineClient(config),
            Set.of(metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    // when
    startupWithRetry(schemaManager, config);

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
            getSearchEngineClient(config),
            Set.of(metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    startupWithRetry(schemaManager, config);

    // verify initial priority is set
    var retrievedTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());
    assertThat(retrievedTemplate.at("/index_template/priority").asInt()).isEqualTo(50);
    // capture initial replica/shard
    final int initialReplicas =
        retrievedTemplate.at("/index_template/template/settings/index/number_of_replicas").asInt();
    final int initialShards =
        retrievedTemplate.at("/index_template/template/settings/index/number_of_shards").asInt();

    // when - update template settings with new priority via startup
    config.index().setTemplatePriority(200);

    startupWithRetry(schemaManager, config);

    // then - verify priority was updated
    retrievedTemplate = searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());
    assertThat(retrievedTemplate.at("/index_template/priority").asInt()).isEqualTo(200);
    // and shards/replicas untouched
    assertThat(
            retrievedTemplate
                .at("/index_template/template/settings/index/number_of_replicas")
                .asInt())
        .isEqualTo(initialReplicas);
    assertThat(
            retrievedTemplate
                .at("/index_template/template/settings/index/number_of_shards")
                .asInt())
        .isEqualTo(initialShards);
  }

  @TestTemplate
  void shouldNotUpdateIndexTemplateSettingsWhenUnchanged(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given
    config.index().setTemplatePriority(75);
    config.index().setNumberOfShards(2);
    config.index().setNumberOfReplicas(1);

    final var schemaManager =
        new SchemaManager(
            getSearchEngineClient(config),
            Set.of(metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    startupWithRetry(schemaManager, config);

    final var templateName = indexTemplate.getTemplateName();
    final var initialTemplate = searchClientAdapter.getIndexTemplateAsNode(templateName);
    final var initialSettingsJson =
        initialTemplate.at("/index_template/template/settings").toString();
    final var initialPriorityNode = initialTemplate.at("/index_template/priority");

    // when - run startup again without any config or mapping changes
    startupWithRetry(schemaManager, config);

    // then - settings & priority stay identical
    final var secondTemplate = searchClientAdapter.getIndexTemplateAsNode(templateName);
    assertThat(secondTemplate.at("/index_template/template/settings").toString())
        .isEqualTo(initialSettingsJson);
    final var secondPriorityNode = secondTemplate.at("/index_template/priority");
    assertThat(secondPriorityNode.asInt()).isEqualTo(initialPriorityNode.asInt());
  }

  @TestTemplate
  void shoulUnsetIndexTemplatePriorityWhenSettingsIsRemoved(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given - create template with initial priority
    config.index().setTemplatePriority(50);

    final var schemaManager =
        new SchemaManager(
            getSearchEngineClient(config),
            Set.of(metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    startupWithRetry(schemaManager, config);

    // verify initial priority is set
    var retrievedTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());
    assertThat(retrievedTemplate.at("/index_template/priority").asInt()).isEqualTo(50);
    final int initialReplicas =
        retrievedTemplate.at("/index_template/template/settings/index/number_of_replicas").asInt();
    final int initialShards =
        retrievedTemplate.at("/index_template/template/settings/index/number_of_shards").asInt();

    // when - unset template priority setting
    config.index().setTemplatePriority(null);

    startupWithRetry(schemaManager, config);

    // then - verify priority is removed & other settings untouched
    retrievedTemplate = searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());
    assertThat(retrievedTemplate.at("/index_template/priority").isMissingNode()).isTrue();
    assertThat(
            retrievedTemplate
                .at("/index_template/template/settings/index/number_of_replicas")
                .asInt())
        .isEqualTo(initialReplicas);
    assertThat(
            retrievedTemplate
                .at("/index_template/template/settings/index/number_of_shards")
                .asInt())
        .isEqualTo(initialShards);
  }

  @TestTemplate
  void shouldPreservePriorityWhenUpdatingTemplateMappings(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given - create template with priority
    config.index().setTemplatePriority(150);

    final var schemaManager =
        new SchemaManager(
            getSearchEngineClient(config),
            Set.of(metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    startupWithRetry(schemaManager, config);

    // verify initial priority is set
    var retrievedTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());
    assertThat(retrievedTemplate.at("/index_template/priority").asInt()).isEqualTo(150);

    // when - update template mappings
    indexTemplate.setMappingsClasspathFilename("/mappings-added-property.json");
    startupWithRetry(schemaManager, config);

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

  @TestTemplate
  void shouldNotTryToCreateExistingIndexTemplate(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter) {
    // given - create first schema manager with one template and verify it's created
    final SearchEngineClient searchEngineClient = spy(getSearchEngineClient(config));
    final var firstSchemaManager =
        new SchemaManager(
            searchEngineClient, Set.of(metadataIndex), Set.of(indexTemplate), config, objectMapper);

    initialiseResources(firstSchemaManager);

    // verify the first template was created
    verify(searchEngineClient, times(1)).createIndexTemplate(eq(indexTemplate), any(), eq(true));

    // verify template exists in the search engine
    assertThatNoException()
        .isThrownBy(
            () -> searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName()));

    // when - reset mock and create second schema manager with existing template plus a new one
    reset(searchEngineClient);

    final var secondIndexTemplate =
        createTestTemplateDescriptor("template_name_2", "/mappings.json");
    final var secondSchemaManager =
        new SchemaManager(
            searchEngineClient,
            Set.of(metadataIndex),
            Set.of(indexTemplate, secondIndexTemplate),
            config,
            objectMapper);

    initialiseResources(secondSchemaManager);

    // then - verify existing template was not recreated but new template was created
    verify(searchEngineClient, never()).createIndexTemplate(eq(indexTemplate), any(), eq(true));
    verify(searchEngineClient, times(1))
        .createIndexTemplate(eq(secondIndexTemplate), any(), eq(true));

    // verify both templates exist in the search engine
    assertThatNoException()
        .isThrownBy(
            () -> searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName()));
    assertThatNoException()
        .isThrownBy(
            () ->
                searchClientAdapter.getIndexTemplateAsNode(secondIndexTemplate.getTemplateName()));
  }

  @TestTemplate
  void shouldNotFailStartupWhenOptionalTemplateIndexIsMissing(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given
    final var webSessionTemplate =
        new PersistentWebSessionTemplate(
            config.connect().getIndexPrefix(), config.connect().getTypeEnum().isElasticSearch());
    final var searchEngineClient = getSearchEngineClient(config);
    final var schemaManager =
        new SchemaManager(
            searchEngineClient,
            Set.of(metadataIndex),
            Set.of(webSessionTemplate),
            config,
            objectMapper);
    startupWithRetry(schemaManager, config);
    searchEngineClient.deleteIndex(webSessionTemplate.getFullQualifiedName());
    searchClientAdapter.refresh();

    // when
    final var restartedSchemaManager =
        new SchemaManager(
            searchEngineClient,
            Set.of(metadataIndex),
            Set.of(webSessionTemplate),
            config,
            objectMapper);

    // then
    assertThatNoException().isThrownBy(() -> startupWithRetry(restartedSchemaManager, config));
    searchEngineClient.deleteIndex(webSessionTemplate.getFullQualifiedName());
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(searchEngineClient.indexExists(webSessionTemplate.getFullQualifiedName()))
                  .isFalse();
              assertThat(restartedSchemaManager.isSchemaReadyForUse()).isTrue();
            });

    searchClientAdapter.deleteIndexTemplate(webSessionTemplate.getTemplateName());
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(restartedSchemaManager.isSchemaReadyForUse()).isFalse());
  }

  @TestTemplate
  void shouldCreateIndexTemplateWhenRelatedIndexAlreadyExists(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws IOException {
    // given - create schema first (which creates both template and index)
    final var schemaManager =
        new SchemaManager(
            getSearchEngineClient(config),
            Set.of(metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    startupWithRetry(schemaManager, config);

    // verify both template and index exist
    final String existingIndexName = indexTemplate.getFullQualifiedName();
    assertThatNoException().isThrownBy(() -> searchClientAdapter.getIndexAsNode(existingIndexName));
    assertThatNoException()
        .isThrownBy(
            () -> searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName()));

    // when - delete the index but keep the template
    searchClientAdapter.deleteIndex(existingIndexName);

    // verify index is deleted but template still exists
    assertThatThrownBy(() -> searchClientAdapter.getIndexAsNode(existingIndexName))
        .isInstanceOfAny(ElasticsearchException.class, OpenSearchException.class)
        .hasMessageContaining("no such index");
    assertThatNoException()
        .isThrownBy(
            () -> searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName()));

    // recreate schema using startup
    final SearchEngineClient searchEngineClient = spy(getSearchEngineClient(config));
    final var newSchemaManager =
        new SchemaManager(
            searchEngineClient, Set.of(metadataIndex), Set.of(indexTemplate), config, objectMapper);

    startupWithRetry(newSchemaManager, config);

    // then - verify both the index and template exist again
    assertThatNoException().isThrownBy(() -> searchClientAdapter.getIndexAsNode(existingIndexName));

    final var retrievedTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    // verify that schema manager created the index (since template existed but index was missing)
    verify(searchEngineClient, times(1)).createIndex(any(), any());

    // verify template has correct mappings
    assertThat(
            mappingsMatch(
                retrievedTemplate.at("/index_template/template/mappings"), "/mappings.json"))
        .isTrue();

    // verify template pattern matches the recreated index
    assertThat(retrievedTemplate.at("/index_template/index_patterns").toString())
        .contains(indexTemplate.getIndexPattern());
  }

  @TestTemplate
  void shouldCreateIndexWhenRelatedIndexTemplateAlreadyExists(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws IOException {
    // given - create only the template first (without the index)
    final SearchEngineClient searchEngineClient = getSearchEngineClient(config);
    searchEngineClient.createIndexTemplate(indexTemplate, new IndexConfiguration(), true);

    // verify template exists but index doesn't exist yet
    assertThatNoException()
        .isThrownBy(
            () -> searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName()));

    final String indexName = indexTemplate.getFullQualifiedName();
    assertThatThrownBy(() -> searchClientAdapter.getIndexAsNode(indexName))
        .isInstanceOfAny(ElasticsearchException.class, OpenSearchException.class)
        .hasMessageContaining("no such index");

    // when - start schema manager with spy to track index creation
    final SearchEngineClient spySearchEngineClient = spy(getSearchEngineClient(config));
    final var schemaManager =
        new SchemaManager(
            spySearchEngineClient,
            Set.of(metadataIndex),
            Set.of(indexTemplate),
            config,
            objectMapper);

    startupWithRetry(schemaManager, config);

    // then - verify the index was created but template was not recreated
    assertThatNoException().isThrownBy(() -> searchClientAdapter.getIndexAsNode(indexName));

    final var retrievedTemplate =
        searchClientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    // verify that schema manager did NOT recreate the template (since it already existed)
    verify(spySearchEngineClient, never()).createIndexTemplate(any(), any(), eq(true));

    // verify that schema manager DID create the index (since template existed but index was
    // missing)
    final var captor = ArgumentCaptor.forClass(IndexDescriptor.class);
    verify(spySearchEngineClient, times(2)).createIndex(captor.capture(), any());
    assertThat(captor.getAllValues().stream().map(IndexDescriptor::getIndexName))
        .containsExactlyInAnyOrder(indexTemplate.getIndexName(), metadataIndex.getIndexName());

    // verify template has correct mappings
    assertThat(
            mappingsMatch(
                retrievedTemplate.at("/index_template/template/mappings"), "/mappings.json"))
        .isTrue();

    // verify template pattern matches the created index
    assertThat(retrievedTemplate.at("/index_template/index_patterns").toString())
        .contains(indexTemplate.getIndexPattern());
  }

  /**
   * Regression test for the camunda-14 startup hang (120 physical tenants, ES yellow with a
   * backlogged master task queue, 2026-09-18): a schema mutation was accepted by the search engine
   * but never acknowledged within the process's lifetime. {@link SchemaManager#joinOnFutures} gives
   * up after {@link SchemaManager#INDEX_CREATION_TIMEOUT_SECONDS} and cancels the future, but that
   * alone does not stop the virtual thread already running it — {@link CompletableFuture#cancel}
   * never interrupts the underlying task. What used to leave the tenant's init thread stuck forever
   * — exactly where the incident's thread dump showed it: {@code SchemaManager.close() ->
   * ThreadPerTaskExecutor.close() -> awaitTermination() -> CountDownLatch.await()} — is {@link
   * #close()} itself now bounding that wait and escalating to {@code shutdownNow()} instead of
   * relying on the JDK's unbounded {@code ExecutorService.close()}.
   */
  @TestTemplate
  void shouldNotHangOnCloseWhenAnIndexTemplateCreationNeverCompletes(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given - createIndexTemplate is accepted but never returns, standing in for a call queued
    // behind an overloaded Elasticsearch master that never gets to it
    final SearchEngineClient searchEngineClient = spy(getSearchEngineClient(config));
    final var neverCompletes = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              neverCompletes.await();
              return invocation.callRealMethod();
            })
        .when(searchEngineClient)
        .createIndexTemplate(eq(indexTemplate), any(), eq(true));

    final var schemaManager =
        new SchemaManager(
            searchEngineClient, Set.of(metadataIndex), Set.of(indexTemplate), config, objectMapper);

    // when - startupOnce() times out because the future never completes, and close() runs on
    // the way out, exactly like SearchEngineSchemaInitializer.initializeTenant()'s
    // try-with-resources does for a real physical tenant
    final var tenantInitThread =
        new Thread(
            () -> {
              try {
                schemaManager.startupOnce();
              } catch (final Exception timedOut) {
                // expected: INDEX_CREATION_TIMEOUT_SECONDS elapses because the mocked call
                // never completes
              } finally {
                schemaManager.close();
              }
            },
            "schema-init-pt-under-test");
    tenantInitThread.setDaemon(true);
    tenantInitThread.start();

    try {
      // then - close() returns within a bound instead of hanging forever: it first waits
      // gracefully for up to INDEX_CREATION_TIMEOUT_SECONDS (the abandoned task never finishes
      // naturally, since the mock blocks on a latch with no timeout of its own), then escalates to
      // shutdownNow(), whose interrupt reaches the latch's await() and ends the task
      tenantInitThread.join(
          Duration.ofSeconds(2L * SchemaManager.INDEX_CREATION_TIMEOUT_SECONDS + 15).toMillis());

      assertThat(tenantInitThread.isAlive())
          .as(
              "SchemaManager.close() should not hang forever waiting on a virtual-thread task"
                  + " abandoned by a timed-out attempt")
          .isFalse();
    } finally {
      // cleanup - release the blocked call in case it is still running in the background
      neverCompletes.countDown();
      tenantInitThread.join(Duration.ofSeconds(10).toMillis());
    }
  }

  /**
   * Regression test for the same camunda-14 hang as {@link
   * #shouldNotHangOnCloseWhenAnIndexTemplateCreationNeverCompletes}, but against a real search
   * engine instead of a mock, to confirm the fix holds under the search-engine SDK's own bounded
   * defaults (connectTimeout=1s, socketTimeout=30s — see {@code
   * RestClientBuilder.createHttpClient()}) rather than being an artifact of mocking.
   *
   * <p>A Toxiproxy adds real, bounded per-request latency between the client and the real search
   * engine — well under the 30s socket timeout, so no individual call ever throws — and the
   * connection pool is shrunk to a single connection, so that far more than {@link
   * SchemaManager#INDEX_CREATION_TIMEOUT_SECONDS} of genuinely-succeeding work is serialized behind
   * it. {@code initialiseIndexTemplates() -> joinOnFutures()} times out with real work still queued
   * for that one connection, and {@code close()} — run exactly as {@code
   * SearchEngineSchemaInitializer.initializeTenant()}'s try-with-resources runs it on the way out
   * of a failed attempt — then bounds its own wait on that same, still genuinely-running, real work
   * instead of hanging on it forever.
   */
  @TestTemplate
  void shouldNotHangOnCloseUnderRealSearchEngineWithSlowConnectionPool(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws Exception {
    // given - the real search engine is reachable only through a Toxiproxy that delays every
    // request by latencyMillis (well under the SDK's own 30s socket timeout, so no call ever
    // throws), and the connection pool is shrunk to one, so genuinely-succeeding template
    // creations are fully serialized behind a single slow connection
    final var toxiproxyListenPort = 10_000;
    final var latencyMillis = 6_000L;
    final var templateCount = 20;

    // OpensearchConnector parses the URL with java.net.URI, which needs an explicit scheme to
    // find the host at all (a bare "host:port" is parsed as an opaque URI with scheme=host and a
    // null host) - ElasticsearchContainer's address has none, OpenSearchContainer's does, so the
    // proxied URL below must carry over whichever the original had.
    final var upstreamUrl = config.connect().getUrl();
    final var schemePrefix =
        upstreamUrl.contains("://") ? upstreamUrl.substring(0, upstreamUrl.indexOf("://") + 3) : "";
    final var upstreamPort =
        Integer.parseInt(upstreamUrl.substring(upstreamUrl.lastIndexOf(':') + 1));
    Testcontainers.exposeHostPorts(upstreamPort);

    final var toxiproxy =
        new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.5.0").withAccessToHost(true);
    toxiproxy.addExposedPorts(toxiproxyListenPort);
    toxiproxy.start();
    closeables.add(toxiproxy);

    final var toxiproxyClient =
        new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort());
    final var proxy =
        toxiproxyClient.createProxy(
            "search-engine",
            "0.0.0.0:" + toxiproxyListenPort,
            "host.testcontainers.internal:" + upstreamPort);
    proxy.toxics().latency("slow-search-engine", ToxicDirection.UPSTREAM, latencyMillis);

    config
        .connect()
        .setUrl(
            schemePrefix
                + toxiproxy.getHost()
                + ":"
                + toxiproxy.getMappedPort(toxiproxyListenPort));
    config.connect().setMaxConnections(1);
    config.connect().setMaxConnectionsPerRoute(1);
    config.connect().setConnectTimeout(1_000);
    config.connect().setSocketTimeout(30_000);

    final var manyTemplates = new HashSet<IndexTemplateDescriptor>();
    for (int i = 0; i < templateCount; i++) {
      manyTemplates.add(createTestTemplateDescriptor("hang_template_" + i, "/mappings.json"));
    }

    final SearchEngineClient searchEngineClient = getSearchEngineClient(config);
    final var schemaManager =
        new SchemaManager(
            searchEngineClient, Set.of(metadataIndex), manyTemplates, config, objectMapper);

    // when - drive it exactly like SearchEngineSchemaInitializer.initializeTenant() drives a single
    // attempt for a real physical tenant: startupOnce() times out with real work still queued
    // behind the single connection, and close() runs on the way out of that failed attempt
    final var tenantInitThread =
        new Thread(
            () -> {
              try {
                schemaManager.startupOnce();
              } catch (final Exception timedOut) {
                // expected: INDEX_CREATION_TIMEOUT_SECONDS elapses while real work for
                // templateCount templates is still queued behind the single connection
              } finally {
                schemaManager.close();
              }
            },
            "schema-init-pt-under-test-real-search-engine");
    tenantInitThread.setDaemon(true);
    tenantInitThread.start();

    try {
      // then - close() returns within a bound instead of hanging forever on the real,
      // still-running search-engine calls, even though close() itself first waits gracefully for
      // up to INDEX_CREATION_TIMEOUT_SECONDS before escalating to an interrupt
      tenantInitThread.join(
          Duration.ofSeconds(2L * SchemaManager.INDEX_CREATION_TIMEOUT_SECONDS + 30).toMillis());

      assertThat(tenantInitThread.isAlive())
          .as(
              "SchemaManager.close() should not hang forever waiting on real, still-running"
                  + " search-engine calls queued behind a slow connection pool")
          .isFalse();
    } finally {
      // cleanup - remove the toxic so any remaining real, queued work can drain immediately and
      // the thread (and the JVM) can actually terminate, instead of waiting out the full latency
      proxy.toxics().get("slow-search-engine").remove();
      tenantInitThread.join(Duration.ofSeconds(60).toMillis());
    }
  }

  private String retentionMinAge(final JsonNode policyNode) {
    // Check if this is an Elasticsearch policy (has "phases" structure)
    final var phases = policyNode.at("/policy/phases");
    if (!phases.isMissingNode()) {
      // Elasticsearch structure:
      // - policy.phases.delete.min_age
      return phases.at("/delete/min_age").asText();
    }

    // OpenSearch structure:
    // - policy.states[archived].transitions[to deleted].conditions.min_index_age
    final JsonNode archivedState =
        policyNode
            .at("/policy/states")
            .valueStream()
            .filter(state -> "archived".equals(state.get("name").asText()))
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("Could not find 'archived' state in policy"));
    final JsonNode deletedTransition =
        archivedState
            .get("transitions")
            .valueStream()
            .filter(transition -> "deleted".equals(transition.get("state_name").asText()))
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("Could not find transition to 'deleted' state"));

    return deletedTransition.at("/conditions/min_index_age").asText();
  }

  private void initialiseResources(final SchemaManager schemaManager) {
    schemaManager.initialiseIndexTemplates();
    schemaManager.initialiseIndices();
  }

  private SearchEngineClient getSearchEngineClient(final SearchEngineConfiguration config) {
    final var searchClient = searchEngineClientFromConfig(config);
    closeables.add(searchClient);
    return searchClient;
  }
}
