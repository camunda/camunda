/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.schema;

import static io.camunda.optimize.service.db.DatabaseConstants.INDEX_ALREADY_EXISTS_EXCEPTION_TYPE;
import static io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager.INDEX_EXIST_BATCH_SIZE;
import static io.camunda.optimize.service.db.os.schema.OpenSearchIndexSettingsBuilder.buildDynamicSettings;
import static io.camunda.optimize.service.util.mapper.ObjectMapperFactory.OPTIMIZE_MAPPER;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.util.stream.Collectors.toSet;

import co.elastic.clients.util.Pair;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import io.camunda.optimize.service.db.os.MappingMetadataUtilOS;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.schema.index.AlertIndexOS;
import io.camunda.optimize.service.db.os.schema.index.BusinessKeyIndexOS;
import io.camunda.optimize.service.db.os.schema.index.CollectionIndexOS;
import io.camunda.optimize.service.db.os.schema.index.DashboardIndexOS;
import io.camunda.optimize.service.db.os.schema.index.DashboardShareIndexOS;
import io.camunda.optimize.service.db.os.schema.index.DecisionDefinitionIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ExternalProcessVariableIndexOS;
import io.camunda.optimize.service.db.os.schema.index.InstantPreviewDashboardMetadataIndexOS;
import io.camunda.optimize.service.db.os.schema.index.MetadataIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ProcessDefinitionIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ProcessOverviewIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ReportShareIndexOS;
import io.camunda.optimize.service.db.os.schema.index.SettingsIndexOS;
import io.camunda.optimize.service.db.os.schema.index.TenantIndexOS;
import io.camunda.optimize.service.db.os.schema.index.TerminatedUserSessionIndexOS;
import io.camunda.optimize.service.db.os.schema.index.VariableLabelIndexOS;
import io.camunda.optimize.service.db.os.schema.index.VariableUpdateInstanceIndexOS;
import io.camunda.optimize.service.db.os.schema.index.index.PositionBasedImportIndexOS;
import io.camunda.optimize.service.db.os.schema.index.index.TimestampBasedImportIndexOS;
import io.camunda.optimize.service.db.os.schema.index.report.CombinedReportIndexOS;
import io.camunda.optimize.service.db.os.schema.index.report.SingleDecisionReportIndexOS;
import io.camunda.optimize.service.db.os.schema.index.report.SingleProcessReportIndexOS;
import io.camunda.optimize.service.db.schema.DatabaseSchemaManager;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.json.jackson.JacksonJsonpGenerator;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.indices.Alias;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.GetIndicesSettingsRequest;
import org.opensearch.client.opensearch.indices.GetIndicesSettingsResponse;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.IndexSettings.Builder;
import org.opensearch.client.opensearch.indices.PutIndicesSettingsRequest;
import org.opensearch.client.opensearch.indices.PutIndicesSettingsResponse;
import org.opensearch.client.opensearch.indices.PutMappingRequest;
import org.opensearch.client.opensearch.indices.PutMappingResponse;
import org.opensearch.client.opensearch.indices.PutTemplateRequest;
import org.opensearch.client.opensearch.indices.put_index_template.IndexTemplateMapping;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class OpenSearchSchemaManager
    extends DatabaseSchemaManager<OptimizeOpenSearchClient, IndexSettings.Builder> {

  public static final String ALL_INDEXES = "_all";
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(OpenSearchSchemaManager.class);
  private final OpenSearchMetadataService metadataService;

  @Autowired
  public OpenSearchSchemaManager(
      final OpenSearchMetadataService metadataService,
      final ConfigurationService configurationService,
      final OptimizeIndexNameService indexNameService) {
    super(configurationService, indexNameService, new ArrayList<>(getAllNonDynamicMappings()));
    this.metadataService = metadataService;
  }

  public OpenSearchSchemaManager(
      final OpenSearchMetadataService metadataService,
      final ConfigurationService configurationService,
      final OptimizeIndexNameService indexNameService,
      final List<IndexMappingCreator<Builder>> mappings) {
    super(configurationService, indexNameService, mappings);
    this.metadataService = metadataService;
  }

  @Override
  public void validateDatabaseMetadata(final OptimizeOpenSearchClient osClient) {
    metadataService.validateMetadata(osClient);
  }

  @Override
  public void initializeSchema(final OptimizeOpenSearchClient osClient) {
    unblockIndices(osClient);
    if (!schemaExists(osClient)) {
      LOG.info("Initializing Optimize schema...");
      createOptimizeIndices(osClient);
      LOG.info("Optimize schema initialized successfully.");
    } else {
      updateAllMappingsAndDynamicSettings(osClient);
    }
    metadataService.initMetadataIfMissing(osClient);
  }

  @Override
  public boolean schemaExists(final OptimizeOpenSearchClient osClient) {
    return indicesExist(osClient, getMappings());
  }

  @Override
  public boolean indexExists(
      final OptimizeOpenSearchClient osClient,
      final IndexMappingCreator<IndexSettings.Builder> mapping) {
    return indicesExist(osClient, Collections.singletonList(mapping));
  }

  @Override
  public boolean indexExists(final OptimizeOpenSearchClient osClient, final String indexName) {
    return indicesExistWithNames(osClient, Collections.singletonList(indexName));
  }

  @Override
  public boolean indicesExist(
      final OptimizeOpenSearchClient osClient,
      final List<IndexMappingCreator<IndexSettings.Builder>> mappings) {
    return indicesExistWithNames(
        osClient, mappings.stream().map(IndexMappingCreator::getIndexName).toList());
  }

  @Override
  public void createIndexIfMissing(
      final OptimizeOpenSearchClient osClient,
      final IndexMappingCreator<IndexSettings.Builder> indexMapping) {
    createIndexIfMissing(osClient, indexMapping, Collections.emptySet());
  }

  @Override
  public void createIndexIfMissing(
      final OptimizeOpenSearchClient osClient,
      final IndexMappingCreator<IndexSettings.Builder> indexMapping,
      final Set<String> additionalReadOnlyAliases) {
    try {
      final boolean indexAlreadyExists = indexExists(osClient, indexMapping);
      if (!indexAlreadyExists) {
        createOrUpdateOptimizeIndex(osClient, indexMapping, additionalReadOnlyAliases);
      }
    } catch (final Exception e) {
      LOG.error("Failed ensuring index is present: {}", indexMapping.getIndexName(), e);
      throw e;
    }
  }

  @Override
  public void createOrUpdateOptimizeIndex(
      final OptimizeOpenSearchClient osClient,
      final IndexMappingCreator<IndexSettings.Builder> mapping,
      final Set<String> readOnlyAliases) {
    final Set<String> prefixedReadOnlyAliases =
        readOnlyAliases.stream()
            .map(indexNameService::getOptimizeIndexAliasForIndex)
            .collect(toSet());
    final String defaultAliasName =
        indexNameService.getOptimizeIndexAliasForIndex(mapping.getIndexName());
    final String suffixedIndexName = indexNameService.getOptimizeIndexNameWithVersion(mapping);
    final IndexSettings indexSettings = createIndexSettings(mapping);
    try {
      if (mapping.isCreateFromTemplate()) {
        // Creating template without alias and adding aliases manually to indices created from this
        // template to
        // ensure correct alias handling on rollover
        createOrUpdateTemplateWithAliases(
            osClient, mapping, defaultAliasName, prefixedReadOnlyAliases, indexSettings);
        createOptimizeIndexWithWriteAliasFromTemplate(
            osClient, suffixedIndexName, defaultAliasName);
      } else {
        createIndex(
            osClient,
            suffixedIndexName,
            defaultAliasName,
            prefixedReadOnlyAliases,
            mapping,
            indexSettings);
      }
    } catch (final OpenSearchException e) {
      if (e.status() == HTTP_BAD_REQUEST
          && e.getMessage().contains(INDEX_ALREADY_EXISTS_EXCEPTION_TYPE)) {
        LOG.debug(
            "index {} already exists, updating mapping and dynamic settings.", suffixedIndexName);
        updateDynamicSettingsAndMappings(osClient, mapping);
      } else {
        throw e;
      }
    } catch (final Exception e) {
      final String message = String.format("Could not create Index [%s]", suffixedIndexName);
      LOG.warn(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @Override
  public void deleteOptimizeIndex(
      final OptimizeOpenSearchClient dbClient,
      final IndexMappingCreator<IndexSettings.Builder> mapping) {
    dbClient.deleteIndex(mapping.getIndexName());
  }

  @Override
  public void createOrUpdateTemplateWithoutAliases(
      final OptimizeOpenSearchClient dbClient, final IndexMappingCreator<Builder> mappingCreator) {
    final String templateName =
        indexNameService.getOptimizeIndexTemplateNameWithVersion(mappingCreator);
    final IndexSettings indexSettings = createIndexSettings(mappingCreator);

    LOG.debug("Creating or updating template with name {}.", templateName);
    try {
      dbClient
          .getOpenSearchClient()
          .indices()
          .putTemplate(
              (PutTemplateRequest.of(
                  b ->
                      b.name(templateName)
                          .version((long) mappingCreator.getVersion())
                          .mappings(getMappings(mappingCreator.getSource().toString()))
                          .settings(convertToMap(indexSettings))
                          .indexPatterns(
                              Collections.singletonList(
                                  indexNameService
                                      .getOptimizeIndexNameWithVersionWithWildcardSuffix(
                                          mappingCreator))))));
    } catch (final Exception e) {
      final String message = String.format("Could not create or update template %s.", templateName);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @Override
  public void updateDynamicSettingsAndMappings(
      final OptimizeOpenSearchClient osClient,
      final IndexMappingCreator<IndexSettings.Builder> indexMapping) {
    updateIndexDynamicSettingsAndMappings(osClient, indexMapping);
    if (indexMapping.isCreateFromTemplate()) {
      updateTemplateDynamicSettingsAndMappings(osClient, indexMapping);
    }
  }

  // Needed for loading from JSON, opensearch doesn't provide something like .withJSON(...) for
  // index creation from files
  public static CreateIndexRequest createIndexFromJson(
      final String jsonMappings,
      final String indexName,
      final Map<String, Alias> aliases,
      final IndexSettings settings)
      throws OptimizeRuntimeException {
    final String jsonNew =
        "{\"mappings\": "
            + jsonMappings
                .replace("TypeMapping: ", "")
                .replace("\"match_mapping_type\":[\"string\"]", "\"match_mapping_type\":\"string\"")
                .replace("\"path_match\":[\"*\"]", "\"path_match\":\"*\"")
            + "}";
    try (final JsonParser jsonParser =
        JsonProvider.provider().createParser(new StringReader(jsonNew))) {
      final Supplier<CreateIndexRequest.Builder> builderSupplier =
          () ->
              new CreateIndexRequest.Builder().index(indexName).aliases(aliases).settings(settings);
      final ObjectDeserializer<CreateIndexRequest.Builder> deserializer =
          getDeserializerWithPreconfiguredBuilder(builderSupplier);
      try {
        return deserializer.deserialize(jsonParser, new JsonbJsonpMapper()).build();
      } catch (final Exception e) {
        throw new OptimizeRuntimeException("Could not load schema for " + indexName, e);
      }
    }
  }

  private boolean indicesExistWithNames(
      final OptimizeOpenSearchClient osClient, final List<String> indexNames) {
    return StreamSupport.stream(
            Iterables.partition(indexNames, INDEX_EXIST_BATCH_SIZE).spliterator(), true)
        .allMatch(
            indices -> {
              try {
                return osClient.getRichOpenSearchClient().index().indicesExist(indices);
              } catch (final IOException e) {
                final String message =
                    String.format(
                        "Could not check if [%s] index(es) already exist.",
                        String.join(",", indices));
                throw new OptimizeRuntimeException(message, e);
              }
            });
  }

  private void createOptimizeIndexWithWriteAliasFromTemplate(
      final OptimizeOpenSearchClient osClient,
      final String indexNameWithSuffix,
      final String aliasName) {
    LOG.info("Creating index {} from template with write alias {}", indexNameWithSuffix, aliasName);
    final CreateIndexRequest.Builder createIndexRequest =
        new CreateIndexRequest.Builder().index(indexNameWithSuffix);
    if (aliasName != null) {
      createIndexRequest.aliases(aliasName, new Alias.Builder().isWriteIndex(true).build());
    }
    try {
      createIndex(osClient, createIndexRequest.build(), indexNameWithSuffix);
    } catch (final IOException e) {
      final String message =
          String.format("Could not create index %s from template.", indexNameWithSuffix);
      LOG.warn(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private void createIndex(
      final OptimizeOpenSearchClient osClient,
      final CreateIndexRequest createIndexRequest,
      final String indexName)
      throws IOException {
    final boolean created =
        osClient.getRichOpenSearchClient().index().createIndex(createIndexRequest);
    if (created) {
      LOG.info("Index [{}] was successfully created", indexName);
    } else {
      LOG.info("Index [{}] was not created", indexName);
    }
  }

  private void createIndex(
      final OptimizeOpenSearchClient osClient,
      final String suffixedIndexName,
      final String defaultAliasName,
      final Set<String> prefixedReadOnlyAliases,
      final IndexMappingCreator<IndexSettings.Builder> mapping,
      final IndexSettings indexSettings)
      throws IOException {
    LOG.debug(
        "Creating Optimize Index with name {}, default alias {} and additional aliases {}",
        suffixedIndexName,
        defaultAliasName,
        prefixedReadOnlyAliases);

    final CreateIndexRequest request =
        createIndexFromJson(
            mapping.getSource().toString(),
            suffixedIndexName,
            createAliasMap(prefixedReadOnlyAliases, defaultAliasName),
            indexSettings);
    createIndex(osClient, request, suffixedIndexName);
  }

  // Needed for loading from JSON, opensearch doesn't provide something like .withJSON(...) for
  // index creation from files
  private PutMappingRequest updateMappingFromJson(final String jsonMappings, final String indexName)
      throws OptimizeRuntimeException {
    try (final JsonParser jsonParser =
        JsonProvider.provider()
            .createParser(
                new StringReader(
                    jsonMappings
                        .replace("TypeMapping: ", "")
                        .replace(
                            "\"match_mapping_type\":[\"string\"]",
                            "\"match_mapping_type\":\"string\"")
                        .replace("\"path_match\":[\"*\"]", "\"path_match\":\"*\"")))) {
      final Supplier<PutMappingRequest.Builder> builderSupplier =
          () -> new PutMappingRequest.Builder().index(indexName);
      final ObjectDeserializer<PutMappingRequest.Builder> deserializer =
          getDeserializerPutIndexMapping(builderSupplier);
      try {
        return deserializer.deserialize(jsonParser, new JsonbJsonpMapper()).build();
      } catch (final Exception e) {
        throw new OptimizeRuntimeException("Could not load schema for " + indexName, e);
      }
    }
  }

  private static <T, B> ObjectDeserializer<B> getDeserializer(
      final Class<T> clazz, final String methodName, final Supplier<B> builderSupplier)
      throws OptimizeRuntimeException {

    final Method method;
    try {
      method = clazz.getDeclaredMethod(methodName, ObjectDeserializer.class);
    } catch (final NoSuchMethodException e) {
      throw new OptimizeRuntimeException(
          "Method " + methodName + " could not be found when deserializing " + clazz.getName());
    }
    method.setAccessible(true);

    final ObjectDeserializer<B> deserializer = new ObjectDeserializer<>(builderSupplier);
    try {
      method.invoke(null, deserializer);
    } catch (final IllegalAccessException | InvocationTargetException e) {
      throw new OptimizeRuntimeException(
          "Method " + methodName + " could not be invoked when deserializing " + clazz.getName());
    }
    return deserializer;
  }

  private static ObjectDeserializer<PutMappingRequest.Builder> getDeserializerPutIndexMapping(
      final Supplier<PutMappingRequest.Builder> builderSupplier) {
    return getDeserializer(
        PutMappingRequest.class, "setupPutMappingRequestDeserializer", builderSupplier);
  }

  private static ObjectDeserializer<CreateIndexRequest.Builder>
      getDeserializerWithPreconfiguredBuilder(
          final Supplier<CreateIndexRequest.Builder> builderSupplier) {
    return getDeserializer(
        CreateIndexRequest.class, "setupCreateIndexRequestDeserializer", builderSupplier);
  }

  private static ObjectDeserializer<IndexTemplateMapping.Builder>
      getDeserializerIndexTemplateMapping(
          final Supplier<IndexTemplateMapping.Builder> builderSupplier) {
    return getDeserializer(
        IndexTemplateMapping.class, "setupIndexTemplateMappingDeserializer", builderSupplier);
  }

  private Map<String, Alias> createAliasMap(
      final Set<String> aliases, final String defaultAliasName) {
    final Map<String, Alias> additionalAliases =
        aliases.stream()
            .filter(aliasName -> !aliasName.equals(defaultAliasName))
            .collect(
                Collectors.toMap(
                    aliasName -> aliasName,
                    aliasName -> new Alias.Builder().isWriteIndex(false).build()));
    additionalAliases.put(defaultAliasName, new Alias.Builder().isWriteIndex(true).build());
    return additionalAliases;
  }

  private void createOrUpdateTemplateWithAliases(
      final OptimizeOpenSearchClient osClient,
      final IndexMappingCreator<Builder> mappingCreator,
      final String defaultAliasName,
      final Set<String> additionalAliases,
      final IndexSettings indexSettings) {
    final String templateName =
        indexNameService.getOptimizeIndexNameWithVersionWithoutSuffix(mappingCreator);
    LOG.info("Creating or updating template with name {}", templateName);

    try {
      final PutTemplateRequest request =
          PutTemplateRequest.of(
              b -> {
                b.name(templateName)
                    .version((long) mappingCreator.getVersion())
                    .mappings(getMappings(mappingCreator.getSource().toString()))
                    .settings(convertToMap(indexSettings))
                    .indexPatterns(
                        Collections.singletonList(
                            indexNameService.getOptimizeIndexNameWithVersionWithWildcardSuffix(
                                mappingCreator)));
                additionalAliases.stream()
                    .filter(aliasName -> !aliasName.equals(defaultAliasName))
                    .map(aliasName -> Pair.of(aliasName, Alias.of(a -> a.isWriteIndex(false))))
                    .forEach((p) -> b.aliases(p.key(), p.value()));
                return b;
              });
      putIndexTemplate(osClient, request);
    } catch (final OptimizeRuntimeException | IOException e) {
      throw new OptimizeRuntimeException(
          "Could not create or update template " + templateName + ". Error: " + e.getMessage());
    }
  }

  private Map<String, JsonData> convertToMap(final IndexSettings indexSettings) {
    try {
      final JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper(OPTIMIZE_MAPPER);
      final StringWriter writer = new StringWriter();
      final JacksonJsonpGenerator generator =
          new JacksonJsonpGenerator(new JsonFactory().createGenerator(writer));
      indexSettings.serialize(generator, jsonpMapper);
      generator.flush();

      try (final JsonParser jsonParser =
          JsonProvider.provider().createParser(new StringReader(writer.toString()))) {

        return ((Map<String, Object>) JsonData.from(jsonParser, jsonpMapper).to(Map.class))
            .entrySet().stream()
                .collect(
                    Collectors.toMap(Map.Entry::getKey, entry -> JsonData.of(entry.getValue())));
      }
    } catch (final IOException e) {
      LOG.error("Could not create settings!", e);
      throw new OptimizeRuntimeException("Could not create index settings");
    }
  }

  private TypeMapping getMappings(final String json) {
    final ObjectMapper objectMapper = new ObjectMapper();
    JsonNode indexAsJSONNode = null;
    final String jsonNew =
        "{\"mappings\": "
            + json.replace("TypeMapping: ", "")
                .replace("\"match_mapping_type\":[\"string\"]", "\"match_mapping_type\":\"string\"")
                .replace("\"path_match\":[\"*\"]", "\"path_match\":\"*\"")
            + "}";
    try {
      indexAsJSONNode = objectMapper.readTree(new StringReader(jsonNew));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }

    final JsonParser jsonParser =
        JsonProvider.provider()
            .createParser(new StringReader(indexAsJSONNode.get("mappings").toPrettyString()));
    return TypeMapping._DESERIALIZER.deserialize(jsonParser, new JsonbJsonpMapper());
  }

  private void putIndexTemplate(
      final OptimizeOpenSearchClient osClient, final PutTemplateRequest request)
      throws IOException {
    final boolean created =
        osClient.getOpenSearchClient().indices().putTemplate(request).acknowledged();
    if (created) {
      LOG.info("Template [{}] was successfully created", request.name());
    } else {
      LOG.info("Template [{}] was not created", request.name());
    }
  }

  private void updateAllMappingsAndDynamicSettings(final OptimizeOpenSearchClient osClient) {
    LOG.info("Updating Optimize schema...");
    for (final IndexMappingCreator<IndexSettings.Builder> mapping : mappings) {
      updateDynamicSettingsAndMappings(osClient, mapping);
    }
    final List<IndexMappingCreator<Builder>> allDynamicMappings =
        new MappingMetadataUtilOS(osClient)
            .getAllDynamicMappings(indexNameService.getIndexPrefix());
    for (final IndexMappingCreator<Builder> mapping : allDynamicMappings) {
      updateDynamicSettingsAndMappings(osClient, mapping);
    }
    LOG.info("Finished updating Optimize schema.");
  }

  private void unblockIndices(final OptimizeOpenSearchClient osClient) {
    final List<String> indexesBlocked;
    try {
      final GetIndicesSettingsResponse settingsResponse =
          osClient
              .getOpenSearchClient()
              .indices()
              .getSettings(new GetIndicesSettingsRequest.Builder().index(ALL_INDEXES).build());
      indexesBlocked =
          settingsResponse.result().values().stream()
              .filter(
                  entry ->
                      entry.settings() != null
                          && entry.settings().blocksReadOnlyAllowDelete() != null
                          && entry.settings().blocksReadOnlyAllowDelete()
                          && entry.settings().index() != null)
              .map(entry -> entry.settings().index().providedName())
              .filter(Objects::nonNull)
              .filter(indexName -> indexName.startsWith(indexNameService.getIndexPrefix()))
              .toList();
    } catch (final IOException e) {
      final String errorMsg = "Could not retrieve index settings!";
      LOG.error(errorMsg, e);
      throw new OptimizeRuntimeException(errorMsg, e);
    }
    for (final String indexBlocked : indexesBlocked) {
      final PutIndicesSettingsRequest indexUpdateRequest =
          new PutIndicesSettingsRequest.Builder()
              .index(indexBlocked)
              .settings(
                  new IndexSettings.Builder().blocksReadOnlyAllowDelete(Boolean.FALSE).build())
              .build();
      final PutIndicesSettingsResponse response;
      final String stdErrorMessage = "Could not unblock index " + indexBlocked;
      try {
        response = osClient.getOpenSearchClient().indices().putSettings(indexUpdateRequest);
        if (!response.acknowledged()) {
          LOG.warn(stdErrorMessage);
        } else {
          LOG.debug("Successfully unblocked index " + indexBlocked);
        }
      } catch (final IOException e) {
        throw new OptimizeRuntimeException(stdErrorMessage + ": " + e.getMessage());
      }
    }
    LOG.debug("No indexes blocked");
  }

  private void updateTemplateDynamicSettingsAndMappings(
      final OptimizeOpenSearchClient osClient,
      final IndexMappingCreator<IndexSettings.Builder> mappingCreator) {
    final String defaultAliasName =
        indexNameService.getOptimizeIndexAliasForIndex(mappingCreator.getIndexName());
    final IndexSettings indexSettings = createIndexSettings(mappingCreator);
    createOrUpdateTemplateWithAliases(
        osClient, mappingCreator, defaultAliasName, new HashSet<>(), indexSettings);
  }

  private void updateIndexDynamicSettingsAndMappings(
      final OptimizeOpenSearchClient osClient,
      final IndexMappingCreator<IndexSettings.Builder> indexMapping) {
    final String indexName =
        indexNameService.getOptimizeIndexNameWithVersionForAllIndicesOf(indexMapping);
    final String errorMsgTemplate = "Could not update [%s] for index [%s]. ";
    try {
      final IndexSettings indexSettings = buildDynamicSettings(configurationService);
      final PutIndicesSettingsRequest indexUpdateRequest =
          new PutIndicesSettingsRequest.Builder().index(indexName).settings(indexSettings).build();
      final PutIndicesSettingsResponse response =
          osClient.getOpenSearchClient().indices().putSettings(indexUpdateRequest);
      if (!response.acknowledged()) {
        throw new OptimizeRuntimeException(
            String.format(errorMsgTemplate, "settings", indexName) + "Response not acknowledged");
      }
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          String.format(errorMsgTemplate, "settings", indexName) + e.getMessage());
    }

    try {
      final PutMappingRequest mappingUpdateRequest =
          updateMappingFromJson(indexMapping.getSource().toString(), indexName);
      final PutMappingResponse response =
          osClient.getOpenSearchClient().indices().putMapping(mappingUpdateRequest);
      if (!response.acknowledged()) {
        throw new OptimizeRuntimeException(
            String.format(errorMsgTemplate, "mappings", indexName) + "Response not acknowledged");
      }
    } catch (final OptimizeRuntimeException | IOException e) {
      throw new OptimizeRuntimeException(
          String.format(errorMsgTemplate, "mappings", indexName) + e.getMessage());
    }
    LOG.debug("Successfully updated settings and mapping for index " + indexName);
  }

  private IndexSettings createIndexSettings(
      final IndexMappingCreator<Builder> indexMappingCreator) {
    try {
      return OpenSearchIndexSettingsBuilder.buildAllSettings(
          configurationService, indexMappingCreator);
    } catch (final IOException e) {
      LOG.error("Could not create settings!", e);
      throw new OptimizeRuntimeException("Could not create index settings");
    }
  }

  public static List<IndexMappingCreator<IndexSettings.Builder>> getAllNonDynamicMappings() {
    return Arrays.asList(
        new AlertIndexOS(),
        new BusinessKeyIndexOS(),
        new CollectionIndexOS(),
        new DashboardIndexOS(),
        new DashboardShareIndexOS(),
        new DecisionDefinitionIndexOS(),
        new MetadataIndexOS(),
        new ProcessDefinitionIndexOS(),
        new ReportShareIndexOS(),
        new SettingsIndexOS(),
        new TerminatedUserSessionIndexOS(),
        new VariableUpdateInstanceIndexOS(),
        new TenantIndexOS(),
        new TimestampBasedImportIndexOS(),
        new PositionBasedImportIndexOS(),
        new CombinedReportIndexOS(),
        new SingleDecisionReportIndexOS(),
        new SingleProcessReportIndexOS(),
        new ExternalProcessVariableIndexOS(),
        new VariableLabelIndexOS(),
        new ProcessOverviewIndexOS(),
        new InstantPreviewDashboardMetadataIndexOS());
  }
}
