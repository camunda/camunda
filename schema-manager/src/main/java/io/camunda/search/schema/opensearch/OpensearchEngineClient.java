/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.opensearch;

import static io.camunda.search.schema.utils.SearchEngineClientUtils.convertValue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import io.camunda.zeebe.util.VisibleForTesting;
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
import org.apache.http.HttpStatus;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.jackson.JacksonJsonpGenerator;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Conflicts;
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.generic.Body;
import org.opensearch.client.opensearch.generic.Request;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest;
import org.opensearch.client.opensearch.indices.PutIndicesSettingsRequest;
import org.opensearch.client.opensearch.indices.PutMappingRequest;
import org.opensearch.client.opensearch.indices.get_index_template.IndexTemplate;
import org.opensearch.client.opensearch.indices.get_index_template.IndexTemplateItem;
import org.opensearch.client.opensearch.indices.put_index_template.IndexTemplateMapping;
import org.slf4j.LoggerFactory;

public class OpensearchEngineClient implements SearchEngineClient {
  public static final String ISM_POLICIES_ENDPOINT = "/_plugins/_ism/policies";
  private static final SuppressLogger LOG =
      new SuppressLogger(LoggerFactory.getLogger(OpensearchEngineClient.class));
  private static final String OPERATE_DELETE_ARCHIVED_POLICY =
      "/schema/opensearch/create/policy/operate_delete_archived_indices.json";
  private static final long AUTO_SLICES = 0; // see OS docs; 0 means auto
  private final ObjectReader objectReader;
  private final ObjectWriter objectWriter;
  private final OpenSearchClient client;
  private final SearchEngineClientUtils utils;
  private final ObjectMapper objectMappper;
  private final SchemaResourceSerializer schemaResourceSerializer;

