/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.schema;

import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.schema.IndexMappingCreator;
import org.camunda.optimize.service.db.schema.MappingMetadataUtil;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.os.schema.index.AlertIndexOS;
import org.camunda.optimize.service.os.schema.index.BusinessKeyIndexOS;
import org.camunda.optimize.service.os.schema.index.CollectionIndexOS;
import org.camunda.optimize.service.os.schema.index.DashboardIndexOS;
import org.camunda.optimize.service.os.schema.index.DashboardShareIndexOS;
import org.camunda.optimize.service.os.schema.index.DecisionDefinitionIndexOS;
import org.camunda.optimize.service.os.schema.index.ExternalProcessVariableIndexOS;
import org.camunda.optimize.service.os.schema.index.InstantPreviewDashboardMetadataIndexOS;
import org.camunda.optimize.service.os.schema.index.LicenseIndexOS;
import org.camunda.optimize.service.os.schema.index.MetadataIndexOS;
import org.camunda.optimize.service.os.schema.index.OnboardingStateIndexOS;
import org.camunda.optimize.service.os.schema.index.ProcessDefinitionIndexOS;
import org.camunda.optimize.service.os.schema.index.ProcessOverviewIndexOS;
import org.camunda.optimize.service.os.schema.index.ReportShareIndexOS;
import org.camunda.optimize.service.os.schema.index.SettingsIndexOS;
import org.camunda.optimize.service.os.schema.index.TenantIndexOS;
import org.camunda.optimize.service.os.schema.index.TerminatedUserSessionIndexOS;
import org.camunda.optimize.service.os.schema.index.VariableLabelIndexOS;
import org.camunda.optimize.service.os.schema.index.VariableUpdateInstanceIndexOS;
import org.camunda.optimize.service.os.schema.index.events.EventIndexOS;
import org.camunda.optimize.service.os.schema.index.events.EventProcessDefinitionIndexOS;
import org.camunda.optimize.service.os.schema.index.events.EventProcessMappingIndexOS;
import org.camunda.optimize.service.os.schema.index.events.EventProcessPublishStateIndexOS;
import org.camunda.optimize.service.os.schema.index.index.ImportIndexIndexOS;
import org.camunda.optimize.service.os.schema.index.index.PositionBasedImportIndexOS;
import org.camunda.optimize.service.os.schema.index.index.TimestampBasedImportIndexOS;
import org.camunda.optimize.service.os.schema.index.report.CombinedReportIndexOS;
import org.camunda.optimize.service.os.schema.index.report.SingleDecisionReportIndexOS;
import org.camunda.optimize.service.os.schema.index.report.SingleProcessReportIndexOS;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.util.set.Sets;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch.indices.Alias;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.GetIndicesSettingsResponse;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest;
import org.opensearch.client.opensearch.indices.PutIndicesSettingsRequest;
import org.opensearch.client.opensearch.indices.PutIndicesSettingsResponse;
import org.opensearch.client.opensearch.indices.PutMappingRequest;
import org.opensearch.client.opensearch.indices.PutMappingResponse;
import org.opensearch.client.opensearch.indices.put_index_template.IndexTemplateMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.service.os.schema.OpenSearchIndexSettingsBuilder.buildDynamicSettings;

