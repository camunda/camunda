/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.schema;

import static io.camunda.optimize.service.db.DatabaseClient.convertToPrefixedAliasNames;
import static io.camunda.optimize.service.db.DatabaseConstants.INDEX_ALREADY_EXISTS_EXCEPTION_TYPE;
import static java.util.stream.Collectors.toSet;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.indices.Alias;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetIndicesSettingsResponse;
import co.elastic.clients.elasticsearch.indices.IndexSettingBlocks;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.PutIndicesSettingsRequest;
import co.elastic.clients.elasticsearch.indices.PutMappingRequest;
import co.elastic.clients.elasticsearch.indices.PutTemplateRequest;
import co.elastic.clients.util.Pair;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import io.camunda.optimize.service.db.es.MappingMetadataUtilES;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.schema.index.AlertIndexES;
import io.camunda.optimize.service.db.es.schema.index.BusinessKeyIndexES;
import io.camunda.optimize.service.db.es.schema.index.CollectionIndexES;
import io.camunda.optimize.service.db.es.schema.index.DashboardIndexES;
import io.camunda.optimize.service.db.es.schema.index.DashboardShareIndexES;
import io.camunda.optimize.service.db.es.schema.index.DecisionDefinitionIndexES;
import io.camunda.optimize.service.db.es.schema.index.ExternalProcessVariableIndexES;
import io.camunda.optimize.service.db.es.schema.index.InstantPreviewDashboardMetadataIndexES;
import io.camunda.optimize.service.db.es.schema.index.MetadataIndexES;
import io.camunda.optimize.service.db.es.schema.index.ProcessDefinitionIndexES;
import io.camunda.optimize.service.db.es.schema.index.ProcessOverviewIndexES;
import io.camunda.optimize.service.db.es.schema.index.ReportShareIndexES;
import io.camunda.optimize.service.db.es.schema.index.SettingsIndexES;
import io.camunda.optimize.service.db.es.schema.index.TenantIndexES;
import io.camunda.optimize.service.db.es.schema.index.TerminatedUserSessionIndexES;
import io.camunda.optimize.service.db.es.schema.index.VariableLabelIndexES;
import io.camunda.optimize.service.db.es.schema.index.VariableUpdateInstanceIndexES;
import io.camunda.optimize.service.db.es.schema.index.index.PositionBasedImportIndexES;
import io.camunda.optimize.service.db.es.schema.index.index.TimestampBasedImportIndexES;
import io.camunda.optimize.service.db.es.schema.index.report.CombinedReportIndexES;
import io.camunda.optimize.service.db.es.schema.index.report.SingleDecisionReportIndexES;
import io.camunda.optimize.service.db.es.schema.index.report.SingleProcessReportIndexES;
import io.camunda.optimize.service.db.schema.DatabaseSchemaManager;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
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
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ElasticSearchSchemaManager
    extends DatabaseSchemaManager<OptimizeElasticsearchClient, IndexSettings.Builder> {

  public static final int INDEX_EXIST_BATCH_SIZE = 10;
  private static final String INDEX_READ_ONLY_SETTING = "index.blocks.read_only_allow_delete";
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ElasticSearchSchemaManager.class);
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
      final List<IndexMappingCreator<IndexSettings.Builder>> mappings) {
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
      LOG.info("Initializing Optimize schema...");
      createOptimizeIndices(esClient);
      LOG.info("Optimize schema initialized successfully.");
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
      final IndexMappingCreator<IndexSettings.Builder> mapping) {
    return indicesExist(esClient, Collections.singletonList(mapping));
  }

  @Override
  public boolean indexExists(final OptimizeElasticsearchClient esClient, final String indexName) {
    return indicesExistWithNames(esClient, Collections.singletonList(indexName));
  }

  @Override
  public boolean indicesExist(
      final OptimizeElasticsearchClient esClient,
      final List<IndexMappingCreator<IndexSettings.Builder>> mappings) {
    return indicesExistWithNames(
        esClient, mappings.stream().map(IndexMappingCreator::getIndexName).toList());
  }

  @Override
  public void createIndexIfMissing(
      final OptimizeElasticsearchClient esClient,
      final IndexMappingCreator<IndexSettings.Builder> indexMapping) {
    createIndexIfMissing(esClient, indexMapping, Collections.emptySet());
  }

  @Override
  public void createIndexIfMissing(
      final OptimizeElasticsearchClient esClient,
      final IndexMappingCreator<IndexSettings.Builder> indexMapping,
      final Set<String> additionalReadOnlyAliases) {
    try {
      final boolean indexAlreadyExists = indexExists(esClient, indexMapping);
      if (!indexAlreadyExists) {
        createOrUpdateOptimizeIndex(esClient, indexMapping, additionalReadOnlyAliases);
      }
    } catch (final Exception e) {
      LOG.error("Failed ensuring index is present: {}", indexMapping.getIndexName(), e);
      throw e;
    }
  }

  @Override
  public void createOrUpdateOptimizeIndex(
      final OptimizeElasticsearchClient esClient,
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
    } catch (final ElasticsearchException e) {
      if (e.status() == 400 && e.getMessage().contains(INDEX_ALREADY_EXISTS_EXCEPTION_TYPE)) {
        LOG.debug(
            "index {} already exists, updating mapping and dynamic settings.", suffixedIndexName);
        updateDynamicSettingsAndMappings(esClient, mapping);
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
      final OptimizeElasticsearchClient esClient,
      final IndexMappingCreator<IndexSettings.Builder> mapping) {
    try {
      esClient.deleteIndex(mapping);
    } catch (final ElasticsearchException e) {
      if (e.status() == 404) {
        LOG.debug("Index {} was not found.", mapping.getIndexName());
      } else {
        throw e;
      }
    }
  }

  @Override
  public void createOrUpdateTemplateWithoutAliases(
      final OptimizeElasticsearchClient esClient,
      final IndexMappingCreator<IndexSettings.Builder> mappingCreator) {
    final String templateName =
        indexNameService.getOptimizeIndexTemplateNameWithVersion(mappingCreator);
    final IndexSettings indexSettings = createIndexSettings(mappingCreator);
    LOG.debug("Creating or updating template with name {}.", templateName);
    try {
      esClient.createTemplate(
          PutTemplateRequest.of(
              b ->
                  b.name(templateName)
                      .version((long) mappingCreator.getVersion())
                      .mappings(mappingCreator.getSource())
                      .settings(indexSettings)
                      .indexPatterns(
                          Collections.singletonList(
                              indexNameService.getOptimizeIndexNameWithVersionWithWildcardSuffix(
                                  mappingCreator)))));
    } catch (final Exception e) {
      final String message = String.format("Could not create or update template %s.", templateName);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @Override
  public void updateDynamicSettingsAndMappings(
      final OptimizeElasticsearchClient esClient,
      final IndexMappingCreator<IndexSettings.Builder> indexMapping) {
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
              try {
                return esClient.exists(
                    ExistsRequest.of(
                        e ->
                            e.index(
                                List.of(
                                    convertToPrefixedAliasNames(
                                        indices.toArray(String[]::new), esClient)))));
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
      final IndexMappingCreator<IndexSettings.Builder> mapping,
      final String indexName,
      final String defaultAliasName,
      final Set<String> additionalAliases,
      final IndexSettings indexSettings)
      throws IOException {
    LOG.debug(
        "Creating Optimize Index with name {}, default alias {} and additional aliases {}",
        indexName,
        defaultAliasName,
        additionalAliases);
    esClient.createIndex(
        CreateIndexRequest.of(
            r -> {
              final Set<String> aliases = new HashSet<>(additionalAliases);
              r.index(indexName);
              aliases.add(defaultAliasName);
              aliases.forEach(
                  additionalAliasName ->
                      r.aliases(
                          additionalAliasName,
                          a -> a.isWriteIndex(defaultAliasName.equals(additionalAliasName))));
              r.settings(indexSettings);
              r.mappings(mapping.getSource());
              return r;
            }));
  }

  private void createOptimizeIndexWithWriteAliasFromTemplate(
      final OptimizeElasticsearchClient esClient,
      final String indexNameWithSuffix,
      final String aliasName) {
    LOG.info("Creating index {} from template with write alias {}", indexNameWithSuffix, aliasName);
    try {
      esClient.createIndex(
          CreateIndexRequest.of(
              c -> {
                c.index(indexNameWithSuffix);
                if (aliasName != null) {
                  c.aliases(aliasName, a -> a.isWriteIndex(true));
                }
                return c;
              }));
    } catch (final IOException e) {
      final String message =
          String.format("Could not create index %s from template.", indexNameWithSuffix);
      LOG.warn(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private void createOrUpdateTemplateWithAliases(
      final OptimizeElasticsearchClient esClient,
      final IndexMappingCreator<IndexSettings.Builder> mappingCreator,
      final String defaultAliasName,
      final Set<String> additionalAliases,
      final IndexSettings indexSettings) {
    final String templateName =
        indexNameService.getOptimizeIndexNameWithVersionWithoutSuffix(mappingCreator);
    LOG.info("Creating or updating template with name {}", templateName);
    try {
      esClient.createTemplate(
          PutTemplateRequest.of(
              b -> {
                b.name(templateName)
                    .version((long) mappingCreator.getVersion())
                    .mappings(mappingCreator.getSource())
                    .settings(indexSettings)
                    .indexPatterns(
                        Collections.singletonList(
                            indexNameService.getOptimizeIndexNameWithVersionWithWildcardSuffix(
                                mappingCreator)));
                additionalAliases.stream()
                    .filter(aliasName -> !aliasName.equals(defaultAliasName))
                    .map(aliasName -> Pair.of(aliasName, Alias.of(a -> a.isWriteIndex(false))))
                    .forEach((p) -> b.aliases(p.key(), p.value()));
                return b;
              }));
    } catch (final IOException e) {
      final String message = String.format("Could not create or update template %s", templateName);
      LOG.warn(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private void updateAllMappingsAndDynamicSettings(final OptimizeElasticsearchClient esClient) {
    LOG.info("Updating Optimize schema...");
    for (final IndexMappingCreator<IndexSettings.Builder> mapping : mappings) {
      updateDynamicSettingsAndMappings(esClient, mapping);
    }

    final List<IndexMappingCreator<IndexSettings.Builder>> allDynamicMappings =
        new MappingMetadataUtilES(esClient)
            .getAllDynamicMappings(indexNameService.getIndexPrefix());
    for (final IndexMappingCreator<IndexSettings.Builder> mapping : allDynamicMappings) {
      updateDynamicSettingsAndMappings(esClient, mapping);
    }
    LOG.info("Finished updating Optimize schema.");
  }

  private void unblockIndices(final OptimizeElasticsearchClient esClient) {
    final boolean indexBlocked;
    try {
      final GetIndicesSettingsResponse settingsResponse = esClient.getOldIndexSettings();
      indexBlocked =
          settingsResponse.result().values().stream()
              .anyMatch(
                  settings -> {
                    final IndexSettingBlocks s = settings.settings().blocks();
                    if (s != null && s.readOnly() != null) {
                      return s.readOnly();
                    } else {
                      return false;
                    }
                  });
    } catch (final IOException e) {
      LOG.error("Could not retrieve index settings!", e);
      throw new OptimizeRuntimeException("Could not retrieve index settings!", e);
    }

    if (indexBlocked) {
      LOG.info("Unblocking Elasticsearch indices...");
      try {
        esClient.updateSettings(
            PutIndicesSettingsRequest.of(
                p ->
                    p.index(indexNameService.getIndexPrefix() + "*")
                        .settings(
                            i -> i.settings(s -> s.index(f -> f.blocks(b -> b.readOnly(false)))))));
      } catch (final IOException e) {
        throw new OptimizeRuntimeException("Could not unblock Elasticsearch indices!", e);
      }
    }
  }

  private void updateTemplateDynamicSettingsAndMappings(
      final OptimizeElasticsearchClient esClient,
      final IndexMappingCreator<IndexSettings.Builder> mappingCreator) {
    final String defaultAliasName =
        indexNameService.getOptimizeIndexAliasForIndex(mappingCreator.getIndexName());
    final IndexSettings indexSettings = createIndexSettings(mappingCreator);
    createOrUpdateTemplateWithAliases(
        esClient, mappingCreator, defaultAliasName, Sets.newHashSet(), indexSettings);
  }

  private void updateIndexDynamicSettingsAndMappings(
      final OptimizeElasticsearchClient esClient,
      final IndexMappingCreator<IndexSettings.Builder> indexMapping) {
    final String indexName =
        indexNameService.getOptimizeIndexNameWithVersionForAllIndicesOf(indexMapping);
    try {
      final IndexSettings indexSettings =
          ElasticSearchIndexSettingsBuilder.buildDynamicSettings(configurationService);
      esClient.updateSettings(
          PutIndicesSettingsRequest.of(p -> p.index(indexName).settings(indexSettings)));
    } catch (final IOException e) {
      final String message =
          String.format(
              "Could not update index settings for index [%s].", indexMapping.getIndexName());
      throw new OptimizeRuntimeException(message, e);
    }

    try {
      esClient.createMapping(
          PutMappingRequest.of(
              m ->
                  m.index(indexName)
                      .source(indexMapping.getSource().source())
                      .dateDetection(indexMapping.getSource().dateDetection())
                      .dynamic(indexMapping.getSource().dynamic())
                      .dynamicDateFormats(indexMapping.getSource().dynamicDateFormats())
                      .dynamicTemplates(indexMapping.getSource().dynamicTemplates())
                      .fieldNames(indexMapping.getSource().fieldNames())
                      .meta(indexMapping.getSource().meta())
                      .numericDetection(indexMapping.getSource().numericDetection())
                      .properties(indexMapping.getSource().properties())
                      .routing(indexMapping.getSource().routing())
                      .runtime(indexMapping.getSource().runtime())));
    } catch (final IOException e) {
      final String message =
          String.format(
              "Could not update index mappings for index [%s].", indexMapping.getIndexName());
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private IndexSettings createIndexSettings(
      final IndexMappingCreator<IndexSettings.Builder> indexMappingCreator) {
    try {
      return ElasticSearchIndexSettingsBuilder.buildAllSettings(
          configurationService, indexMappingCreator);
    } catch (final IOException e) {
      LOG.error("Could not create settings!", e);
      throw new OptimizeRuntimeException("Could not create index settings");
    }
  }

  public static List<IndexMappingCreator<IndexSettings.Builder>> getAllNonDynamicMappings() {
    return Arrays.asList(
        new AlertIndexES(),
        new BusinessKeyIndexES(),
        new CollectionIndexES(),
        new DashboardIndexES(),
        new DashboardShareIndexES(),
        new DecisionDefinitionIndexES(),
        new MetadataIndexES(),
        new ProcessDefinitionIndexES(),
        new ReportShareIndexES(),
        new SettingsIndexES(),
        new TenantIndexES(),
        new TerminatedUserSessionIndexES(),
        new VariableUpdateInstanceIndexES(),
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
