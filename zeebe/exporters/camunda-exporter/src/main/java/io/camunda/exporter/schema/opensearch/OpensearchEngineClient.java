/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema.opensearch;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.exporter.SchemaResourceSerializer;
import io.camunda.exporter.config.ExporterConfiguration.IndexSettings;
import io.camunda.exporter.exceptions.ElasticsearchExporterException;
import io.camunda.exporter.exceptions.IndexSchemaValidationException;
import io.camunda.exporter.exceptions.OpensearchExporterException;
import io.camunda.exporter.schema.IndexMapping;
import io.camunda.exporter.schema.IndexMappingProperty;
import io.camunda.exporter.schema.MappingSource;
import io.camunda.exporter.schema.SearchEngineClient;
import io.camunda.exporter.utils.SearchEngineClientUtils;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.operate.index.ImportPositionIndex;
import io.camunda.webapps.schema.entities.operate.ImportPositionEntity;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.jackson.JacksonJsonpGenerator;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.generic.Body;
import org.opensearch.client.opensearch.generic.Request;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest;
import org.opensearch.client.opensearch.indices.PutIndicesSettingsRequest;
import org.opensearch.client.opensearch.indices.PutMappingRequest;
import org.opensearch.client.opensearch.indices.get_index_template.IndexTemplateItem;
import org.opensearch.client.opensearch.indices.get_index_template.IndexTemplateSummary;
import org.opensearch.client.opensearch.indices.put_index_template.IndexTemplateMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpensearchEngineClient implements SearchEngineClient {
  private static final Logger LOG = LoggerFactory.getLogger(OpensearchEngineClient.class);
  private static final String OPERATE_DELETE_ARCHIVED_POLICY =
      "/schema/opensearch/create/policy/operate_delete_archived_indices.json";
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
  public void createIndex(final IndexDescriptor indexDescriptor, final IndexSettings settings) {
    final CreateIndexRequest request = createIndexRequest(indexDescriptor, settings);

    try {
      client.indices().create(request);
      LOG.debug("Index [{}] was successfully created", indexDescriptor.getFullQualifiedName());
    } catch (final IOException ioe) {
      final var errMsg =
          String.format("Index [%s] was not created", indexDescriptor.getFullQualifiedName());
      LOG.error(errMsg, ioe);
      throw new OpensearchExporterException(errMsg, ioe);
    } catch (final OpenSearchException ose) {
      if ("resource_already_exists_exception".equals(ose.error().type())) {
        // we can ignore already exists exceptions
        // as this means the index was created by another exporter on a different partition
        final var warnMsg =
            String.format(
                "Expected to create index [%s], but already exist. Will continue, likely was created by different partition (exporter).",
                indexDescriptor.getFullQualifiedName());
        LOG.debug(warnMsg, ose);
        return;
      }

      final var errMsg =
          String.format("Index [%s] was not created", indexDescriptor.getFullQualifiedName());
      LOG.error(errMsg, ose);
      throw new OpensearchExporterException(errMsg, ose);
    }
  }

  @Override
  public void createIndexTemplate(
      final IndexTemplateDescriptor templateDescriptor,
      final IndexSettings settings,
      final boolean create) {

    final PutIndexTemplateRequest request = putIndexTemplateRequest(templateDescriptor, settings);

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
      throw new OpensearchExporterException(errMsg, e);
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
      throw new OpensearchExporterException(errMsg, e);
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
      throw new OpensearchExporterException(
          String.format(
              "Failed retrieving mappings from index/index templates with pattern [%s]",
              namePattern),
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
              utils.listIndices(indexDescriptors));
      LOG.error(errMsg, e);
      throw new OpensearchExporterException(errMsg, e);
    }
  }

  @Override
  public void putIndexLifeCyclePolicy(final String policyName, final String deletionMinAge) {
    final var request = createIndexStateManagementPolicy(policyName, deletionMinAge);

    try (final var response = client.generic().execute(request)) {
      if (response.getStatus() / 100 != 2) {
        throw new OpensearchExporterException(
            String.format(
                "Creating index state management policy [%s] with min_deletion_age [%s] failed. Http response = [%s]",
                policyName, deletionMinAge, response.getBody().get().bodyAsString()));
      }

    } catch (final IOException | OpenSearchException e) {
      final var errMsg =
          String.format("Failed to create index state management policy [%s]", policyName);
      LOG.error(errMsg, e);
      throw new OpensearchExporterException(errMsg, e);
    }
  }

  @Override
  public boolean importersCompleted(
      final int partitionId, final List<IndexDescriptor> importPositionIndices) {
    final var allImportPositionDocuments =
        allImportPositionDocuments(partitionId, importPositionIndices);
    try {
      final var allRecordReaderStatuses =
          client.search(allImportPositionDocuments, ImportPositionEntity.class).hits().hits();

      // brand new install no need to wait for importers to complete
      if (allRecordReaderStatuses.isEmpty()) {
        return true;
      }

      return allRecordReaderStatuses.stream().allMatch(status -> status.source().getCompleted());
    } catch (final IOException e) {
      final var errMsg =
          String.format(
              "Failed to search documents in the import position index for partition [%s]",
              partitionId);
      LOG.error(errMsg, e);
      return false;
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
      final IndexTemplateDescriptor indexTemplateDescriptor, final IndexSettings currentSettings) {
    final var updateIndexTemplateSettingsRequest =
        updateTemplateSettings(indexTemplateDescriptor, currentSettings);

    try {
      client.indices().putIndexTemplate(updateIndexTemplateSettingsRequest);
    } catch (final IOException | ElasticsearchException e) {
      throw new ElasticsearchExporterException(
          String.format(
              "Expected to update index template settings '%s' with '%s', but failed ",
              indexTemplateDescriptor.getTemplateName(), updateIndexTemplateSettingsRequest),
          e);
    }
  }

  private SearchRequest allImportPositionDocuments(
      final int partitionId, final List<IndexDescriptor> importPositionIndices) {
    final var importPositionIndicesNames =
        importPositionIndices.stream().map(IndexDescriptor::getFullQualifiedName).toList();
    return new SearchRequest.Builder()
        .index(importPositionIndicesNames)
        .size(100)
        .query(
            q ->
                q.term(
                    t ->
                        t.field(ImportPositionIndex.PARTITION_ID)
                            .value(v -> v.longValue(partitionId))))
        .build();
  }

  public Request createIndexStateManagementPolicy(
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

      return Requests.builder()
          .method("PUT")
          .endpoint("_plugins/_ism/policies/" + policyName)
          .body(Body.from(policy, "application/json"))
          .build();

    } catch (final IOException e) {
      throw new OpensearchExporterException(
          "Failed to deserialize policy file " + OPERATE_DELETE_ARCHIVED_POLICY, e);
    }
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
        .index(utils.listIndices(indexDescriptors))
        .settings(settings)
        .build();
  }

  private String dynamicFromMappings(final TypeMapping mapping) {
    final var dynamic = mapping.dynamic();
    return dynamic == null ? "strict" : dynamic.toString().toLowerCase();
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
                    .typeDefinition(propertyToMap(p.getValue()))
                    .build())
        .collect(Collectors.toSet());
  }

  private Map<String, Object> propertyToMap(final Property property) {
    try {
      return schemaResourceSerializer.serialize(
          (JacksonJsonpGenerator::new),
          (jacksonJsonpGenerator) ->
              property.serialize(jacksonJsonpGenerator, client._transport().jsonpMapper()));
    } catch (final IOException e) {
      throw new OpensearchExporterException(
          String.format("Failed to serialize property [%s]", property.toString()), e);
    }
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
        .index(indexDescriptor.getFullQualifiedName())
        .properties(opensearchProperties)
        .build();
  }

  private PutIndexTemplateRequest putIndexTemplateRequest(
      final IndexTemplateDescriptor indexTemplateDescriptor, final IndexSettings settings) {

    try (final var templateFile =
        getClass().getResourceAsStream(indexTemplateDescriptor.getMappingsClasspathFilename())) {

      final var templateFields =
          deserializeJson(
              IndexTemplateMapping._DESERIALIZER,
              utils.new SchemaSettingsAppender(templateFile)
                  .withNumberOfShards(settings.getNumberOfShards())
                  .withNumberOfReplicas(settings.getNumberOfReplicas())
                  .build());

      return new PutIndexTemplateRequest.Builder()
          .name(indexTemplateDescriptor.getTemplateName())
          .indexPatterns(indexTemplateDescriptor.getIndexPattern())
          .template(
              t ->
                  t.aliases(indexTemplateDescriptor.getAlias(), a -> a)
                      .mappings(templateFields.mappings())
                      .settings(templateFields.settings()))
          .composedOf(indexTemplateDescriptor.getComposedOf())
          .build();
    } catch (final IOException e) {
      throw new OpensearchExporterException(
          "Failed to load file "
              + indexTemplateDescriptor.getMappingsClasspathFilename()
              + " from classpath.",
          e);
    }
  }

  private PutIndexTemplateRequest updateTemplateSettings(
      final IndexTemplateDescriptor indexTemplateDescriptor, final IndexSettings currentSettings) {
    try (final var templateFile =
        getClass().getResourceAsStream(indexTemplateDescriptor.getMappingsClasspathFilename())) {
      final var updatedTemplateSettings =
          deserializeJson(
                  IndexTemplateMapping._DESERIALIZER,
                  utils.new SchemaSettingsAppender(templateFile)
                      .withNumberOfShards(currentSettings.getNumberOfShards())
                      .withNumberOfReplicas(currentSettings.getNumberOfReplicas())
                      .build())
              .settings();

      final var currentIndexTemplateState = getIndexTemplateState(indexTemplateDescriptor);

      return new PutIndexTemplateRequest.Builder()
          .name(indexTemplateDescriptor.getTemplateName())
          .indexPatterns(indexTemplateDescriptor.getIndexPattern())
          .template(
              t ->
                  t.settings(updatedTemplateSettings)
                      .mappings(currentIndexTemplateState.mappings())
                      .aliases(currentIndexTemplateState.aliases()))
          .build();

    } catch (final IOException e) {
      throw new OpensearchExporterException(
          "Failed to load file "
              + indexTemplateDescriptor.getMappingsClasspathFilename()
              + " from classpath.",
          e);
    }
  }

  private IndexTemplateSummary getIndexTemplateState(
      final IndexTemplateDescriptor indexTemplateDescriptor) {
    try {
      return client
          .indices()
          .getIndexTemplate(r -> r.name(indexTemplateDescriptor.getTemplateName()))
          .indexTemplates()
          .getFirst()
          .indexTemplate()
          .template();
    } catch (final IOException e) {
      throw new OpensearchExporterException(
          String.format(
              "Failed to retrieve index template '%s'", indexTemplateDescriptor.getTemplateName()),
          e);
    }
  }

  private CreateIndexRequest createIndexRequest(
      final IndexDescriptor indexDescriptor, final IndexSettings settings) {

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
      throw new OpensearchExporterException(
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
}