@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class OpenSearchSchemaManager {

  private final OpenSearchMetadataService metadataService;
  private final ConfigurationService configurationService;
  private final OptimizeIndexNameService indexNameService;

  @Getter
  private final List<IndexMappingCreator<IndexSettings.Builder>> mappings;

  @Autowired
  public OpenSearchSchemaManager(final OpenSearchMetadataService metadataService,
                                 final ConfigurationService configurationService,
                                 final OptimizeIndexNameService indexNameService) {
    this.metadataService = metadataService;
    this.configurationService = configurationService;
    this.indexNameService = indexNameService;
    this.mappings = new ArrayList<>();

    mappings.addAll(getAllNonDynamicMappings());
  }

  public OpenSearchSchemaManager(final OpenSearchMetadataService metadataService,
                                 final ConfigurationService configurationService,
                                 final OptimizeIndexNameService indexNameService,
                                 final List<IndexMappingCreator<IndexSettings.Builder>> mappings) {
    this.metadataService = metadataService;
    this.configurationService = configurationService;
    this.indexNameService = indexNameService;
    this.mappings = mappings;
  }

  public void validateExistingSchemaVersion(final OptimizeOpenSearchClient osClient) {
    metadataService.validateSchemaVersionCompatibility(osClient);
  }

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

  public void addMapping(IndexMappingCreator<IndexSettings.Builder> mapping) {
    mappings.add(mapping);
  }

  public boolean schemaExists(OptimizeOpenSearchClient osClient) {
    return indicesExist(osClient, getMappings());
  }

  public boolean indexExists(final OptimizeOpenSearchClient osClient,
                             final IndexMappingCreator<IndexSettings.Builder> mapping) {
    return indicesExist(osClient, Collections.singletonList(mapping));
  }

  public boolean indexExists(final OptimizeOpenSearchClient osClient,
                             final String indexName) {
    return indicesExistWithNames(osClient, Collections.singletonList(indexName));
  }

  public boolean indicesExist(final OptimizeOpenSearchClient osClient,
                              final List<IndexMappingCreator<IndexSettings.Builder>> mappings) {
    return indicesExistWithNames(
      osClient,
      mappings.stream()
        .map(IndexMappingCreator::getIndexName)
        .toList()
    );
  }

  public void createIndexIfMissing(final OptimizeOpenSearchClient osClient,
                                   final IndexMappingCreator<IndexSettings.Builder> indexMapping) {
    createIndexIfMissing(osClient, indexMapping, Collections.emptySet());
  }

  public void createIndexIfMissing(final OptimizeOpenSearchClient osClient,
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

  public void createOptimizeIndices(OptimizeOpenSearchClient osClient) {
    for (IndexMappingCreator<IndexSettings.Builder> mapping : mappings) {
      createOrUpdateOptimizeIndex(osClient, mapping);
    }
  }
  
  public void createOrUpdateOptimizeIndex(final OptimizeOpenSearchClient osClient,
                                          final IndexMappingCreator<IndexSettings.Builder> mapping) {
    createOrUpdateOptimizeIndex(osClient, mapping, Collections.emptySet());
  }
  
  public void createOrUpdateOptimizeIndex(final OptimizeOpenSearchClient osClient,
                                          final IndexMappingCreator<IndexSettings.Builder> mapping,
                                          final Set<String> readOnlyAliases) {
    final Set<String> prefixedReadOnlyAliases =
      readOnlyAliases.stream()
        .map(indexNameService::getOptimizeIndexAliasForIndex)
        .collect(toSet());
    final String defaultAliasName = indexNameService.getOptimizeIndexAliasForIndex(mapping.getIndexName());
    final String suffixedIndexName = indexNameService.getOptimizeIndexNameWithVersion(mapping);
    
    if (mapping.isCreateFromTemplate()) {
      // Creating template without alias and adding aliases manually to indices created from this template to
      // ensure correct alias handling on rollover
      createOrUpdateTemplateWithAliases(
        osClient, mapping, defaultAliasName, prefixedReadOnlyAliases);
      createOptimizeIndexWithWriteAliasFromTemplate(osClient, suffixedIndexName, defaultAliasName);
    } else {
      createIndex(osClient, suffixedIndexName, defaultAliasName, prefixedReadOnlyAliases, mapping);
    }
  }

  public void updateDynamicSettingsAndMappings(OptimizeOpenSearchClient osClient,
                                               IndexMappingCreator<?> indexMapping) {
    updateIndexDynamicSettingsAndMappings(osClient, indexMapping);
    if (indexMapping.isCreateFromTemplate()) {
      updateTemplateDynamicSettingsAndMappings(osClient, indexMapping);
    }
  }

  private boolean indicesExistWithNames(final OptimizeOpenSearchClient osClient,
                                        final List<String> indexNames) {
    return indexNames.stream()
      .allMatch(index -> osClient.getRichOpenSearchClient().index().indexExists(index));
  }

  private void createOptimizeIndexWithWriteAliasFromTemplate(final OptimizeOpenSearchClient osClient,
                                                             final String indexNameWithSuffix,
                                                             final String aliasName) {
    log.info("Creating index {} from template with write alias {}", indexNameWithSuffix, aliasName);
    final CreateIndexRequest.Builder createIndexRequest = new CreateIndexRequest.Builder().index(indexNameWithSuffix);
    if (aliasName != null) {
      createIndexRequest.aliases(aliasName, new Alias.Builder().isWriteIndex(true).build());
    }
    createIndex(osClient, createIndexRequest.build(), indexNameWithSuffix);
  }

  private void createIndex(final OptimizeOpenSearchClient osClient,
                           final CreateIndexRequest createIndexRequest,
                           final String indexName) {
    final boolean created = osClient.getRichOpenSearchClient().index().createIndexWithRetries(createIndexRequest);
    if (created) {
      log.info("Index [{}] was successfully created", indexName);
    } else {
      log.info("Index [{}] was not created", indexName);
    }
  }

  private void createIndex(final OptimizeOpenSearchClient osClient,
                           final String suffixedIndexName,
                           final String defaultAliasName,
                           final Set<String> prefixedReadOnlyAliases,
                           final IndexMappingCreator<IndexSettings.Builder> mapping)  {
    log.debug("Creating Optimize Index with name {}, default alias {} and additional aliases {}",
              suffixedIndexName, defaultAliasName, prefixedReadOnlyAliases
    );
    try {
      CreateIndexRequest request = createIndexFromJson(
        Strings.toString(mapping.getSource()),
        suffixedIndexName,
        createAliasMap(prefixedReadOnlyAliases, defaultAliasName),
        OpenSearchIndexSettingsBuilder.buildAllSettings(configurationService, mapping));

      createIndex(osClient, request, suffixedIndexName);
    } catch (Exception e){
      throw new OptimizeRuntimeException("Could not create index " + suffixedIndexName, e);
    }
  }

  // Needed for loading from JSON, opensearch doesn't provide something like .withJSON(...) for index creation from files
  private CreateIndexRequest createIndexFromJson(String jsonMappings,
                                                 String indexName,
                                                 Map<String, Alias> aliases,
                                                 IndexSettings settings) throws OptimizeRuntimeException
  {
    String jsonNew = "{\"mappings\": " + jsonMappings + "}";
    try (JsonParser jsonParser = JsonProvider.provider().createParser(new StringReader(jsonNew))) {
      Supplier<CreateIndexRequest.Builder> builderSupplier = () -> new CreateIndexRequest.Builder()
        .index(indexName)
        .aliases(aliases)
        .settings(settings);
      ObjectDeserializer<CreateIndexRequest.Builder> deserializer = getDeserializerWithPreconfiguredBuilder(
        builderSupplier);
      try {
        return deserializer.deserialize(jsonParser, new JsonbJsonpMapper()).build();
      } catch (Exception e) {
        throw new OptimizeRuntimeException("Could not load schema for " + indexName, e);
      }
    }
  }

  // Needed for loading from JSON, opensearch doesn't provide something like .withJSON(...) for index creation from files
  private PutMappingRequest updateMappingFromJson(String jsonMappings,
                                                  String indexName) throws OptimizeRuntimeException {
    try (JsonParser jsonParser = JsonProvider.provider().createParser(new StringReader(jsonMappings))) {
      Supplier<PutMappingRequest.Builder> builderSupplier = () -> new PutMappingRequest.Builder()
        .index(indexName);
      ObjectDeserializer<PutMappingRequest.Builder> deserializer = getDeserializerPutIndexMapping(
        builderSupplier);
      try {
        return deserializer.deserialize(jsonParser, new JsonbJsonpMapper()).build();
      } catch (Exception e) {
        throw new OptimizeRuntimeException("Could not load schema for " + indexName, e);
      }
    }
  }

  // TODO make this and the three methods below a parametrized method, since their build-up is very similar with
  //  OPT-7352
  private static ObjectDeserializer<PutMappingRequest.Builder> getDeserializerPutIndexMapping(
    Supplier<PutMappingRequest.Builder> builderSupplier) throws OptimizeRuntimeException {
    Class<PutMappingRequest> clazz = PutMappingRequest.class;
    Method method;
    String methodName = "setupPutMappingRequestDeserializer";
    try {
      method = clazz.getDeclaredMethod(methodName, ObjectDeserializer.class);
    } catch (NoSuchMethodException e) {
      throw new OptimizeRuntimeException("Method " + methodName + " could not be found when deserializing " + clazz.getName());
    }
    method.setAccessible(true);

    ObjectDeserializer<PutMappingRequest.Builder> deserializer = new ObjectDeserializer<>(builderSupplier);
    try {
      method.invoke(null, deserializer);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new OptimizeRuntimeException("Method " + methodName + " could not be invoked when deserializing " + clazz.getName());
    }
    return deserializer;
  }

  private static ObjectDeserializer<CreateIndexRequest.Builder> getDeserializerWithPreconfiguredBuilder(
    Supplier<CreateIndexRequest.Builder> builderSupplier) throws OptimizeRuntimeException {
    Class<CreateIndexRequest> clazz = CreateIndexRequest.class;
    String methodName = "setupCreateIndexRequestDeserializer";
    Method method;
    try {
      method = clazz.getDeclaredMethod(methodName, ObjectDeserializer.class);
    } catch (NoSuchMethodException e) {
      throw new OptimizeRuntimeException("Method " + methodName + " could not be found when deserializing " + clazz.getName());
    }
    method.setAccessible(true);

    ObjectDeserializer<CreateIndexRequest.Builder> deserializer = new ObjectDeserializer<>(builderSupplier);
    try {
      method.invoke(null, deserializer);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new OptimizeRuntimeException("Method " + methodName + " could not be invoked when deserializing " + clazz.getName());
    }
    return deserializer;
  }

  private static ObjectDeserializer<IndexTemplateMapping.Builder> getDeserializerIndexTemplateMapping (
    Supplier<IndexTemplateMapping.Builder> builderSupplier) throws OptimizeRuntimeException {
    Class<IndexTemplateMapping> clazz = IndexTemplateMapping.class;
    String methodName = "setupIndexTemplateMappingDeserializer";
    Method method;
    try {
      method = clazz.getDeclaredMethod(methodName, ObjectDeserializer.class);
    } catch (NoSuchMethodException e) {
      throw new OptimizeRuntimeException("Method " + methodName + " could not be found when deserializing " + clazz.getName());
    }
    method.setAccessible(true);

    ObjectDeserializer<IndexTemplateMapping.Builder> deserializer = new ObjectDeserializer<>(builderSupplier);
    try {
      method.invoke(null, deserializer);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new OptimizeRuntimeException("Method " + methodName + " could not be invoked when deserializing " + clazz.getName());
    }
    return deserializer;
  }

  private Map<String, Alias> createAliasMap(final Set<String> aliases, final String defaultAliasName) {
    Map<String, Alias> additionalAliases = aliases.stream()
      .filter(aliasName -> !aliasName.equals(defaultAliasName))
      .collect(Collectors.toMap(
        aliasName -> aliasName,
        aliasName -> new Alias.Builder().isWriteIndex(false).build()));
    additionalAliases.put(defaultAliasName, new Alias.Builder().isWriteIndex(true).build());
    return additionalAliases;
  }

  private void createOrUpdateTemplateWithAliases(final OptimizeOpenSearchClient osClient,
                                                 final IndexMappingCreator<?> mappingCreator,
                                                 final String defaultAliasName,
                                                 final Set<String> additionalAliases) {
    final String templateName = indexNameService.getOptimizeIndexNameWithVersionWithoutSuffix(mappingCreator);
    log.info("Creating or updating template with name {}", templateName);

    Map<String, Alias> aliases = createAliasMap(additionalAliases, defaultAliasName);

    final IndexTemplateMapping template;
    try {
      template = createTemplateFromJson(
        Strings.toString(mappingCreator.getSource()), aliases,
        OpenSearchIndexSettingsBuilder.buildAllSettings(configurationService,
                                                        (IndexMappingCreator<IndexSettings.Builder>) mappingCreator));
      final PutIndexTemplateRequest request = new PutIndexTemplateRequest.Builder()
        .name(templateName)
        .indexPatterns(Collections.singletonList(indexNameService.getOptimizeIndexNameWithVersionForAllIndicesOf(
          mappingCreator)))
        .template(template)
        .version((long) mappingCreator.getVersion())
        .build();
      putIndexTemplate(osClient, request);
    } catch (OptimizeRuntimeException | IOException e) {
      throw new OptimizeRuntimeException("Could not create or update template " + templateName +". Error: " + e.getMessage());
    }

  }

  // Needed for loading from JSON, opensearch doesn't provide something like .withJSON(...) for index creation from files
  private IndexTemplateMapping createTemplateFromJson(String jsonMappings,
                                                      Map<String, Alias> aliases,
                                                      IndexSettings settings) throws OptimizeRuntimeException {
    String jsonNew = "{\"mappings\": " + jsonMappings + "}";
    try (JsonParser jsonParser = JsonProvider.provider().createParser(new StringReader(jsonNew))) {
      Supplier<IndexTemplateMapping.Builder> builderSupplier = () -> new IndexTemplateMapping.Builder()
        .aliases(aliases)
        .settings(settings);
      ObjectDeserializer<IndexTemplateMapping.Builder> deserializer =
        getDeserializerIndexTemplateMapping(builderSupplier);
      try {
        return deserializer.deserialize(jsonParser, new JsonbJsonpMapper()).build();
      } catch (Exception e) {
        throw new OptimizeRuntimeException("Could not create template", e);
      }
    }
  }

  private void putIndexTemplate(final OptimizeOpenSearchClient osClient, final PutIndexTemplateRequest request) {
    final boolean created = osClient.getRichOpenSearchClient().template().createTemplateWithRetries(request);
    if (created) {
      log.info("Template [{}] was successfully created", request.name());
    } else {
      log.info("Template [{}] was not created", request.name());
    }
  }

  private void updateAllMappingsAndDynamicSettings(OptimizeOpenSearchClient osClient) {
    log.info("Updating Optimize schema...");
    for (IndexMappingCreator<IndexSettings.Builder> mapping : mappings) {
      updateDynamicSettingsAndMappings(osClient, mapping);
    }
    final List<IndexMappingCreator<?>> allDynamicMappings =
      new MappingMetadataUtil(osClient).getAllDynamicMappings();
    for (IndexMappingCreator<?> mapping : allDynamicMappings) {
      updateDynamicSettingsAndMappings(osClient, mapping);
    }
    log.info("Finished updating Optimize schema.");
  }

  private void unblockIndices(final OptimizeOpenSearchClient osClient) {
    final boolean indexBlocked;
    try {
      final GetIndicesSettingsResponse settingsResponse = osClient.getOpenSearchClient().indices().getSettings();
      indexBlocked = settingsResponse.result().values().stream()
        .anyMatch(entry -> entry.settings() != null &&
                           entry.settings().blocksReadOnlyAllowDelete() != null &&
                           entry.settings().blocksReadOnlyAllowDelete()
        );
    } catch (IOException e) {
      String errorMsg = "Could not retrieve index settings!";
      log.error(errorMsg, e);
      throw new OptimizeRuntimeException(errorMsg, e);
    }
    if (indexBlocked) {
      final PutIndicesSettingsRequest indexUpdateRequest = new PutIndicesSettingsRequest.Builder()
        .index("*")
        .settings(new IndexSettings.Builder().blocksReadOnlyAllowDelete(Boolean.FALSE).build())
        .build();
      final PutIndicesSettingsResponse response;
      String stdErrorMessage = "Could not unblock Opensearch indices!";
      try {
        response = osClient.getOpenSearchClient()
          .indices()
          .putSettings(indexUpdateRequest);
        if (!response.acknowledged()) {
          throw new OptimizeRuntimeException(stdErrorMessage + ". Response not acknowledged");
        } else {
          log.debug("Successfully unblocked indexes");
        }
      } catch (IOException e) {
        throw new OptimizeRuntimeException(stdErrorMessage + ": " + e.getMessage());
      }
    } else {
      log.debug("No indexes blocked");
    }
  }

  private void updateTemplateDynamicSettingsAndMappings(OptimizeOpenSearchClient osClient,
                                                        IndexMappingCreator<?> mappingCreator) {
    final String defaultAliasName = indexNameService.getOptimizeIndexAliasForIndex(mappingCreator.getIndexName());
    createIndexSettings(mappingCreator);
    createOrUpdateTemplateWithAliases(
      osClient, mappingCreator, defaultAliasName, Sets.newHashSet());
  }

  private void updateIndexDynamicSettingsAndMappings(OptimizeOpenSearchClient osClient,
                                                     IndexMappingCreator indexMapping) {
    final String indexName = indexNameService.getOptimizeIndexNameWithVersionForAllIndicesOf(indexMapping);
    final String errorMsgTemplate = "Could not update [%s] for index [%s]. ";
    try {
      final IndexSettings indexSettings = buildDynamicSettings(configurationService);
      final PutIndicesSettingsRequest indexUpdateRequest = new PutIndicesSettingsRequest.Builder()
        .index(indexName)
        .settings(indexSettings)
        .build();
      final PutIndicesSettingsResponse response = osClient.getOpenSearchClient()
        .indices()
        .putSettings(indexUpdateRequest);
      if (!response.acknowledged()) {
        throw new OptimizeRuntimeException(String.format(errorMsgTemplate, "settings", indexName) + "Response not acknowledged");
      }
    } catch (IOException e) {
      throw new OptimizeRuntimeException(String.format(errorMsgTemplate, "settings", indexName) + e.getMessage());
    }

    try {
      PutMappingRequest mappingUpdateRequest = updateMappingFromJson(Strings.toString(indexMapping.getSource()), indexName);
      final PutMappingResponse response = osClient.getOpenSearchClient()
        .indices()
        .putMapping(mappingUpdateRequest);
      if (!response.acknowledged()) {
        throw new
          OptimizeRuntimeException(String.format(errorMsgTemplate, "mappings", indexName) + "Response not acknowledged");
      }
    } catch (OptimizeRuntimeException | IOException e) {
      throw new OptimizeRuntimeException(String.format(errorMsgTemplate, "mappings", indexName) + e.getMessage());
    }
    log.debug("Successfully updated settings and mapping for index " + indexName);
  }

  private void createIndexSettings(IndexMappingCreator<?> indexMappingCreator) {
    try {
      OpenSearchIndexSettingsBuilder
        .buildAllSettings(configurationService, (IndexMappingCreator<IndexSettings.Builder>) indexMappingCreator);
    } catch (IOException e) {
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
      new OnboardingStateIndexOS(),
      new ProcessDefinitionIndexOS(),
      new ReportShareIndexOS(),
      new SettingsIndexOS(),
      new TerminatedUserSessionIndexOS(),
      new VariableUpdateInstanceIndexOS(),
      new TenantIndexOS(),
      new EventIndexOS(),
      new EventProcessDefinitionIndexOS(),
      new EventProcessMappingIndexOS(),
      new EventProcessPublishStateIndexOS(),
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
