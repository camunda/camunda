/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.schema.MappingMetadataUtil;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.AlertIndexES;
import org.camunda.optimize.service.es.schema.index.BusinessKeyIndexES;
import org.camunda.optimize.service.es.schema.index.CollectionIndexES;
import org.camunda.optimize.service.es.schema.index.DashboardIndexES;
import org.camunda.optimize.service.es.schema.index.DashboardShareIndexES;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndexES;
import org.camunda.optimize.service.es.schema.index.ExternalProcessVariableIndexES;
import org.camunda.optimize.service.es.schema.index.InstantPreviewDashboardMetadataIndexES;
import org.camunda.optimize.service.es.schema.index.LicenseIndexES;
import org.camunda.optimize.service.es.schema.index.MetadataIndexES;
import org.camunda.optimize.service.es.schema.index.OnboardingStateIndexES;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndexES;
import org.camunda.optimize.service.es.schema.index.ProcessOverviewIndexES;
import org.camunda.optimize.service.es.schema.index.ReportShareIndexES;
import org.camunda.optimize.service.es.schema.index.SettingsIndexES;
import org.camunda.optimize.service.es.schema.index.TenantIndexES;
import org.camunda.optimize.service.es.schema.index.TerminatedUserSessionIndexES;
import org.camunda.optimize.service.es.schema.index.VariableLabelIndexES;
import org.camunda.optimize.service.es.schema.index.VariableUpdateInstanceIndexES;
import org.camunda.optimize.service.es.schema.index.events.EventIndexES;
import org.camunda.optimize.service.es.schema.index.events.EventProcessDefinitionIndexES;
import org.camunda.optimize.service.es.schema.index.events.EventProcessMappingIndexES;
import org.camunda.optimize.service.es.schema.index.events.EventProcessPublishStateIndexES;
import org.camunda.optimize.service.es.schema.index.index.ImportIndexIndexES;
import org.camunda.optimize.service.es.schema.index.index.PositionBasedImportIndexES;
import org.camunda.optimize.service.es.schema.index.index.TimestampBasedImportIndexES;
import org.camunda.optimize.service.es.schema.index.report.CombinedReportIndexES;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndexES;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndexES;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
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
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.service.es.schema.IndexSettingsBuilderES.buildDynamicSettings;
import static org.camunda.optimize.service.db.DatabaseConstants.INDEX_ALREADY_EXISTS_EXCEPTION_TYPE;

@Component
@Slf4j
public class ElasticSearchSchemaManager {
    private static final String INDEX_READ_ONLY_SETTING = "index.blocks.read_only_allow_delete";
    public static final int INDEX_EXIST_BATCH_SIZE = 10;

    private final ElasticsearchMetadataService metadataService;
    private final ConfigurationService configurationService;
    private final OptimizeIndexNameService indexNameService;

    private final List<IndexMappingCreator<?>> mappings;

    @Autowired
    public ElasticSearchSchemaManager(final ElasticsearchMetadataService metadataService,
                                      final ConfigurationService configurationService,
                                      final OptimizeIndexNameService indexNameService) {
        this.metadataService = metadataService;
        this.configurationService = configurationService;
        this.indexNameService = indexNameService;
        this.mappings = new ArrayList<>();
        mappings.addAll(getAllNonDynamicMappings());
    }

    public ElasticSearchSchemaManager(final ElasticsearchMetadataService metadataService,
                                      final ConfigurationService configurationService,
                                      final OptimizeIndexNameService indexNameService,
                                      final List<IndexMappingCreator<?>> mappings) {
        this.metadataService = metadataService;
        this.configurationService = configurationService;
        this.indexNameService = indexNameService;
        this.mappings = mappings;
    }

    public void validateExistingSchemaVersion(final OptimizeElasticsearchClient esClient) {
        metadataService.validateSchemaVersionCompatibility(esClient);
    }

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

    public void addMapping(IndexMappingCreator<?> mapping) {
        mappings.add(mapping);
    }

    public List<IndexMappingCreator<?>> getMappings() {
        return mappings;
    }

    public boolean schemaExists(OptimizeElasticsearchClient esClient) {
        return indicesExist(esClient, getMappings());
    }

    public boolean indexExists(final OptimizeElasticsearchClient esClient,
                               final IndexMappingCreator<?> mapping) {
        return indicesExist(esClient, Collections.singletonList(mapping));
    }

    public boolean indexExists(final OptimizeElasticsearchClient esClient,
                               final String indexName) {
        return indicesExistWithNames(esClient, Collections.singletonList(indexName));
    }

