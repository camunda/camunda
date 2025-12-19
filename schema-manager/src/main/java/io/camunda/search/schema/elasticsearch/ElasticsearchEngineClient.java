/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.elasticsearch;

import static io.camunda.search.schema.utils.SearchEngineClientUtils.convertValue;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.AcknowledgedResponse;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch._types.Slices;
import co.elastic.clients.elasticsearch._types.SlicesCalculation;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.ilm.DeleteAction;
import co.elastic.clients.elasticsearch.ilm.PutLifecycleRequest;
import co.elastic.clients.elasticsearch.indices.Alias;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.IndexTemplate;
import co.elastic.clients.elasticsearch.indices.PutIndexTemplateRequest;
import co.elastic.clients.elasticsearch.indices.PutIndicesSettingsRequest;
import co.elastic.clients.elasticsearch.indices.PutMappingRequest;
import co.elastic.clients.elasticsearch.indices.get_index_template.IndexTemplateItem;
import co.elastic.clients.elasticsearch.indices.put_index_template.IndexTemplateMapping;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.JsonpDeserializer;
import co.elastic.clients.json.JsonpSerializable;
import co.elastic.clients.json.jackson.JacksonJsonpGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.schema.IndexMapping;
import io.camunda.search.schema.IndexMappingProperty;
import io.camunda.search.schema.MappingSource;
import io.camunda.search.schema.SchemaResourceSerializer;
import io.camunda.search.schema.SearchEngineClient;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.schema.exceptions.IndexSchemaValidationException;
import io.camunda.search.schema.exceptions.SearchEngineException;
import io.camunda.search.schema.utils.SearchEngineClientUtils;
import io.camunda.search.schema.utils.SearchEngineClientUtils.SchemaSettingsAppender;
import io.camunda.search.schema.utils.SuppressLogger;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;

public class ElasticsearchEngineClient implements SearchEngineClient {
  private static final SuppressLogger LOG =
      new SuppressLogger(LoggerFactory.getLogger(ElasticsearchEngineClient.class));
  private static final Slices AUTO_SLICES =
      Slices.of(slices -> slices.computed(SlicesCalculation.Auto));
  private final ElasticsearchClient client;
  private final SearchEngineClientUtils utils;
  private final ObjectMapper mapper;
  private final SchemaResourceSerializer schemaSerializer;

  public ElasticsearchEngineClient(
      final ElasticsearchClient client, final ObjectMapper objectMapper) {
    this.client = client;
    utils = new SearchEngineClientUtils(objectMapper);
    mapper = objectMapper;
    schemaSerializer = new SchemaResourceSerializer(mapper);
  }

  @Override
  public void createIndex(
      final IndexDescriptor indexDescriptor, final IndexConfiguration settings) {
    final CreateIndexRequest request = createIndexRequest(indexDescriptor, settings);
    try {
      client.indices().create(request);
      LOG.debug("Index [{}] was successfully created", indexDescriptor.getFullQualifiedName());
    } catch (final IOException ioe) {
      final var errMsg =
          String.format("Index [%s] was not created", indexDescriptor.getFullQualifiedName());
      LOG.error(errMsg, ioe);
      throw new SearchEngineException(errMsg, ioe);
    } catch (final ElasticsearchException elsEx) {
      if ("resource_already_exists_exception".equals(elsEx.error().type())) {
        // we can ignore already exists exceptions
        // as this means the index was created by another instance
        final var warnMsg =
            String.format(
                "Expected to create index [%s], but already exist. Will continue, likely was created by a different instance.",
                indexDescriptor.getFullQualifiedName());
        LOG.debug(warnMsg, elsEx);
        return;
      }

      final var errMsg =
          String.format("Index [%s] was not created", indexDescriptor.getFullQualifiedName());
      LOG.error(errMsg, elsEx);
      throw new SearchEngineException(errMsg, elsEx);
    }
  }

  @Override
  public void createIndexTemplate(
      final IndexTemplateDescriptor templateDescriptor,
      final IndexConfiguration indexConfiguration,
      final boolean create) {
    final PutIndexTemplateRequest request =
        putIndexTemplateRequest(templateDescriptor, indexConfiguration, create);

    try {
      client.indices().putIndexTemplate(request);
      LOG.debug("Template [{}] was successfully created", templateDescriptor.getTemplateName());
    } catch (final IOException e) {
      final var errMsg =
          String.format("Template [%s] was NOT created", templateDescriptor.getTemplateName());
      LOG.error(errMsg, e);
      throw new SearchEngineException(errMsg, e);
    } catch (final ElasticsearchException e) {
      // Creation should only occur once during initialisation but multiple partitions with
      // their own exporter will create race conditions where multiple exporters try to
      // create the same template
      final var errorReason = e.error().reason();
      if (errorReason != null
          && errorReason.equals(
              "index template [" + templateDescriptor.getTemplateName() + "] already exists")) {
        LOG.debug(errorReason);
        return;
      }

      LOG.error(errorReason, e);
      throw new SearchEngineException(errorReason, e);
    }
  }