  public OpensearchEngineClient(final OpenSearchClient client, final ObjectMapper objectMapper) {
    this.client = client;
    objectMappper = objectMapper;
    objectReader = objectMapper.reader();
    objectWriter = objectMapper.writer();
    utils = new SearchEngineClientUtils(objectMapper);
    schemaResourceSerializer = new SchemaResourceSerializer(objectMapper);
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
    } catch (final OpenSearchException ose) {
      if ("resource_already_exists_exception".equals(ose.error().type())) {
        // we can ignore already exists exceptions
        // as this means the index was created by another instance
        final var warnMsg =
            String.format(
                "Expected to create index [%s], but already exist. Will continue, likely was created by a different instance.",
                indexDescriptor.getFullQualifiedName());
        LOG.debug(warnMsg, ose);
        return;
      }

      final var errMsg =
          String.format("Index [%s] was not created", indexDescriptor.getFullQualifiedName());
      LOG.error(errMsg, ose);
      throw new SearchEngineException(errMsg, ose);
    }
  }

  @Override
  public void createIndexTemplate(
      final IndexTemplateDescriptor templateDescriptor,
      final IndexConfiguration indexConfiguration,
      final boolean create) {

    final PutIndexTemplateRequest request =
        putIndexTemplateRequest(templateDescriptor, indexConfiguration);

    try {
      if (create
          && client
              .indices()
              .existsIndexTemplate(req -> req.name(templateDescriptor.getTemplateName()))
              .value()) {
        // Creation should only occur once during initialisation but multiple partitions with
        // their own exporter will create race conditions where multiple exporters try to
        // create the same template
        LOG.debug(
            "Did not create index template [{}] as it already exists",
            templateDescriptor.getTemplateName());
        return;
      }

      client.indices().putIndexTemplate(request);
      LOG.debug("Template [{}] was successfully created", templateDescriptor.getTemplateName());
    } catch (final IOException | OpenSearchException e) {
      final var errMsg =
          String.format("Template [%s] was NOT created", templateDescriptor.getTemplateName());
      LOG.error(errMsg, e);
      throw new SearchEngineException(errMsg, e);
    }
  }

  @Override
  public void putMapping(
      final IndexDescriptor indexDescriptor, final Collection<IndexMappingProperty> newProperties) {
    final PutMappingRequest request = putMappingRequest(indexDescriptor, newProperties);

    try {
      client.indices().putMapping(request);
      LOG.debug("Mapping in [{}] was successfully updated", indexDescriptor.getFullQualifiedName());
    } catch (final IOException | OpenSearchException e) {
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
    } catch (final IOException | OpenSearchException e) {
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
    } catch (final IOException | OpenSearchException e) {
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
    final var request = createIndexStateManagementPolicy(policyName, deletionMinAge);

    try (final var response = client.generic().execute(request)) {
      if (response.getStatus() / 100 != 2) {
        throw new SearchEngineException(
            String.format(
                "Creating index state management policy [%s] with min_deletion_age [%s] failed. Http response = [%s]",
                policyName, deletionMinAge, response.getBody().get().bodyAsString()));
      }

    } catch (final IOException | OpenSearchException exception) {
      final String exceptionMessage = exception.getMessage();
      if (exceptionMessage.contains("already exists")) {
        LOG.warn(
            "Expected to create ISM policy with name '{}', but failed with: '{}'.",
            policyName,
            exceptionMessage);
        return;
      }
      final var errMsg =
          String.format("Failed to create index state management policy [%s]", policyName);
      throw new SearchEngineException(errMsg, exception);
    }
  }

  @Override
  public void putIndexMeta(final String indexName, final Map<String, Object> meta) {
    final var request = putMetaMappingRequest(indexName, meta);

    try {
      client.indices().putMapping(request);
    } catch (final IOException | OpenSearchException e) {
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
    } catch (final IOException | OpenSearchException e) {
      throw new SearchEngineException(
          String.format(
              "Expected to update index template settings '%s' with '%s', but failed: %s",
              indexTemplateDescriptor.getTemplateName(), maybeRequest.get(), e.getMessage()),
          e);
    }
  }

  @Override
  public void deleteIndex(final String indexName) {
    final DeleteIndexRequest request = new DeleteIndexRequest.Builder().index(indexName).build();

    try {
      client.indices().delete(request);
      LOG.debug("Index [{}] was successfully deleted", indexName);
    } catch (final IOException ioe) {
      final var errMsg = String.format("Index [%s] was not deleted", indexName);
      throw new SearchEngineException(errMsg, ioe);
    }
  }

  @Override
  public void truncateIndex(final String indexName) {
    final DeleteByQueryRequest request =
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
      final DeleteByQueryResponse response = client.deleteByQuery(request);
      LOG.debug("Deleted {} documents from index {}", response.deleted(), indexName);
    } catch (final IOException ioe) {
      final var errMsg = String.format("Failed to delete docs from index %s", indexName);
      throw new SearchEngineException(errMsg, ioe);
    }
  }

  @Override
  public boolean isHealthy() {
    try {
      final HealthResponse response = client.cluster().health(h -> h.timeout(t -> t.time("500ms")));
      return !response.timedOut()
          && response.status() != null
          && !response.status().equals(HealthStatus.Red);
    } catch (final IOException e) {
      LOG.warn(
          String.format(
              "Couldn't connect to OpenSearch due to %s. Return unhealthy state.", e.getMessage()),
          e);
      return false;
    }
  }

  @Override
  public boolean indexExists(final String indexName) {
    try {
      return client.indices().exists(req -> req.index(indexName)).value();
    } catch (final IOException | OpenSearchException ex) {
      final var errMsg = String.format("Failed to check if index [%s] exists", indexName);
      throw new SearchEngineException(errMsg, ex);
    }
  }

  @Override
  public Map<String, Object> getDocument(final String indexName, final String documentId) {
    final GetRequest request = GetRequest.of(g -> g.index(indexName).id(documentId));
    try {
      final GetResponse<Map> response = client.get(request, Map.class);
      if (response.found()) {
        return response.source();
      } else {
        LOG.debug("Document with ID [{}] not found in index [{}]", documentId, indexName);
        return null;
      }
    } catch (final IOException | OpenSearchException ex) {
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
      LOG.debug("Document [{}] was successfully upserted in index [{}]", documentId, indexName);
    } catch (final IOException | OpenSearchException ex) {
      final var errMsg =
          String.format("Failed to upsert document [%s] in index [%s]", documentId, indexName);
      throw new SearchEngineException(errMsg, ex);
    }
  }

  private Request createIndexStateManagementPolicy(
      final String policyName, final String deletionMinAge) {
    try (final var policyJson = getClass().getResourceAsStream(OPERATE_DELETE_ARCHIVED_POLICY)) {
      final var jsonMap = objectReader.readTree(policyJson);
      final var conditions =
          (ObjectNode)
              jsonMap
                  .path("policy")
                  .path("states")
                  .path(0)
                  .path("transitions")
                  .path(0)
                  .path("conditions");
      conditions.put("min_index_age", deletionMinAge);

      final var policy = objectWriter.writeValueAsBytes(jsonMap);

      final var builder =
          Requests.builder()
              .method("PUT")
              .endpoint(getPolicyEndpoint(policyName))
              .body(Body.from(policy, "application/json"));

      final var currentPolicyState = getCurrentISMPolicyState(policyName);
      if (currentPolicyState.exists()) {
        builder.query(
            Map.of(
                "if_seq_no",
                String.valueOf(currentPolicyState.seqNo()),
                "if_primary_term",
                String.valueOf(currentPolicyState.primaryTerm())));
      }

      return builder.build();
    } catch (final IOException e) {
      throw new SearchEngineException(
          "Failed to deserialize policy file " + OPERATE_DELETE_ARCHIVED_POLICY, e);
    }
  }

  private String getPolicyEndpoint(final String policyName) {
    return String.format("%s/%s", ISM_POLICIES_ENDPOINT, policyName);
  }

  @VisibleForTesting
  ISMPolicyState getCurrentISMPolicyState(final String policyName) {
    final var request =
        Requests.builder().method("GET").endpoint(getPolicyEndpoint(policyName)).build();

    try (final var response = client.generic().execute(request)) {
      if (response.getStatus() == HttpStatus.SC_NOT_FOUND) {
        // policy does not exist
        return ISMPolicyState.empty();
      }

      if (response.getStatus() / 100 != 2) {
        throw new SearchEngineException(
            String.format(
                "Retrieving the current index state management policy [%s] failed. Http response = [%s]",
                policyName, response.getBody().get().bodyAsString()));
      }

      final var policyJson = response.getBody().get().bodyAsString();
      final var policyJsonNode = objectReader.readTree(policyJson);
      return fromPolicyJson(policyJsonNode);
    } catch (final IOException e) {
      throw new SearchEngineException(
          String.format(
              "Failed to retrieve current seq_no for index state management policy [%s]",
              policyName),
          e);
    }
  }

  private ISMPolicyState fromPolicyJson(final JsonNode policyJsonNode) {
    final var primaryTerm = policyJsonNode.path("_primary_term").asInt();
    final var seqNo = policyJsonNode.path("_seq_no").asInt();
    return new ISMPolicyState(seqNo, primaryTerm);
  }

  private PutIndicesSettingsRequest putIndexSettingsRequest(
      final List<IndexDescriptor> indexDescriptors, final Map<String, String> toAppendSettings) {

    final org.opensearch.client.opensearch.indices.IndexSettings settings =
        utils.mapToSettings(
            toAppendSettings,
            (inp) ->
                deserializeJson(
                    org.opensearch.client.opensearch.indices.IndexSettings._DESERIALIZER, inp));
    return new PutIndicesSettingsRequest.Builder()
        .index(utils.listIndicesByAlias(indexDescriptors))
        .settings(settings)
        .build();
  }

  private String dynamicFromMappings(final TypeMapping mapping) {
    final var dynamic = mapping.dynamic();
    return dynamic == null ? "true" : dynamic.toString().toLowerCase();
  }

  private Map<String, Object> metaFromMappings(final TypeMapping mapping) {
    return mapping.meta().entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, ent -> ent.getValue().to(Object.class)));
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
      return schemaResourceSerializer.serialize(
          (JacksonJsonpGenerator::new),
          (jacksonJsonpGenerator) ->
              serializable.serialize(jacksonJsonpGenerator, client._transport().jsonpMapper()));
    } catch (final IOException e) {
      throw new SearchEngineException(
          String.format("Failed to serialize [%s]", serializable.toString()), e);
    }
  }

  private <V extends JsonpSerializable> Map<String, Object> serializeAsMap(
      final Map<String, V> serializableMap) {
    return serializableMap.entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, e -> serializeAsMap(e.getValue())));
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
          .filter(indexTemplateItem -> indexTemplateItem.indexTemplate().template() != null)
          .collect(
              Collectors.toMap(
                  IndexTemplateItem::name, item -> item.indexTemplate().template().mappings()));
    } else {
      throw new IndexSchemaValidationException(
          "Invalid mapping source provided must be either INDEX or INDEX_TEMPLATE");
    }
  }

  private PutMappingRequest putMappingRequest(
      final IndexDescriptor indexDescriptor, final Collection<IndexMappingProperty> newProperties) {

    final var opensearchProperties =
        newProperties.stream()
            .map(p -> p.toOpensearchProperty(client._transport().jsonpMapper(), objectMappper))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

    return new PutMappingRequest.Builder()
        .index(indexDescriptor.getAlias())
        .properties(opensearchProperties)
        .build();
  }

  private PutIndexTemplateRequest putIndexTemplateRequest(
      final IndexTemplateDescriptor indexTemplateDescriptor, final IndexConfiguration settings) {

    try (final var templateFile =
        getClass().getResourceAsStream(indexTemplateDescriptor.getMappingsClasspathFilename())) {

      final var templateFields =
          deserializeJson(
              IndexTemplateMapping._DESERIALIZER,
              utils.new SchemaSettingsAppender(templateFile)
                  .withNumberOfShards(settings.getNumberOfShards())
                  .withNumberOfReplicas(settings.getNumberOfReplicas())
                  .build());

      return PutIndexTemplateRequest.of(
          b ->
              b.name(indexTemplateDescriptor.getTemplateName())
                  .indexPatterns(indexTemplateDescriptor.getIndexPattern())
                  .template(
                      t ->
                          t.aliases(indexTemplateDescriptor.getAlias(), a -> a)
                              .mappings(templateFields.mappings())
                              .settings(templateFields.settings()))
                  .priority(settings.getTemplatePriority())
                  .composedOf(indexTemplateDescriptor.getComposedOf()));
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
        getClass().getResourceAsStream(indexTemplateDescriptor.getMappingsClasspathFilename())) {
      final var currentTemplate = getIndexTemplateState(indexTemplateDescriptor);
      final var configuredSettings =
          utils.new SchemaSettingsAppender(templateFile)
              .withNumberOfShards(indexConfiguration.getNumberOfShards())
              .withNumberOfReplicas(indexConfiguration.getNumberOfReplicas());
      final var configuredPriority = indexConfiguration.getTemplatePriority();
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

  private PutMappingRequest putMetaMappingRequest(
      final String indexName, final Map<String, Object> meta) {
    final var jsonMeta =
        meta.entrySet().stream()
            .collect(Collectors.toMap(Entry::getKey, e -> JsonData.of(e.getValue())));
    return new PutMappingRequest.Builder().index(indexName).meta(jsonMeta).build();
  }

  private boolean areTemplateSettingsEqualToConfigured(
      final IndexTemplate currentTemplate,
      final SchemaSettingsAppender configuredSettings,
      final Integer configuredPriority) {
    return Objects.equals(
            convertValue(configuredPriority, Long::valueOf), currentTemplate.priority())
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
        getClass().getResourceAsStream(indexDescriptor.getMappingsClasspathFilename())) {

      final var templateFields =
          deserializeJson(
              IndexTemplateMapping._DESERIALIZER,
              utils.new SchemaSettingsAppender(templateFile)
                  .withNumberOfShards(settings.getNumberOfShards())
                  .withNumberOfReplicas(settings.getNumberOfReplicas())
                  .build());

      return new CreateIndexRequest.Builder()
          .index(indexDescriptor.getFullQualifiedName())
          .aliases(indexDescriptor.getAlias(), a -> a.isWriteIndex(false))
          .mappings(templateFields.mappings())
          .settings(templateFields.settings())
          .build();

    } catch (final IOException e) {
      throw new SearchEngineException(
          "Failed to load file: "
              + indexDescriptor.getMappingsClasspathFilename()
              + " from classpath",
          e);
    }
  }

  private <T> T deserializeJson(final JsonpDeserializer<T> deserializer, final InputStream json) {
    final JsonbJsonpMapper mapper = new JsonbJsonpMapper();

    try (final var parser = mapper.jsonProvider().createParser(json)) {
      return deserializer.deserialize(parser, mapper);
    }
  }

  @Override
  public void close() {
    if (client != null) {
      try {
        client._transport().close();
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  record ISMPolicyState(boolean exists, int seqNo, int primaryTerm) {

    public ISMPolicyState(final int seqNo, final int primaryTerm) {
      this(true, seqNo, primaryTerm);
    }

    static ISMPolicyState empty() {
      return new ISMPolicyState(false, 0, 0);
    }
  }
}