    public boolean indicesExist(final OptimizeElasticsearchClient esClient,
                                final List<IndexMappingCreator<?>> mappings) {
        return indicesExistWithNames(
                esClient,
                mappings.stream()
                        .map(IndexMappingCreator::getIndexName)
                        .collect(toList())
        );
    }

    public void createIndexIfMissing(final OptimizeElasticsearchClient esClient,
                                     final IndexMappingCreator<?> indexMapping) {
        createIndexIfMissing(esClient, indexMapping, Collections.emptySet());
    }

    public void createIndexIfMissing(final OptimizeElasticsearchClient esClient,
                                     final IndexMappingCreator<?> indexMapping,
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

    /**
     * NOTE: create one alias and index per type
     * <p>
     * https://www.elastic.co/guide/en/elasticsearch/reference/6.0/indices-aliases.html
     */
    public void createOptimizeIndices(OptimizeElasticsearchClient esClient) {
        for (IndexMappingCreator<?> mapping : mappings) {
            createOrUpdateOptimizeIndex(esClient, mapping);
        }
    }

    public void createOrUpdateOptimizeIndex(final OptimizeElasticsearchClient esClient,
                                            final IndexMappingCreator<?> mapping) {
        createOrUpdateOptimizeIndex(esClient, mapping, Collections.emptySet());
    }

    public void createOrUpdateOptimizeIndex(final OptimizeElasticsearchClient esClient,
                                            final IndexMappingCreator<?> mapping,
                                            final Set<String> readOnlyAliases) {
        final Set<String> prefixedReadOnlyAliases =
                readOnlyAliases.stream()
                        .map(indexNameService::getOptimizeIndexAliasForIndex)
                        .collect(toSet());
        final String defaultAliasName = indexNameService.getOptimizeIndexAliasForIndex(mapping.getIndexName());
        final String suffixedIndexName = indexNameService.getOptimizeIndexNameWithVersion(mapping);
        final Settings indexSettings = createIndexSettings(mapping);

        try {
            if (mapping.isCreateFromTemplate()) {
                // Creating template without alias and adding aliases manually to indices created from this template to
                // ensure correct alias handling on rollover
                createOrUpdateTemplateWithAliases(
                        esClient, mapping, defaultAliasName, prefixedReadOnlyAliases, indexSettings
                );
                createOptimizeIndexWithWriteAliasFromTemplate(esClient, suffixedIndexName, defaultAliasName);
            } else {
                createOptimizeIndexFromRequest(
                        esClient, mapping, suffixedIndexName, defaultAliasName, prefixedReadOnlyAliases, indexSettings
                );
            }
        } catch (ElasticsearchStatusException e) {
            if (e.status() == RestStatus.BAD_REQUEST && e.getMessage().contains(INDEX_ALREADY_EXISTS_EXCEPTION_TYPE)) {
                log.debug("index {} already exists, updating mapping and dynamic settings.", suffixedIndexName);
                updateDynamicSettingsAndMappings(esClient, mapping);
            } else {
                throw e;
            }
        } catch (Exception e) {
            String message = String.format("Could not create Index [%s]", suffixedIndexName);
            log.warn(message, e);
            throw new OptimizeRuntimeException(message, e);
        }
    }

    public void deleteOptimizeIndex(final OptimizeElasticsearchClient esClient, final IndexMappingCreator<?> mapping) {
        try {
            esClient.deleteIndex(mapping);
        } catch (ElasticsearchStatusException e) {
            if (e.status() == RestStatus.NOT_FOUND) {
                log.debug("Index {} was not found.", mapping.getIndexName());
            } else {
                throw e;
            }
        }
    }

    public void createOrUpdateTemplateWithoutAliases(final OptimizeElasticsearchClient esClient,
                                                     final IndexMappingCreator<?> mappingCreator) {
        final String templateName = indexNameService.getOptimizeIndexTemplateNameWithVersion(mappingCreator);
        final Settings indexSettings = createIndexSettings(mappingCreator);

        log.debug("Creating or updating template with name {}.", templateName);
        PutIndexTemplateRequest templateRequest = new PutIndexTemplateRequest(templateName)
                .version(mappingCreator.getVersion())
                .mapping(mappingCreator.getSource())
                .settings(indexSettings)
                .patterns(Collections.singletonList(
                        indexNameService.getOptimizeIndexNameWithVersionWithWildcardSuffix(mappingCreator)
                ));
        try {
            esClient.createTemplate(templateRequest);
        } catch (Exception e) {
            final String message = String.format("Could not create or update template %s.", templateName);
            throw new OptimizeRuntimeException(message, e);
        }
    }

    public void updateDynamicSettingsAndMappings(OptimizeElasticsearchClient esClient,
                                                 IndexMappingCreator<?> indexMapping) {
        updateIndexDynamicSettingsAndMappings(esClient, indexMapping);
        if (indexMapping.isCreateFromTemplate()) {
            updateTemplateDynamicSettingsAndMappings(esClient, indexMapping);
        }
    }

    private boolean indicesExistWithNames(final OptimizeElasticsearchClient esClient,
                                          final List<String> indexNames) {
        return StreamSupport.stream(Iterables.partition(indexNames, INDEX_EXIST_BATCH_SIZE).spliterator(), true)
                .allMatch(indices -> {
                    final GetIndexRequest request = new GetIndexRequest(indices.toArray(new String[]{}));
                    try {
                        return esClient.exists(request);
                    } catch (IOException e) {
                        final String message = String.format(
                                "Could not check if [%s] index(es) already exist.", String.join(",", indices)
                        );
                        throw new OptimizeRuntimeException(message, e);
                    }
                });
    }

    private void createOptimizeIndexFromRequest(final OptimizeElasticsearchClient esClient,
                                                final IndexMappingCreator<?> mapping,
                                                final String indexName,
                                                final String defaultAliasName,
                                                final Set<String> additionalAliases,
                                                final Settings indexSettings) throws IOException {
        log.debug("Creating Optimize Index with name {}, default alias {} and additional aliases {}",
                indexName, defaultAliasName, additionalAliases
        );
        final CreateIndexRequest request = new CreateIndexRequest(indexName);
        final Set<String> aliases = new HashSet<>(additionalAliases);
        aliases.add(defaultAliasName);
        aliases.forEach(
                additionalAliasName -> request.alias(
                        new Alias(additionalAliasName).writeIndex(defaultAliasName.equals(additionalAliasName))
                )
        );
        request.settings(indexSettings);
        request.mapping(mapping.getSource());
        esClient.createIndex(request);
    }

    private void createOptimizeIndexWithWriteAliasFromTemplate(final OptimizeElasticsearchClient esClient,
                                                               final String indexNameWithSuffix,
                                                               final String aliasName) {
        log.info("Creating index {} from template with write alias {}", indexNameWithSuffix, aliasName);
        final CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexNameWithSuffix);
        if (aliasName != null) {
            createIndexRequest.alias(new Alias(aliasName).writeIndex(true));
        }
        try {
            esClient.createIndex(createIndexRequest);
        } catch (IOException e) {
            String message = String.format("Could not create index %s from template.", indexNameWithSuffix);
            log.warn(message, e);
            throw new OptimizeRuntimeException(message, e);
        }
    }