  @Override
  public void putMapping(
      final IndexDescriptor indexDescriptor, final Collection<IndexMappingProperty> newProperties) {
    final PutMappingRequest request = putMappingRequest(indexDescriptor, newProperties);

    try {
      client.indices().putMapping(request);
      LOG.debug("Mapping in [{}] was successfully updated", indexDescriptor.getFullQualifiedName());
    } catch (final IOException | ElasticsearchException e) {
      final var errMsg =
          String.format("Mapping in [%s] was NOT updated", indexDescriptor.getFullQualifiedName());
      LOG.error(errMsg, e);
      throw new SearchEngineException(errMsg, e);
    }
  }

  @Override
  public Map<String, IndexMapping> getMappings(
      final String namePattern, final MappingSource mappingSource) {
    try {
      final Map<String, TypeMapping> mappings = getCurrentMappings(mappingSource, namePattern);

      return mappings.entrySet().stream()
          .collect(
              Collectors.toMap(
                  Entry::getKey,
                  entry -> {
                    final var mappingsBlock = entry.getValue();
                    return new IndexMapping.Builder()
                        .indexName(entry.getKey())
                        .dynamic(dynamicFromMappings(mappingsBlock))
                        .properties(propertiesFromMappings(mappingsBlock))
                        .metaProperties(metaFromMappings(mappingsBlock))
                        .build();
                  }));
    } catch (final IOException | ElasticsearchException e) {
      throw new SearchEngineException(
          String.format(
              "Failed retrieving mappings from index/index templates with pattern [%s]: %s",
              namePattern, e.getMessage()),
          e);
    }
  }

  @Override
  public void putSettings(
      final List<IndexDescriptor> indexDescriptors, final Map<String, String> toAppendSettings) {
    final var request = putIndexSettingsRequest(indexDescriptors, toAppendSettings);

    try {
      client.indices().putSettings(request);
    } catch (final IOException | ElasticsearchException e) {
      final var errMsg =
          String.format(
              "settings PUT failed for the following indices [%s]",
              utils.listIndicesByAlias(indexDescriptors));
      LOG.error(errMsg, e);
      throw new SearchEngineException(errMsg, e);
    }
  }

  @Override
  public void putIndexLifeCyclePolicy(final String policyName, final String deletionMinAge) {
    final PutLifecycleRequest request = putLifecycleRequest(policyName, deletionMinAge);

    try {
      client.ilm().putLifecycle(request);
    } catch (final IOException e) {
      final var errMsg = String.format("Index lifecycle policy [%s] failed to PUT", policyName);
      LOG.error(errMsg, e);
      throw new SearchEngineException(errMsg, e);
    }
  }

  @Override
  public void putIndexMeta(final String indexName, final Map<String, Object> meta) {
    final var request = putMetaMappingRequest(indexName, meta);

    try {
      client.indices().putMapping(request);
    } catch (final IOException | ElasticsearchException e) {
      final var errMsg = String.format("_meta PUT failed for %s", indexName);
      LOG.error(errMsg, e);
      throw new SearchEngineException(errMsg, e);
    }
  }

  /**
   * Overwrites the settings of the existing index template with the settings block in the
   * descriptor schema json file.
   *
   * @param indexTemplateDescriptor of the index template to have its settings overwritten.
   */
  @Override
  public void updateIndexTemplateSettings(
      final IndexTemplateDescriptor indexTemplateDescriptor,
      final IndexConfiguration indexConfiguration) {
    final var maybeRequest =
        buildTemplateSettingsUpdateRequestIfChanged(indexTemplateDescriptor, indexConfiguration);

    // If settings are equal, no update is needed
    if (maybeRequest.isEmpty()) {
      return;
    }

    try {
      client.indices().putIndexTemplate(maybeRequest.get());
    } catch (final IOException | ElasticsearchException e) {
      throw new SearchEngineException(
          String.format(
              "Expected to update index template settings '%s' with '%s', but failed: %s",
              indexTemplateDescriptor.getTemplateName(), maybeRequest.get(), e.getMessage()),
          e);
    }
  }

