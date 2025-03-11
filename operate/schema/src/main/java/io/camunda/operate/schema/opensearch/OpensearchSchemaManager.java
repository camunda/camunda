/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.opensearch;

import static io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor.SCHEMA_FOLDER_OPENSEARCH;
import static io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor.formatIndexPrefix;
import static io.camunda.webapps.schema.descriptors.ComponentNames.OPERATE;
import static java.lang.String.format;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.config.operate.OperateOpensearchProperties;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.IndexMapping;
import io.camunda.operate.schema.IndexMapping.IndexMappingProperty;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.LambdaExceptionUtil;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.cluster.PutComponentTemplateRequest;
import org.opensearch.client.opensearch.indices.Alias;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest.Builder;
import org.opensearch.client.opensearch.indices.PutMappingRequest;
import org.opensearch.client.opensearch.indices.put_index_template.IndexTemplateMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component("schemaManager")
@Profile("!test")
@Conditional(OpensearchCondition.class)
public class OpensearchSchemaManager implements SchemaManager {
  public static final String SETTINGS = "settings";
  public static final String MAPPINGS = "mappings";
  private static final String SCHEMA_OPENSEARCH_CREATE_POLICY_JSON =
      SCHEMA_FOLDER_OPENSEARCH + "/policy/%s.json";
  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchSchemaManager.class);
  protected final OperateProperties operateProperties;

  protected final RichOpenSearchClient richOpenSearchClient;

  private final ObjectMapper objectMapper;
  private final JsonbJsonpMapper jsonpMapper = new JsonbJsonpMapper();

  private final List<IndexTemplateDescriptor> templateDescriptors;

  private final List<IndexDescriptor> indexDescriptors;

  @Autowired
  public OpensearchSchemaManager(
      final OperateProperties operateProperties,
      final RichOpenSearchClient richOpenSearchClient,
      final List<IndexTemplateDescriptor> templateDescriptors,
      final List<IndexDescriptor> indexDescriptors,
      @Qualifier("operateObjectMapper") final ObjectMapper objectMapper) {
    super();
    this.operateProperties = operateProperties;
    this.richOpenSearchClient = richOpenSearchClient;
    this.templateDescriptors = templateDescriptors;
    this.objectMapper = objectMapper;
    this.indexDescriptors =
        indexDescriptors.stream()
            .filter(indexDescriptor -> !(indexDescriptor instanceof IndexTemplateDescriptor))
            .toList();
  }

  @Override
  public void createSchema() {
    createDefaults();
    createTemplates();
    createIndices();
  }

  @Override
  public void createDefaults() {
    final OperateOpensearchProperties osConfig = operateProperties.getOpensearch();

    final String settingsTemplateName = settingsTemplateName();
    LOGGER.info(
        "Create default settings '{}' with {} shards and {} replicas per index.",
        settingsTemplateName,
        osConfig.getNumberOfShards(),
        osConfig.getNumberOfReplicas());

    final IndexSettings settings = getDefaultIndexSettings();
    richOpenSearchClient
        .template()
        .createComponentTemplateWithRetries(
            new PutComponentTemplateRequest.Builder()
                .name(settingsTemplateName)
                .template(t -> t.settings(settings))
                .build());
  }

  @Override
  public void createIndex(
      final IndexDescriptor indexDescriptor, final String indexClasspathResource) {
    try {
      final InputStream description =
          OpensearchSchemaManager.class.getResourceAsStream(indexClasspathResource);
      final var request =
          createIndexFromJson(
              StreamUtils.copyToString(description, StandardCharsets.UTF_8),
              indexDescriptor.getFullQualifiedName(),
              Map.of(indexDescriptor.getAlias(), new Alias.Builder().isWriteIndex(false).build()),
              getIndexSettings(indexDescriptor.getIndexName()));
      createIndex(request, indexDescriptor.getFullQualifiedName());
    } catch (final Exception e) {
      throw new OperateRuntimeException(
          "Could not create index " + indexDescriptor.getIndexName(), e);
    }
  }

  @Override
  public void createTemplate(
      final IndexTemplateDescriptor templateDescriptor, final String templateClasspathResource) {
    final String json =
        templateClasspathResource != null
            ? readTemplateJson(templateClasspathResource)
            : readTemplateJson(templateDescriptor.getSchemaClasspathFilename());

    final PutIndexTemplateRequest indexTemplateRequest =
        prepareIndexTemplateRequest(templateDescriptor, json);
    putIndexTemplate(indexTemplateRequest);

    // This is necessary, otherwise operate won't find indexes at startup
    final String indexName = templateDescriptor.getFullQualifiedName();

    final var request =
        createIndexFromJson(
            json,
            templateDescriptor.getFullQualifiedName(),
            Map.of(templateDescriptor.getAlias(), new Alias.Builder().isWriteIndex(false).build()),
            getIndexSettings(templateDescriptor.getIndexName()));
    createIndex(request, indexName);
  }

  @Override
  public boolean setIndexSettingsFor(final Map<String, ?> settings, final String indexPattern) {
    final IndexSettings indexSettings =
        new IndexSettings.Builder()
            .refreshInterval(ri -> ri.time(((String) settings.get(REFRESH_INTERVAL))))
            .numberOfReplicas(String.valueOf(settings.get(NUMBERS_OF_REPLICA)))
            .build();
    return richOpenSearchClient.index().setIndexSettingsFor(indexSettings, indexPattern);
  }

  @Override
  public String getOrDefaultRefreshInterval(final String indexName, final String defaultValue) {
    return richOpenSearchClient.index().getOrDefaultRefreshInterval(indexName, defaultValue);
  }

  @Override
  public String getOrDefaultNumbersOfReplica(final String indexName, final String defaultValue) {
    return richOpenSearchClient.index().getOrDefaultNumbersOfReplica(indexName, defaultValue);
  }

  @Override
  public void refresh(final String indexPattern) {
    richOpenSearchClient.index().refreshWithRetries(indexPattern);
  }

  @Override
  public boolean isHealthy() {
    if (operateProperties.getOpensearch().isHealthCheckEnabled()) {
      return richOpenSearchClient.cluster().isHealthy();
    } else {
      LOGGER.warn("OpenSearch cluster health check is disabled.");
      return true;
    }
  }

  @Override
  public Set<String> getIndexNames(final String indexPattern) {
    return richOpenSearchClient.index().getIndexNamesWithRetries(indexPattern);
  }

  @Override
  public Set<String> getAliasesNames(final String indexPattern) {
    return richOpenSearchClient.index().getAliasesNamesWithRetries(indexPattern);
  }

  @Override
  public long getNumberOfDocumentsFor(final String... indexPatterns) {
    return richOpenSearchClient.index().getNumberOfDocumentsWithRetries(indexPatterns);
  }

  @Override
  public boolean deleteIndicesFor(final String indexPattern) {
    return richOpenSearchClient.index().deleteIndicesWithRetries(indexPattern);
  }

  @Override
  public boolean deleteTemplatesFor(final String deleteTemplatePattern) {
    return richOpenSearchClient.template().deleteTemplatesWithRetries(deleteTemplatePattern);
  }

  @Override
  public void removePipeline(final String pipelineName) {
    richOpenSearchClient.pipeline().removePipelineWithRetries(pipelineName);
  }

  @Override
  public boolean addPipeline(final String name, final String pipelineDefinition) {
    return richOpenSearchClient.pipeline().addPipelineWithRetries(name, pipelineDefinition);
  }

  @Override
  public Map<String, String> getIndexSettingsFor(final String indexName, final String... fields) {
    final IndexSettings indexSettings =
        richOpenSearchClient.index().getIndexSettingsWithRetries(indexName);
    final var result = new HashMap<String, String>();
    for (final String field : fields) {
      if (field.equals(REFRESH_INTERVAL)) {
        final var refreshInterval = indexSettings.refreshInterval();
        result.put(REFRESH_INTERVAL, refreshInterval != null ? refreshInterval.time() : null);
      }
      if (field.equals(NUMBERS_OF_REPLICA)) {
        result.put(NUMBERS_OF_REPLICA, indexSettings.numberOfReplicas());
      }
    }
    return result;
  }

  @Override
  public String getIndexPrefix() {
    return operateProperties.getOpensearch().getIndexPrefix();
  }

  @Override
  public Map<String, IndexMapping> getIndexMappings(final String indexNamePattern) {
    return richOpenSearchClient.index().getIndexMappings(indexNamePattern);
  }

  /**
   * @deprecated schema manager is happening in Zeebe exporter now
   */
  @Deprecated
  @Override
  public void updateSchema(final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields) {
    for (final Map.Entry<IndexDescriptor, Set<IndexMappingProperty>> indexNewFields :
        newFields.entrySet()) {
      if (indexNewFields.getKey() instanceof IndexTemplateDescriptor) {
        LOGGER.info(
            "Update template: "
                + ((IndexTemplateDescriptor) indexNewFields.getKey()).getTemplateName());
        final IndexTemplateDescriptor templateDescriptor =
            (IndexTemplateDescriptor) indexNewFields.getKey();
        final String json = readTemplateJson(templateDescriptor.getSchemaClasspathFilename());
        final PutIndexTemplateRequest indexTemplateRequest =
            prepareIndexTemplateRequest(templateDescriptor, json);
        putIndexTemplate(indexTemplateRequest, true);
      }

      final Map<String, Property> properties;
      try (final JsonParser jsonParser =
          JsonProvider.provider()
              .createParser(
                  new StringReader(
                      IndexMappingProperty.toJsonString(
                          indexNewFields.getValue(), objectMapper)))) {
        properties =
            JsonpDeserializer.stringMapDeserializer(Property._DESERIALIZER)
                .deserialize(jsonParser, jsonpMapper);
      }
      final PutMappingRequest request =
          new PutMappingRequest.Builder()
              .index(indexNewFields.getKey().getAlias())
              .properties(properties)
              .build();
      LOGGER.info(
          String.format(
              "Index alias: %s. New fields will be added: %s",
              indexNewFields.getKey().getAlias(), indexNewFields.getValue()));
      richOpenSearchClient.index().putMapping(request);
    }
  }

  @Override
  public IndexMapping getExpectedIndexFields(final IndexDescriptor indexDescriptor) {
    final InputStream description =
        OpensearchSchemaManager.class.getResourceAsStream(
            indexDescriptor.getSchemaClasspathFilename());
    try {
      final String currentVersionSchema =
          StreamUtils.copyToString(description, StandardCharsets.UTF_8);
      final TypeReference<HashMap<String, Object>> type = new TypeReference<>() {};
      final Map<String, Object> mappings =
          (Map<String, Object>) objectMapper.readValue(currentVersionSchema, type).get("mappings");
      final Map<String, Object> properties = (Map<String, Object>) mappings.get("properties");
      final String dynamic = (String) mappings.get("dynamic");
      return new IndexMapping()
          .setIndexName(indexDescriptor.getIndexName())
          .setDynamic(dynamic)
          .setProperties(
              properties.entrySet().stream()
                  .map(
                      LambdaExceptionUtil.rethrowFunction(
                          entry ->
                              new IndexMappingProperty()
                                  .setName(entry.getKey())
                                  .setTypeDefinition(entry.getValue())))
                  .collect(Collectors.toSet()));
    } catch (final IOException e) {
      throw new OperateRuntimeException(e);
    }
  }

  private IndexSettings getDefaultIndexSettings() {
    final OperateOpensearchProperties osConfig = operateProperties.getOpensearch();
    return new IndexSettings.Builder()
        .numberOfShards(String.valueOf(osConfig.getNumberOfShards()))
        .numberOfReplicas(String.valueOf(osConfig.getNumberOfReplicas()))
        .build();
  }

  private IndexSettings getIndexSettings(final String indexName) {
    final OperateOpensearchProperties osConfig = operateProperties.getOpensearch();
    final var shards =
        osConfig
            .getNumberOfShardsForIndices()
            .getOrDefault(indexName, osConfig.getNumberOfShards());
    final var replicas =
        osConfig
            .getNumberOfReplicasForIndices()
            .getOrDefault(indexName, osConfig.getNumberOfReplicas());

    return new IndexSettings.Builder()
        .numberOfShards(String.valueOf(shards))
        .numberOfReplicas(String.valueOf(replicas))
        .build();
  }

  private String settingsTemplateName() {
    return String.format("%s%s_template", formatIndexPrefix(getIndexPrefix()), OPERATE);
  }

  private void createTemplates() {
    templateDescriptors.forEach(this::createTemplate);
  }

  private IndexSettings templateSettings(final IndexTemplateDescriptor indexDescriptor) {
    final var shards =
        operateProperties
            .getOpensearch()
            .getNumberOfShardsForIndices()
            .get(indexDescriptor.getIndexName());

    final var replicas =
        operateProperties
            .getOpensearch()
            .getNumberOfReplicasForIndices()
            .get(indexDescriptor.getIndexName());

    if (shards != null || replicas != null) {
      final var indexSettingsBuilder = new IndexSettings.Builder();

      if (shards != null) {
        indexSettingsBuilder.numberOfShards(shards.toString());
      }

      if (replicas != null) {
        indexSettingsBuilder.numberOfReplicas(replicas.toString());
      }

      return indexSettingsBuilder.build();
    }
    return null;
  }

  private void createTemplate(final IndexTemplateDescriptor templateDescriptor) {

    final String json = readTemplateJson(templateDescriptor.getSchemaClasspathFilename());

    final PutIndexTemplateRequest indexTemplateRequest =
        prepareIndexTemplateRequest(templateDescriptor, json);
    putIndexTemplate(indexTemplateRequest);

    // This is necessary, otherwise operate won't find indexes at startup
    final String indexName = templateDescriptor.getFullQualifiedName();

    final var request =
        createIndexFromJson(
            json,
            templateDescriptor.getFullQualifiedName(),
            Map.of(templateDescriptor.getAlias(), new Alias.Builder().isWriteIndex(false).build()),
            getIndexSettings(templateDescriptor.getIndexName()));
    createIndex(request, indexName);
  }

  private static String readTemplateJson(final String classPathResourceName) {
    try {
      // read settings and mappings
      final InputStream description =
          OpensearchSchemaManager.class.getResourceAsStream(classPathResourceName);
      final String json = StreamUtils.copyToString(description, StandardCharsets.UTF_8);
      return json;
    } catch (final Exception e) {
      throw new OperateRuntimeException(
          "Exception occurred when reading template JSON: " + e.getMessage(), e);
    }
  }

  private PutIndexTemplateRequest prepareIndexTemplateRequest(
      final IndexTemplateDescriptor templateDescriptor, final String json) {
    final var templateSettings = templateSettings(templateDescriptor);
    final var templateBuilder =
        new IndexTemplateMapping.Builder()
            .aliases(templateDescriptor.getAlias(), new Alias.Builder().build());

    try {

      final var indexAsJSONNode = objectMapper.readTree(new StringReader(json));

      final var customSettings = getCustomSettings(templateSettings, indexAsJSONNode);
      final var mappings = getMappings(indexAsJSONNode.get(MAPPINGS));

      final IndexTemplateMapping template =
          templateBuilder.mappings(mappings).settings(customSettings).build();

      final PutIndexTemplateRequest request =
          new Builder()
              .name(templateDescriptor.getTemplateName())
              .indexPatterns(templateDescriptor.getIndexPattern())
              .template(template)
              .composedOf(settingsTemplateName())
              .build();
      return request;
    } catch (final Exception ex) {
      throw new OperateRuntimeException(ex);
    }
  }

  private void putIndexTemplate(final PutIndexTemplateRequest request) {
    putIndexTemplate(request, false);
  }

  private void putIndexTemplate(final PutIndexTemplateRequest request, final boolean overwrite) {
    final boolean created =
        richOpenSearchClient.template().createTemplateWithRetries(request, overwrite);
    if (created) {
      LOGGER.debug("Template [{}] was successfully created", request.name());
    } else {
      LOGGER.debug("Template [{}] was NOT created", request.name());
    }
  }

  private void createIndex(final CreateIndexRequest createIndexRequest, final String indexName) {
    final boolean created = richOpenSearchClient.index().createIndexWithRetries(createIndexRequest);
    if (created) {
      LOGGER.debug("Index [{}] was successfully created", indexName);
    } else {
      LOGGER.debug("Index [{}] was NOT created", indexName);
    }
  }

  private void createIndex(final IndexDescriptor indexDescriptor) {
    createIndex(indexDescriptor, indexDescriptor.getSchemaClasspathFilename());
  }

  /** Reads mappings and optionally settings from json file */
  private CreateIndexRequest createIndexFromJson(
      final String json,
      final String indexName,
      final Map<String, Alias> aliases,
      final IndexSettings settings) {
    try {
      final var indexAsJSONNode = objectMapper.readTree(new StringReader(json));

      final var customSettings = getCustomSettings(settings, indexAsJSONNode);
      final var mappings = getMappings(indexAsJSONNode.get(MAPPINGS));

      return new CreateIndexRequest.Builder()
          .index(indexName)
          .aliases(aliases)
          .settings(customSettings)
          .mappings(mappings)
          .build();
    } catch (final Exception e) {
      throw new OperateRuntimeException("Could not load schema for " + indexName, e);
    }
  }

  private TypeMapping getMappings(final JsonNode mappingsAsJSON) {
    final JsonParser jsonParser =
        JsonProvider.provider().createParser(new StringReader(mappingsAsJSON.toPrettyString()));
    return TypeMapping._DESERIALIZER.deserialize(jsonParser, jsonpMapper);
  }

  private IndexSettings getCustomSettings(
      final IndexSettings defaultSettings, final JsonNode indexAsJSONNode) {
    if (indexAsJSONNode.has(SETTINGS)) {
      final var settingsJSON = indexAsJSONNode.get(SETTINGS);
      final JsonParser jsonParser =
          JsonProvider.provider().createParser(new StringReader(settingsJSON.toPrettyString()));
      final var updatedSettings = IndexSettings._DESERIALIZER.deserialize(jsonParser, jsonpMapper);
      return new IndexSettings.Builder()
          .index(defaultSettings)
          .analysis(updatedSettings.analysis())
          .build();
    }
    return defaultSettings;
  }

  private void createIndices() {
    indexDescriptors.forEach(this::createIndex);
  }

  private Optional<Map<String, Object>> fetchIsmPolicy() {
    try {
      return Optional.ofNullable(
          richOpenSearchClient.ism().getPolicy(OPERATE_DELETE_ARCHIVED_INDICES));
    } catch (final OpenSearchException e) {
      if (e.status() != 404) {
        LOGGER.error(format("Failed to get policy %s", OPERATE_DELETE_ARCHIVED_INDICES), e);
      }
      return Optional.empty();
    }
  }
}