    private void createOrUpdateTemplateWithAliases(final OptimizeElasticsearchClient esClient,
                                                   final IndexMappingCreator<?> mappingCreator,
                                                   final String defaultAliasName,
                                                   final Set<String> additionalAliases,
                                                   final Settings indexSettings) {
        final String templateName = indexNameService.getOptimizeIndexNameWithVersionWithoutSuffix(mappingCreator);
        log.info("Creating or updating template with name {}", templateName);

        PutIndexTemplateRequest templateRequest = new PutIndexTemplateRequest(templateName)
                .version(mappingCreator.getVersion())
                .mapping(mappingCreator.getSource())
                .settings(indexSettings)
                .patterns(Collections.singletonList(indexNameService.getOptimizeIndexNameWithVersionForAllIndicesOf(mappingCreator)));

        additionalAliases.stream()
                .filter(aliasName -> !aliasName.equals(defaultAliasName))
                .map(aliasName -> {
                    final Alias alias = new Alias(aliasName);
                    alias.writeIndex(false);
                    return alias;
                })
                .forEach(templateRequest::alias);

        try {
            esClient.createTemplate(templateRequest);
        } catch (IOException e) {
            String message = String.format("Could not create or update template %s", templateName);
            log.warn(message, e);
            throw new OptimizeRuntimeException(message, e);
        }
    }

    private void updateAllMappingsAndDynamicSettings(OptimizeElasticsearchClient esClient) {
        log.info("Updating Optimize schema...");
        for (IndexMappingCreator<?> mapping : mappings) {
            updateDynamicSettingsAndMappings(esClient, mapping);
        }

        final List<IndexMappingCreator<?>> allDynamicMappings =
          new MappingMetadataUtil(esClient).getAllDynamicMappings();
        for (IndexMappingCreator<?> mapping : allDynamicMappings) {
            updateDynamicSettingsAndMappings(esClient, mapping);
        }
        log.info("Finished updating Optimize schema.");
    }