  @Override
  public void deleteIndex(final String indexName) {
    final DeleteIndexRequest deleteIndexRequest =
        new DeleteIndexRequest.Builder().index(indexName).build();
    try {
      final AcknowledgedResponse deleteIndexResponse = client.indices().delete(deleteIndexRequest);
      LOG.debug("Delete index acknowledged: {}", deleteIndexResponse.acknowledged());
    } catch (final IOException e) {
      final var errMsg = String.format("Failed to delete index %s", indexName);
      throw new SearchEngineException(errMsg, e);
    }
  }

  @Override
  public void truncateIndex(final String indexName) {
    final DeleteByQueryRequest deleteByQueryRequest =
        new DeleteByQueryRequest.Builder()
            .waitForCompletion(true)
            .slices(AUTO_SLICES)
            .conflicts(Conflicts.Proceed)
            .index(indexName)
            .query(q -> q.matchAll(m -> m))
            .refresh(true)
            .build();
    try {
      client.indices().refresh(r -> r.index(indexName));
      LOG.debug("Refreshed index {}", indexName);
      final DeleteByQueryResponse response = client.deleteByQuery(deleteByQueryRequest);
      LOG.debug("Deleted {} documents from index {}", response.deleted(), indexName);
    } catch (final IOException e) {
      final var errMsg = String.format("Failed to delete docs from index %s", indexName);
      throw new SearchEngineException(errMsg, e);
    }
  }

  @Override
  public boolean isHealthy() {
    try {
      final var response = client.cluster().health(r -> r.timeout(Time.of(t -> t.time("500ms"))));
      return !response.timedOut()
          && response.status() != null
          && !response.status().equals(HealthStatus.Red);
    } catch (final IOException e) {
      LOG.warn(
          String.format(
              "Couldn't connect to Elasticsearch due to %s. Return unhealthy state. ",
              e.getMessage()),
          e);
      return false;
    }
  }

  @Override
  public boolean indexExists(final String indexName) {
    try {
      return client.indices().exists(r -> r.index(indexName)).value();
    } catch (final IOException | ElasticsearchException ex) {
      final var errMsg = String.format("Failed to check existence of index %s", indexName);
      throw new SearchEngineException(errMsg, ex);
    }
  }

  @Override
  public Map<String, Object> getDocument(final String indexName, final String documentId) {
    try {
      final GetResponse<Map> response =
          client.get(g -> g.index(indexName).id(documentId), Map.class);
      if (response.found()) {
        return response.source();
      } else {
        LOG.debug("Document with ID [{}] not found in index [{}]", documentId, indexName);
        return null;
      }
    } catch (final IOException | ElasticsearchException ex) {
      final var errMsg =
          String.format("Failed to get document [%s] from index [%s]", documentId, indexName);
      throw new SearchEngineException(errMsg, ex);
    }
  }

  @Override
  public void upsertDocument(
      final String indexName, final String documentId, final Map<String, Object> document) {
    final IndexRequest<Map<String, Object>> request =
        IndexRequest.of(i -> i.index(indexName).id(documentId).document(document));
    try {
      client.index(request);
      LOG.debug("Document [{}] was successfully upserted in index [{}]]", documentId, indexName);
    } catch (final IOException | ElasticsearchException ex) {
      final var errMsg =
          String.format("Failed to upsert document [%s] in index [%s]", documentId, indexName);
      throw new SearchEngineException(errMsg, ex);
    }
  }

  private PutIndicesSettingsRequest putIndexSettingsRequest(
      final List<IndexDescriptor> indexDescriptors, final Map<String, String> toAppendSettings) {
    final co.elastic.clients.elasticsearch.indices.IndexSettings settings =
        utils.mapToSettings(
            toAppendSettings,
            (inp) ->
                deserializeJson(
                    co.elastic.clients.elasticsearch.indices.IndexSettings._DESERIALIZER, inp));
    return new PutIndicesSettingsRequest.Builder()
        .index(utils.listIndicesByAlias(indexDescriptors))
        .settings(settings)
        .build();
  }

  public PutLifecycleRequest putLifecycleRequest(
      final String policyName, final String deletionMinAge) {
    return new PutLifecycleRequest.Builder()
        .name(policyName)
        .policy(
            policy ->
                policy.phases(
                    phase ->
                        phase.delete(
                            del ->
                                del.minAge(m -> m.time(deletionMinAge))
                                    .actions(a -> a.delete(DeleteAction.of(d -> d))))))
        .build();
  }

