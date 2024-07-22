/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.schema;

import static io.camunda.optimize.service.db.DatabaseConstants.INDEX_ALREADY_EXISTS_EXCEPTION_TYPE;
import static io.camunda.optimize.service.db.es.schema.ElasticSearchIndexSettingsBuilder.buildDynamicSettings;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.schema.index.AlertIndexES;
import io.camunda.optimize.service.db.es.schema.index.BusinessKeyIndexES;
import io.camunda.optimize.service.db.es.schema.index.CollectionIndexES;
import io.camunda.optimize.service.db.es.schema.index.DashboardIndexES;
import io.camunda.optimize.service.db.es.schema.index.DashboardShareIndexES;
import io.camunda.optimize.service.db.es.schema.index.DecisionDefinitionIndexES;
import io.camunda.optimize.service.db.es.schema.index.ExternalProcessVariableIndexES;
import io.camunda.optimize.service.db.es.schema.index.InstantPreviewDashboardMetadataIndexES;
import io.camunda.optimize.service.db.es.schema.index.LicenseIndexES;
import io.camunda.optimize.service.db.es.schema.index.MetadataIndexES;
import io.camunda.optimize.service.db.es.schema.index.ProcessDefinitionIndexES;
import io.camunda.optimize.service.db.es.schema.index.ProcessOverviewIndexES;
import io.camunda.optimize.service.db.es.schema.index.ReportShareIndexES;
import io.camunda.optimize.service.db.es.schema.index.SettingsIndexES;
import io.camunda.optimize.service.db.es.schema.index.TenantIndexES;
import io.camunda.optimize.service.db.es.schema.index.TerminatedUserSessionIndexES;
import io.camunda.optimize.service.db.es.schema.index.VariableLabelIndexES;
import io.camunda.optimize.service.db.es.schema.index.VariableUpdateInstanceIndexES;
import io.camunda.optimize.service.db.es.schema.index.index.ImportIndexIndexES;
import io.camunda.optimize.service.db.es.schema.index.index.PositionBasedImportIndexES;
import io.camunda.optimize.service.db.es.schema.index.index.TimestampBasedImportIndexES;
import io.camunda.optimize.service.db.es.schema.index.report.CombinedReportIndexES;
import io.camunda.optimize.service.db.es.schema.index.report.SingleDecisionReportIndexES;
import io.camunda.optimize.service.db.es.schema.index.report.SingleProcessReportIndexES;
import io.camunda.optimize.service.db.schema.DatabaseSchemaManager;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.MappingMetadataUtil;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.xcontent.XContentBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class ElasticSearchSchemaManager
    extends DatabaseSchemaManager<OptimizeElasticsearchClient, XContentBuilder> {
  public static final int INDEX_EXIST_BATCH_SIZE = 10;
  private static final String INDEX_READ_ONLY_SETTING = "index.blocks.read_only_allow_delete";
  protected final ElasticSearchMetadataService metadataService;

  @Autowired
  public ElasticSearchSchemaManager(
      final ElasticSearchMetadataService metadataService,
      final ConfigurationService configurationService,
      final OptimizeIndexNameService indexNameService) {
    this(
        metadataService,
        configurationService,
        indexNameService,
        new ArrayList<>(getAllNonDynamicMappings()));
  }

  public ElasticSearchSchemaManager(
      final ElasticSearchMetadataService metadataService,
      final ConfigurationService configurationService,
      final OptimizeIndexNameService indexNameService,
      final List<IndexMappingCreator<XContentBuilder>> mappings) {
    super(configurationService, indexNameService, mappings);
    this.metadataService = metadataService;
  }

  @Override
  public void validateDatabaseMetadata(final OptimizeElasticsearchClient esClient) {
    metadataService.validateMetadata(esClient);
  }

  @Override
  public void initializeSchema(final OptimizeElasticsearchClient esClient) {
    unblockIndices(esClient);
    if (!schemaExists(esClient)) {
      log.info("Initializing Optimize schema...");
      createOptimizeIndices(esClient);
      log.info("Optimize schema initialized successfully.");
    } else {
      updateAllMappingsAndDynamicSettings(esClient);
    }
    metadataService.initMetadataIfMissing(esClient);
  }

  @Override
  public boolean schemaExists(final OptimizeElasticsearchClient esClient) {
    return indicesExist(esClient, getMappings());
  }

  @Override
  public boolean indexExists(
      final OptimizeElasticsearchClient esClient,
      final IndexMappingCreator<XContentBuilder> mapping) {
    return indicesExist(esClient, Collections.singletonList(mapping));
  }

  @Override
  public boolean indexExists(final OptimizeElasticsearchClient esClient, final String indexName) {
    return indicesExistWithNames(esClient, Collections.singletonList(indexName));
  }

  @Override
  public boolean indicesExist(
      final OptimizeElasticsearchClient esClient,
      final List<IndexMappingCreator<XContentBuilder>> mappings) {
    return indicesExistWithNames(
        esClient, mappings.stream().map(IndexMappingCreator::getIndexName).toList());
  }

  @Override
  public void createIndexIfMissing(
      final OptimizeElasticsearchClient esClient,
      final IndexMappingCreator<XContentBuilder> indexMapping) {
    createIndexIfMissing(esClient, indexMapping, Collections.emptySet());
  }

  @Override
  public void createIndexIfMissing(
      final OptimizeElasticsearchClient esClient,
      final IndexMappingCreator<XContentBuilder> indexMapping,
      final Set<String> additionalReadOnlyAliases) {
    try {
      final boolean indexAlreadyExists = indexExists(esClient, indexMapping);
      if (!indexAlreadyExists) {
        createOrUpdateOptimizeIndex(esClient, indexMapping, additionalReadOnlyAliases);
      }
    } catch (final Exception e) {
      log.error("Failed ensuring index is present: {}", indexMapping.getIndexName(), e);
      throw e;
    }
  }

  @Override
  public void createOrUpdateOptimizeIndex(
      final OptimizeElasticsearchClient esClient,
      final IndexMappingCreator<XContentBuilder> mapping,
      final Set<String> readOnlyAliases) {
    final Set<String> prefixedReadOnlyAliases =
        readOnlyAliases.stream()
            .map(indexNameService::getOptimizeIndexAliasForIndex)
            .collect(toSet());
    final String defaultAliasName =
        indexNameService.getOptimizeIndexAliasForIndex(mapping.getIndexName());
    final String suffixedIndexName = indexNameService.getOptimizeIndexNameWithVersion(mapping);
    final Settings indexSettings = createIndexSettings(mapping);

    try {
      if (mapping.isCreateFromTemplate()) {
        // Creating template without alias and adding aliases manually to indices created from this
        // template to
        // ensure correct alias handling on rollover
        createOrUpdateTemplateWithAliases(
            esClient, mapping, defaultAliasName, prefixedReadOnlyAliases, indexSettings);
        createOptimizeIndexWithWriteAliasFromTemplate(
            esClient, suffixedIndexName, defaultAliasName);
      } else {
        createOptimizeIndexFromRequest(
            esClient,
            mapping,
            suffixedIndexName,
            defaultAliasName,
            prefixedReadOnlyAliases,
            indexSettings);
      }
    } catch (final ElasticsearchStatusException e) {
      if (e.status() == RestStatus.BAD_REQUEST
          && e.getMessage().contains(INDEX_ALREADY_EXISTS_EXCEPTION_TYPE)) {
        log.debug(
            "index {} already exists, updating mapping and dynamic settings.", suffixedIndexName);
        updateDynamicSettingsAndMappings(esClient, mapping);
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
      final OptimizeElasticsearchClient esClient,
      final IndexMappingCreator<XContentBuilder> mapping) {
    try {
      esClient.deleteIndex(mapping);
    } catch (final ElasticsearchStatusException e) {
      if (e.status() == RestStatus.NOT_FOUND) {
        log.debug("Index {} was not found.", mapping.getIndexName());
      } else {
        throw e;
      }
    }
  }

  @Override
  public void createOrUpdateTemplateWithoutAliases(
      final OptimizeElasticsearchClient esClient,
      final IndexMappingCreator<XContentBuilder> mappingCreator) {
    final String templateName =
        indexNameService.getOptimizeIndexTemplateNameWithVersion(mappingCreator);
    final Settings indexSettings = createIndexSettings(mappingCreator);

    log.debug("Creating or updating template with name {}.", templateName);
    final PutIndexTemplateRequest templateRequest =
        new PutIndexTemplateRequest(templateName)
            .version(mappingCreator.getVersion())
            .mapping(mappingCreator.getSource())
            .settings(indexSettings)
            .patterns(
                Collections.singletonList(
                    indexNameService.getOptimizeIndexNameWithVersionWithWildcardSuffix(
                        mappingCreator)));
    try {
      esClient.createTemplate(templateRequest);
    } catch (final Exception e) {
      final String message = String.format("Could not create or update template %s.", templateName);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @Override
  public void updateDynamicSettingsAndMappings(
      final OptimizeElasticsearchClient esClient,
      final IndexMappingCreator<XContentBuilder> indexMapping) {
    updateIndexDynamicSettingsAndMappings(esClient, indexMapping);
    if (indexMapping.isCreateFromTemplate()) {
      updateTemplateDynamicSettingsAndMappings(esClient, indexMapping);
    }
  }

  private boolean indicesExistWithNames(
      final OptimizeElasticsearchClient esClient, final List<String> indexNames) {
    return StreamSupport.stream(
            Iterables.partition(indexNames, INDEX_EXIST_BATCH_SIZE).spliterator(), true)
        .allMatch(
            indices -> {
              final GetIndexRequest request = new GetIndexRequest(indices.toArray(new String[] {}));
              try {
                return esClient.exists(request);
              } catch (final IOException e) {
                final String message =
                    String.format(
                        "Could not check if [%s] index(es) already exist.",
                        String.join(",", indices));
                throw new OptimizeRuntimeException(message, e);
              }
            });
  }

  private void createOptimizeIndexFromRequest(
      final OptimizeElasticsearchClient esClient,
      final IndexMappingCreator<XContentBuilder> mapping,
      final String indexName,
      final String defaultAliasName,
      final Set<String> additionalAliases,
      final Settings indexSettings)
      throws IOException {
    log.debug(
        "Creating Optimize Index with name {}, default alias {} and additional aliases {}",
        indexName,
        defaultAliasName,
        additionalAliases);
    final CreateIndexRequest request = new CreateIndexRequest(indexName);
    final Set<String> aliases = new HashSet<>(additionalAliases);
    aliases.add(defaultAliasName);
    aliases.forEach(
        additionalAliasName ->
            request.alias(
                new Alias(additionalAliasName)
                    .writeIndex(defaultAliasName.equals(additionalAliasName))));
    request.settings(indexSettings);
    request.mapping(mapping.getSource());
    esClient.createIndex(request);
  }

  private void createOptimizeIndexWithWriteAliasFromTemplate(
      final OptimizeElasticsearchClient esClient,
      final String indexNameWithSuffix,
      final String aliasName) {
    log.info("Creating index {} from template with write alias {}", indexNameWithSuffix, aliasName);
    final CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexNameWithSuffix);
    if (aliasName != null) {
      createIndexRequest.alias(new Alias(aliasName).writeIndex(true));
    }
    try {
      esClient.createIndex(createIndexRequest);
    } catch (final IOException e) {
      final String message =
          String.format("Could not create index %s from template.", indexNameWithSuffix);
      log.warn(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private void createOrUpdateTemplateWithAliases(
      final OptimizeElasticsearchClient esClient,
      final IndexMappingCreator<XContentBuilder> mappingCreator,
      final String defaultAliasName,
      final Set<String> additionalAliases,
      final Settings indexSettings) {
    final String templateName =
        indexNameService.getOptimizeIndexNameWithVersionWithoutSuffix(mappingCreator);
    log.info("Creating or updating template with name {}", templateName);

    final PutIndexTemplateRequest templateRequest =
        new PutIndexTemplateRequest(templateName)
            .version(mappingCreator.getVersion())
            .mapping(mappingCreator.getSource())
            .settings(indexSettings)
            .patterns(
                Collections.singletonList(
                    indexNameService.getOptimizeIndexNameWithVersionForAllIndicesOf(
                        mappingCreator)));

    additionalAliases.stream()
        .filter(aliasName -> !aliasName.equals(defaultAliasName))
        .map(
            aliasName -> {
              final Alias alias = new Alias(aliasName);
              alias.writeIndex(false);
              return alias;
            })
        .forEach(templateRequest::alias);

    try {
      esClient.createTemplate(templateRequest);
    } catch (final IOException e) {
      final String message = String.format("Could not create or update template %s", templateName);
      log.warn(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private void updateAllMappingsAndDynamicSettings(final OptimizeElasticsearchClient esClient) {
    log.info("Updating Optimize schema...");
    for (final IndexMappingCreator<XContentBuilder> mapping : mappings) {
      updateDynamicSettingsAndMappings(esClient, mapping);
    }

    final List<IndexMappingCreator<?>> allDynamicMappings =
        new MappingMetadataUtil(esClient).getAllDynamicMappings(indexNameService.getIndexPrefix());
    for (final IndexMappingCreator<?> mapping : allDynamicMappings) {
      updateDynamicSettingsAndMappings(esClient, (IndexMappingCreator<XContentBuilder>) mapping);
    }
    log.info("Finished updating Optimize schema.");
  }

  private void unblockIndices(final OptimizeElasticsearchClient esClient) {
    final boolean indexBlocked;
    try {
      final GetSettingsResponse settingsResponse = esClient.getIndexSettings();
      indexBlocked =
          Streams.stream(settingsResponse.getIndexToSettings().valuesIt())
              .anyMatch(settings -> settings.getAsBoolean(INDEX_READ_ONLY_SETTING, false));
    } catch (final IOException e) {
      log.error("Could not retrieve index settings!", e);
      throw new OptimizeRuntimeException("Could not retrieve index settings!", e);
    }

    if (indexBlocked) {
      log.info("Unblocking Elasticsearch indices...");
      final UpdateSettingsRequest updateSettingsRequest =
          new UpdateSettingsRequest(indexNameService.getIndexPrefix() + "*");
      updateSettingsRequest.settings(Settings.builder().put(INDEX_READ_ONLY_SETTING, false));
      try {
        esClient.updateSettings(updateSettingsRequest);
      } catch (final IOException e) {
        throw new OptimizeRuntimeException("Could not unblock Elasticsearch indices!", e);
      }
    }
  }

  private void updateTemplateDynamicSettingsAndMappings(
      final OptimizeElasticsearchClient esClient,
      final IndexMappingCreator<XContentBuilder> mappingCreator) {
    final String defaultAliasName =
        indexNameService.getOptimizeIndexAliasForIndex(mappingCreator.getIndexName());
    final Settings indexSettings = createIndexSettings(mappingCreator);
    createOrUpdateTemplateWithAliases(
        esClient, mappingCreator, defaultAliasName, Sets.newHashSet(), indexSettings);
  }

  private void updateIndexDynamicSettingsAndMappings(
      final OptimizeElasticsearchClient esClient,
      final IndexMappingCreator<XContentBuilder> indexMapping) {
    final String indexName =
        indexNameService.getOptimizeIndexNameWithVersionForAllIndicesOf(indexMapping);
    try {
      final Settings indexSettings = buildDynamicSettings(configurationService);
      final UpdateSettingsRequest updateSettingsRequest = new UpdateSettingsRequest();
      updateSettingsRequest.indices(indexName);
      updateSettingsRequest.settings(indexSettings);
      esClient.updateSettings(updateSettingsRequest);
    } catch (final IOException e) {
      final String message =
          String.format(
              "Could not update index settings for index [%s].", indexMapping.getIndexName());
      throw new OptimizeRuntimeException(message, e);
    }

    try {
      final PutMappingRequest putMappingRequest = new PutMappingRequest(indexName);
      putMappingRequest.source(indexMapping.getSource());
      esClient.createMapping(putMappingRequest);
    } catch (final IOException e) {
      final String message =
          String.format(
              "Could not update index mappings for index [%s].", indexMapping.getIndexName());
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private Settings createIndexSettings(
      final IndexMappingCreator<XContentBuilder> indexMappingCreator) {
    try {
      return ElasticSearchIndexSettingsBuilder.buildAllSettings(
          configurationService, indexMappingCreator);
    } catch (final IOException e) {
      log.error("Could not create settings!", e);
      throw new OptimizeRuntimeException("Could not create index settings");
    }
  }

  public static List<IndexMappingCreator<XContentBuilder>> getAllNonDynamicMappings() {
    return Arrays.asList(
        new AlertIndexES(),
        new BusinessKeyIndexES(),
        new CollectionIndexES(),
        new DashboardIndexES(),
        new DashboardShareIndexES(),
        new DecisionDefinitionIndexES(),
        new LicenseIndexES(),
        new MetadataIndexES(),
        new ProcessDefinitionIndexES(),
        new ReportShareIndexES(),
        new SettingsIndexES(),
        new TenantIndexES(),
        new TerminatedUserSessionIndexES(),
        new VariableUpdateInstanceIndexES(),
        new ImportIndexIndexES(),
        new TimestampBasedImportIndexES(),
        new PositionBasedImportIndexES(),
        new CombinedReportIndexES(),
        new SingleDecisionReportIndexES(),
        new SingleProcessReportIndexES(),
        new ExternalProcessVariableIndexES(),
        new VariableLabelIndexES(),
        new ProcessOverviewIndexES(),
        new InstantPreviewDashboardMetadataIndexES());
  }
}