    private void unblockIndices(final OptimizeElasticsearchClient esClient) {
        final boolean indexBlocked;
        try {
            final GetSettingsResponse settingsResponse = esClient.getIndexSettings();
            indexBlocked = Streams.stream(settingsResponse.getIndexToSettings().valuesIt())
                    .anyMatch(settings -> settings.getAsBoolean(INDEX_READ_ONLY_SETTING, false));
        } catch (IOException e) {
            log.error("Could not retrieve index settings!", e);
            throw new OptimizeRuntimeException("Could not retrieve index settings!", e);
        }

        if (indexBlocked) {
            log.info("Unblocking Elasticsearch indices...");
            final UpdateSettingsRequest updateSettingsRequest = new UpdateSettingsRequest(
                    indexNameService.getIndexPrefix() + "*"
            );
            updateSettingsRequest.settings(Settings.builder().put(INDEX_READ_ONLY_SETTING, false));
            try {
                esClient.updateSettings(updateSettingsRequest);
            } catch (IOException e) {
                throw new OptimizeRuntimeException("Could not unblock Elasticsearch indices!", e);
            }
        }
    }

    private void updateTemplateDynamicSettingsAndMappings(OptimizeElasticsearchClient esClient,
                                                          IndexMappingCreator<?> mappingCreator) {
        final String defaultAliasName = indexNameService.getOptimizeIndexAliasForIndex(mappingCreator.getIndexName());
        final Settings indexSettings = createIndexSettings(mappingCreator);
        createOrUpdateTemplateWithAliases(
                esClient, mappingCreator, defaultAliasName, Sets.newHashSet(), indexSettings
        );
    }

    private void updateIndexDynamicSettingsAndMappings(OptimizeElasticsearchClient esClient,
                                                       IndexMappingCreator<?> indexMapping) {
        final String indexName = indexNameService.getOptimizeIndexNameWithVersionForAllIndicesOf(indexMapping);
        try {
            final Settings indexSettings = buildDynamicSettings(configurationService);
            final UpdateSettingsRequest updateSettingsRequest = new UpdateSettingsRequest();
            updateSettingsRequest.indices(indexName);
            updateSettingsRequest.settings(indexSettings);
            esClient.updateSettings(updateSettingsRequest);
        } catch (IOException e) {
            String message = String.format("Could not update index settings for index [%s].", indexMapping.getIndexName());
            throw new OptimizeRuntimeException(message, e);
        }

        try {
            final PutMappingRequest putMappingRequest = new PutMappingRequest(indexName);
            putMappingRequest.source(indexMapping.getSource());
            esClient.createMapping(putMappingRequest);
        } catch (IOException e) {
            String message = String.format("Could not update index mappings for index [%s].", indexMapping.getIndexName());
            throw new OptimizeRuntimeException(message, e);
        }
    }

    private Settings createIndexSettings(IndexMappingCreator<?> indexMappingCreator) {
        try {
            return IndexSettingsBuilderES.buildAllSettings(configurationService,
                                                           (IndexMappingCreator<XContentBuilder>) indexMappingCreator
            );
        } catch (IOException e) {
            log.error("Could not create settings!", e);
            throw new OptimizeRuntimeException("Could not create index settings");
        }
    }

    public static List<IndexMappingCreator<?>> getAllNonDynamicMappings() {
        return Arrays.asList(
                new AlertIndexES(),
                new BusinessKeyIndexES(),
                new CollectionIndexES(),
                new DashboardIndexES(),
                new DashboardShareIndexES(),
                new DecisionDefinitionIndexES(),
                new LicenseIndexES(),
                new MetadataIndexES(),
                new OnboardingStateIndexES(),
                new ProcessDefinitionIndexES(),
                new ReportShareIndexES(),
                new SettingsIndexES(),
                new TenantIndexES(),
                new TerminatedUserSessionIndexES(),
                new VariableUpdateInstanceIndexES(),
                new EventIndexES(),
                new EventProcessDefinitionIndexES(),
                new EventProcessMappingIndexES(),
                new EventProcessPublishStateIndexES(),
                new ImportIndexIndexES(),
                new TimestampBasedImportIndexES(),
                new PositionBasedImportIndexES(),
                new CombinedReportIndexES(),
                new SingleDecisionReportIndexES(),
                new SingleProcessReportIndexES(),
                new ExternalProcessVariableIndexES(),
                new VariableLabelIndexES(),
                new ProcessOverviewIndexES(),
                new InstantPreviewDashboardMetadataIndexES()
        );
    }

}