  private Map<String, TypeMapping> getCurrentMappings(
      final MappingSource mappingSource, final String namePattern) throws IOException {
    if (mappingSource == MappingSource.INDEX) {
      return client
          .indices()
          .getMapping(req -> req.index(namePattern).ignoreUnavailable(true))
          .result()
          .entrySet()
          .stream()
          .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().mappings()));
    } else if (mappingSource == MappingSource.INDEX_TEMPLATE) {
      return client
          .indices()
          .getIndexTemplate(req -> req.name(namePattern))
          .indexTemplates()
          .stream()
          .filter(
              indexTemplateItem ->
                  indexTemplateItem.indexTemplate().template() != null
                      && indexTemplateItem.indexTemplate().template().mappings() != null)
          .collect(
              Collectors.toMap(
                  IndexTemplateItem::name, item -> item.indexTemplate().template().mappings()));
    } else {
      throw new IndexSchemaValidationException(
          "Invalid mapping source provided must be either INDEX or INDEX_TEMPLATE");
    }
  }

  private Set<IndexMappingProperty> propertiesFromMappings(final TypeMapping mapping) {
    return mapping.properties().entrySet().stream()
        .map(
            p ->
                new IndexMappingProperty.Builder()
                    .name(p.getKey())
                    .typeDefinition(serializeAsMap(p.getValue()))
                    .build())
        .collect(Collectors.toSet());
  }

  private Map<String, Object> serializeAsMap(final JsonpSerializable serializable) {
    if (serializable == null) {
      return null;
    }
    try {
      return schemaSerializer.serialize(
          (JacksonJsonpGenerator::new),
          (jacksonJsonpGenerator) ->
              serializable.serialize(jacksonJsonpGenerator, client._transport().jsonpMapper()));
    } catch (final IOException e) {
      throw new SearchEngineException(
          String.format("Failed to serialize [%s]", serializable.toString()), e);
    }
  }

  private String dynamicFromMappings(final TypeMapping mapping) {
    final var dynamic = mapping.dynamic();
    return dynamic == null ? "true" : dynamic.toString().toLowerCase();
  }

  private Map<String, Object> metaFromMappings(final TypeMapping mapping) {
    return mapping.meta().entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, ent -> ent.getValue().to(Object.class)));
  }

  private PutMappingRequest putMappingRequest(
      final IndexDescriptor indexDescriptor, final Collection<IndexMappingProperty> newProperties) {

    final var elsProperties =
        newProperties.stream()
            .map(p -> p.toElasticsearchProperty(client._jsonpMapper(), mapper))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

    return new PutMappingRequest.Builder()
        .index(indexDescriptor.getAlias())
        .properties(elsProperties)
        .build();
  }

  public <T> T deserializeJson(final JsonpDeserializer<T> deserializer, final InputStream json) {
    try (final var parser = client._jsonpMapper().jsonProvider().createParser(json)) {
      return deserializer.deserialize(parser, client._jsonpMapper());
    }
  }

  private InputStream getResourceAsStream(final String classpathFileName) {
    return getClass().getResourceAsStream(classpathFileName);
  }

  private PutIndexTemplateRequest putIndexTemplateRequest(
      final IndexTemplateDescriptor indexTemplateDescriptor,
      final IndexConfiguration settings,
      final Boolean create) {

    try (final var templateFile =
        getResourceAsStream(indexTemplateDescriptor.getMappingsClasspathFilename())) {

      final var templateFields =
          deserializeJson(
              IndexTemplateMapping._DESERIALIZER,
              utils.new SchemaSettingsAppender(templateFile)
                  .withNumberOfReplicas(settings.getNumberOfReplicas())
                  .withNumberOfShards(settings.getNumberOfShards())
                  .build());
      return PutIndexTemplateRequest.of(
          b ->
              b.name(indexTemplateDescriptor.getTemplateName())
                  .indexPatterns(indexTemplateDescriptor.getIndexPattern())
                  .template(
                      t ->
                          t.aliases(indexTemplateDescriptor.getAlias(), Alias.of(a -> a))
                              .mappings(templateFields.mappings())
                              .settings(templateFields.settings()))
                  .priority(convertValue(settings.getTemplatePriority(), Long::valueOf))
                  .composedOf(indexTemplateDescriptor.getComposedOf())
                  .create(create));
    } catch (final IOException e) {
      throw new SearchEngineException(
          "Failed to load file "
              + indexTemplateDescriptor.getMappingsClasspathFilename()
              + " from classpath.",
          e);
    }
  }

  private Optional<PutIndexTemplateRequest> buildTemplateSettingsUpdateRequestIfChanged(
      final IndexTemplateDescriptor indexTemplateDescriptor,
      final IndexConfiguration indexConfiguration) {
    try (final var templateFile =
        getResourceAsStream(indexTemplateDescriptor.getMappingsClasspathFilename())) {
      final var currentTemplate = getIndexTemplateState(indexTemplateDescriptor);
      final var configuredSettings =
          utils.new SchemaSettingsAppender(templateFile)
              .withNumberOfShards(indexConfiguration.getNumberOfShards())
              .withNumberOfReplicas(indexConfiguration.getNumberOfReplicas());
      final var configuredPriority =
          convertValue(indexConfiguration.getTemplatePriority(), Long::valueOf);

      if (areTemplateSettingsEqualToConfigured(
          currentTemplate, configuredSettings, configuredPriority)) {
        LOG.debug(
            "Index template settings for [{}] are already up to date",
            indexTemplateDescriptor.getTemplateName());
        return Optional.empty();
      }
      final var updatedTemplateSettings =
          deserializeJson(IndexTemplateMapping._DESERIALIZER, configuredSettings.build())
              .settings();
      LOG.debug(
          "Applying to index template [{}]: settings={}, priority={}",
          indexTemplateDescriptor.getTemplateName(),
          updatedTemplateSettings,
          configuredPriority);
      return Optional.of(
          PutIndexTemplateRequest.of(
              b ->
                  b.name(indexTemplateDescriptor.getTemplateName())
                      .indexPatterns(indexTemplateDescriptor.getIndexPattern())
                      .template(
                          t ->
                              t.settings(updatedTemplateSettings)
                                  .mappings(currentTemplate.template().mappings())
                                  .aliases(currentTemplate.template().aliases()))
                      .composedOf(currentTemplate.composedOf())
                      .priority(configuredPriority)));
    } catch (final IOException e) {
      throw new SearchEngineException(
          "Failed to load file "
              + indexTemplateDescriptor.getMappingsClasspathFilename()
              + " from classpath.",
          e);
    }
  }

  private boolean areTemplateSettingsEqualToConfigured(
      final IndexTemplate currentTemplate,
      final SchemaSettingsAppender configuredSettings,
      final Long configuredPriority) {
    return Objects.equals(configuredPriority, currentTemplate.priority())
        && configuredSettings.equalsSettings(serializeAsMap(currentTemplate.template().settings()));
  }

  private IndexTemplate getIndexTemplateState(
      final IndexTemplateDescriptor indexTemplateDescriptor) {
    try {
      return client
          .indices()
          .getIndexTemplate(r -> r.name(indexTemplateDescriptor.getTemplateName()))
          .indexTemplates()
          .getFirst()
          .indexTemplate();
    } catch (final IOException e) {
      throw new SearchEngineException(
          String.format(
              "Failed to retrieve index template '%s'", indexTemplateDescriptor.getTemplateName()),
          e);
    }
  }

  private CreateIndexRequest createIndexRequest(
      final IndexDescriptor indexDescriptor, final IndexConfiguration settings) {
    try (final var templateFile =
        getResourceAsStream(indexDescriptor.getMappingsClasspathFilename())) {

      final var templateFields =
          deserializeJson(
              IndexTemplateMapping._DESERIALIZER,
              utils.new SchemaSettingsAppender(templateFile)
                  .withNumberOfReplicas(settings.getNumberOfReplicas())
                  .withNumberOfShards(settings.getNumberOfShards())
                  .build());

      return new CreateIndexRequest.Builder()
          .index(indexDescriptor.getFullQualifiedName())
          .aliases(indexDescriptor.getAlias(), a -> a.isWriteIndex(false))
          .mappings(templateFields.mappings())
          .settings(templateFields.settings())
          .build();
    } catch (final IOException e) {
      throw new SearchEngineException(
          "Failed to load file "
              + indexDescriptor.getMappingsClasspathFilename()
              + " from classpath.",
          e);
    }
  }

  private PutMappingRequest putMetaMappingRequest(
      final String indexName, final Map<String, Object> meta) {
    final var jsonMeta =
        meta.entrySet().stream()
            .collect(Collectors.toMap(Entry::getKey, e -> JsonData.of(e.getValue())));
    return new PutMappingRequest.Builder().index(indexName).meta(jsonMeta).build();
  }

  @Override
  public void close() {
    if (client != null) {
      try {
        client.close();
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
