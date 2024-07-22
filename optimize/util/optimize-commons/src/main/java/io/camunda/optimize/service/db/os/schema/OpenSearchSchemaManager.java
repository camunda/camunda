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
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.Iterables;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.schema.index.AlertIndexOS;
import io.camunda.optimize.service.db.os.schema.index.BusinessKeyIndexOS;
import io.camunda.optimize.service.db.os.schema.index.CollectionIndexOS;
import io.camunda.optimize.service.db.os.schema.index.DashboardIndexOS;
import io.camunda.optimize.service.db.os.schema.index.DashboardShareIndexOS;
import io.camunda.optimize.service.db.os.schema.index.DecisionDefinitionIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ExternalProcessVariableIndexOS;
import io.camunda.optimize.service.db.os.schema.index.InstantPreviewDashboardMetadataIndexOS;
import io.camunda.optimize.service.db.os.schema.index.LicenseIndexOS;
import io.camunda.optimize.service.db.os.schema.index.MetadataIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ProcessDefinitionIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ProcessOverviewIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ReportShareIndexOS;
import io.camunda.optimize.service.db.os.schema.index.SettingsIndexOS;
import io.camunda.optimize.service.db.os.schema.index.TenantIndexOS;
import io.camunda.optimize.service.db.os.schema.index.TerminatedUserSessionIndexOS;
import io.camunda.optimize.service.db.os.schema.index.VariableLabelIndexOS;
import io.camunda.optimize.service.db.os.schema.index.VariableUpdateInstanceIndexOS;
import io.camunda.optimize.service.db.os.schema.index.index.ImportIndexIndexOS;
import io.camunda.optimize.service.db.os.schema.index.index.PositionBasedImportIndexOS;
import io.camunda.optimize.service.db.os.schema.index.index.TimestampBasedImportIndexOS;
import io.camunda.optimize.service.db.os.schema.index.report.CombinedReportIndexOS;
import io.camunda.optimize.service.db.os.schema.index.report.SingleDecisionReportIndexOS;
import io.camunda.optimize.service.db.os.schema.index.report.SingleProcessReportIndexOS;
import io.camunda.optimize.service.db.schema.DatabaseSchemaManager;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.MappingMetadataUtil;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.util.set.Sets;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.indices.Alias;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.GetIndicesSettingsRequest;
import org.opensearch.client.opensearch.indices.GetIndicesSettingsResponse;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.IndexSettings.Builder;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest;
import org.opensearch.client.opensearch.indices.PutIndicesSettingsRequest;
import org.opensearch.client.opensearch.indices.PutIndicesSettingsResponse;
import org.opensearch.client.opensearch.indices.PutMappingRequest;
import org.opensearch.client.opensearch.indices.PutMappingResponse;
import org.opensearch.client.opensearch.indices.put_index_template.IndexTemplateMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class OpenSearchSchemaManager
    extends DatabaseSchemaManager<OptimizeOpenSearchClient, IndexSettings.Builder> {

  public static final String ALL_INDEXES = "_all";
  private final OpenSearchMetadataService metadataService;

  @Autowired
  public OpenSearchSchemaManager(
      final OpenSearchMetadataService metadataService,
      final ConfigurationService configurationService,
      final OptimizeIndexNameService indexNameService) {
    super(configurationService, indexNameService, new ArrayList<>(getAllNonDynamicMappings()));
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
      log.info("Initializing Optimize schema...");
      createOptimizeIndices(osClient);
      log.info("Optimize schema initialized successfully.");
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
      log.error("Failed ensuring index is present: {}", indexMapping.getIndexName(), e);
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
        log.debug(
            "index {} already exists, updating mapping and dynamic settings.", suffixedIndexName);
        updateDynamicSettingsAndMappings(osClient, mapping);
      } else {
        throw e;
      }
    } catch (final Exception e) {
      final String message = String.format("Could not create Index [%s]", suffixedIndexName);
      log.warn(message, e);
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
      final OptimizeOpenSearchClient dbClient,
      final IndexMappingCreator<IndexSettings.Builder> mappingCreator) {
    // TODO?
    throw new NotImplementedException("Not implemented in OpenSearch");
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
    log.info("Creating index {} from template with write alias {}", indexNameWithSuffix, aliasName);
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
      log.warn(message, e);
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
      log.info("Index [{}] was successfully created", indexName);
    } else {
      log.info("Index [{}] was not created", indexName);
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
    log.debug(
        "Creating Optimize Index with name {}, default alias {} and additional aliases {}",
        suffixedIndexName,
        defaultAliasName,
        prefixedReadOnlyAliases);

    final CreateIndexRequest request =
        createIndexFromJson(
            Strings.toString(mapping.getSource()),
            suffixedIndexName,
            createAliasMap(prefixedReadOnlyAliases, defaultAliasName),
            indexSettings);
    createIndex(osClient, request, suffixedIndexName);
  }

  // Needed for loading from JSON, opensearch doesn't provide something like .withJSON(...) for
  // index creation from files
  private CreateIndexRequest createIndexFromJson(
      final String jsonMappings,
      final String indexName,
      final Map<String, Alias> aliases,
      final IndexSettings settings)
      throws OptimizeRuntimeException {
    final String jsonNew = "{\"mappings\": " + jsonMappings + "}";
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

  // Needed for loading from JSON, opensearch doesn't provide something like .withJSON(...) for
  // index creation from files
  private PutMappingRequest updateMappingFromJson(final String jsonMappings, final String indexName)
      throws OptimizeRuntimeException {
    try (final JsonParser jsonParser =
        JsonProvider.provider().createParser(new StringReader(jsonMappings))) {
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

  // TODO make this and the three methods below a parametrized method, since their build-up is very
  // similar with
  //  OPT-7352
  private static ObjectDeserializer<PutMappingRequest.Builder> getDeserializerPutIndexMapping(
      final Supplier<PutMappingRequest.Builder> builderSupplier) throws OptimizeRuntimeException {
    final Class<PutMappingRequest> clazz = PutMappingRequest.class;
    final Method method;
    final String methodName = "setupPutMappingRequestDeserializer";
    try {
      method = clazz.getDeclaredMethod(methodName, ObjectDeserializer.class);
    } catch (final NoSuchMethodException e) {
      throw new OptimizeRuntimeException(
          "Method " + methodName + " could not be found when deserializing " + clazz.getName());
    }
    method.setAccessible(true);

    final ObjectDeserializer<PutMappingRequest.Builder> deserializer =
        new ObjectDeserializer<>(builderSupplier);
    try {
      method.invoke(null, deserializer);
    } catch (final IllegalAccessException | InvocationTargetException e) {
      throw new OptimizeRuntimeException(
          "Method " + methodName + " could not be invoked when deserializing " + clazz.getName());
    }
    return deserializer;
  }

  private static ObjectDeserializer<CreateIndexRequest.Builder>
      getDeserializerWithPreconfiguredBuilder(
          final Supplier<CreateIndexRequest.Builder> builderSupplier)
          throws OptimizeRuntimeException {
    final Class<CreateIndexRequest> clazz = CreateIndexRequest.class;
    final String methodName = "setupCreateIndexRequestDeserializer";
    final Method method;
    try {
      method = clazz.getDeclaredMethod(methodName, ObjectDeserializer.class);
    } catch (final NoSuchMethodException e) {
      throw new OptimizeRuntimeException(
          "Method " + methodName + " could not be found when deserializing " + clazz.getName());
    }
    method.setAccessible(true);

    final ObjectDeserializer<CreateIndexRequest.Builder> deserializer =
        new ObjectDeserializer<>(builderSupplier);
    try {
      method.invoke(null, deserializer);
    } catch (final IllegalAccessException | InvocationTargetException e) {
      throw new OptimizeRuntimeException(
          "Method " + methodName + " could not be invoked when deserializing " + clazz.getName());
    }
    return deserializer;
  }

  private static ObjectDeserializer<IndexTemplateMapping.Builder>
      getDeserializerIndexTemplateMapping(
          final Supplier<IndexTemplateMapping.Builder> builderSupplier)
          throws OptimizeRuntimeException {
    final Class<IndexTemplateMapping> clazz = IndexTemplateMapping.class;
    final String methodName = "setupIndexTemplateMappingDeserializer";
    final Method method;
    try {
      method = clazz.getDeclaredMethod(methodName, ObjectDeserializer.class);
    } catch (final NoSuchMethodException e) {
      throw new OptimizeRuntimeException(
          "Method " + methodName + " could not be found when deserializing " + clazz.getName());
    }
    method.setAccessible(true);

    final ObjectDeserializer<IndexTemplateMapping.Builder> deserializer =
        new ObjectDeserializer<>(builderSupplier);
    try {
      method.invoke(null, deserializer);
    } catch (final IllegalAccessException | InvocationTargetException e) {
      throw new OptimizeRuntimeException(
          "Method " + methodName + " could not be invoked when deserializing " + clazz.getName());
    }
    return deserializer;
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
    log.info("Creating or updating template with name {}", templateName);

    final Map<String, Alias> aliases = createAliasMap(additionalAliases, defaultAliasName);

    final IndexTemplateMapping template;
    try {
      template =
          createTemplateFromJson(
              Strings.toString(mappingCreator.getSource()),
              aliases,
              OpenSearchIndexSettingsBuilder.buildAllSettings(
                  configurationService, mappingCreator));
      final PutIndexTemplateRequest request =
          new PutIndexTemplateRequest.Builder()
              .name(templateName)
              .template(new IndexTemplateMapping.Builder().settings(indexSettings).build())
              .indexPatterns(
                  Collections.singletonList(
                      indexNameService.getOptimizeIndexNameWithVersionForAllIndicesOf(
                          mappingCreator)))
              .template(template)
              .version((long) mappingCreator.getVersion())
              .build();
      putIndexTemplate(osClient, request);
    } catch (final OptimizeRuntimeException | IOException e) {
      throw new OptimizeRuntimeException(
          "Could not create or update template " + templateName + ". Error: " + e.getMessage());
    }
  }

  // Needed for loading from JSON, opensearch doesn't provide something like .withJSON(...) for
  // index creation from files
  private IndexTemplateMapping createTemplateFromJson(
      final String jsonMappings, final Map<String, Alias> aliases, final IndexSettings settings)
      throws OptimizeRuntimeException {
    final String jsonNew = "{\"mappings\": " + jsonMappings + "}";
    try (final JsonParser jsonParser =
        JsonProvider.provider().createParser(new StringReader(jsonNew))) {
      final Supplier<IndexTemplateMapping.Builder> builderSupplier =
          () -> new IndexTemplateMapping.Builder().aliases(aliases).settings(settings);
      final ObjectDeserializer<IndexTemplateMapping.Builder> deserializer =
          getDeserializerIndexTemplateMapping(builderSupplier);
      try {
        return deserializer.deserialize(jsonParser, new JsonbJsonpMapper()).build();
      } catch (final Exception e) {
        throw new OptimizeRuntimeException("Could not create template", e);
      }
    }
  }

  private void putIndexTemplate(
      final OptimizeOpenSearchClient osClient, final PutIndexTemplateRequest request) {
    final boolean created =
        osClient.getRichOpenSearchClient().template().createTemplateWithRetries(request);
    if (created) {
      log.info("Template [{}] was successfully created", request.name());
    } else {
      log.info("Template [{}] was not created", request.name());
    }
  }

  private void updateAllMappingsAndDynamicSettings(final OptimizeOpenSearchClient osClient) {
    log.info("Updating Optimize schema...");
    for (final IndexMappingCreator<IndexSettings.Builder> mapping : mappings) {
      updateDynamicSettingsAndMappings(osClient, mapping);
    }
    final List<IndexMappingCreator<?>> allDynamicMappings =
        new MappingMetadataUtil(osClient).getAllDynamicMappings(indexNameService.getIndexPrefix());
    for (final IndexMappingCreator<?> mapping : allDynamicMappings) {
      updateDynamicSettingsAndMappings(
          osClient, (IndexMappingCreator<IndexSettings.Builder>) mapping);
    }
    log.info("Finished updating Optimize schema.");
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
      log.error(errorMsg, e);
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
          log.warn(stdErrorMessage);
        } else {
          log.debug("Successfully unblocked index " + indexBlocked);
        }
      } catch (final IOException e) {
        throw new OptimizeRuntimeException(stdErrorMessage + ": " + e.getMessage());
      }
    }
    log.debug("No indexes blocked");
  }

  private void updateTemplateDynamicSettingsAndMappings(
      final OptimizeOpenSearchClient osClient,
      final IndexMappingCreator<IndexSettings.Builder> mappingCreator) {
    final String defaultAliasName =
        indexNameService.getOptimizeIndexAliasForIndex(mappingCreator.getIndexName());
    final IndexSettings indexSettings = createIndexSettings(mappingCreator);
    createOrUpdateTemplateWithAliases(
        osClient, mappingCreator, defaultAliasName, Sets.newHashSet(), indexSettings);
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
          updateMappingFromJson(Strings.toString(indexMapping.getSource()), indexName);
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
    log.debug("Successfully updated settings and mapping for index " + indexName);
  }

  private IndexSettings createIndexSettings(
      final IndexMappingCreator<Builder> indexMappingCreator) {
    try {
      return OpenSearchIndexSettingsBuilder.buildAllSettings(
          configurationService, indexMappingCreator);
    } catch (final IOException e) {
      log.error("Could not create settings!", e);
      throw new OptimizeRuntimeException("Could not create index settings");
    }
  }

  public static List<IndexMappingCreator<IndexSettings.Builder>> getAllNonDynamicMappings() {
    // TODO Add test that all indexes are created when doing OPT-7225
    return Arrays.asList(
        new AlertIndexOS(),
        new BusinessKeyIndexOS(),
        new CollectionIndexOS(),
        new DashboardIndexOS(),
        new DashboardShareIndexOS(),
        new DecisionDefinitionIndexOS(),
        new LicenseIndexOS(),
        new MetadataIndexOS(),
        new ProcessDefinitionIndexOS(),
        new ReportShareIndexOS(),
        new SettingsIndexOS(),
        new TerminatedUserSessionIndexOS(),
        new VariableUpdateInstanceIndexOS(),
        new TenantIndexOS(),
        new ImportIndexIndexOS(),
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
