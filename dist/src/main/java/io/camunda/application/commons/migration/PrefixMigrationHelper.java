/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.migration;

import io.camunda.application.StandalonePrefixMigration.OperateIndexPrefixPropertiesOverride;
import io.camunda.application.StandalonePrefixMigration.TasklistIndexPrefixPropertiesOverride;
import io.camunda.migration.commons.storage.MigrationRepositoryIndex;
import io.camunda.migration.commons.storage.TasklistMigrationRepositoryIndex;
import io.camunda.migration.task.adapter.TaskLegacyIndex;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.schema.PrefixMigrationClient;
import io.camunda.search.schema.elasticsearch.ElasticsearchPrefixMigrationClient;
import io.camunda.search.schema.opensearch.OpensearchPrefixMigrationClient;
import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.index.ImportPositionIndex;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.index.TasklistImportPositionIndex;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.EventTemplate;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.template.JobTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.MessageTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.descriptors.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PrefixMigrationHelper {
  @VisibleForTesting
  public static final Set<Class<? extends AbstractIndexDescriptor>> TASKLIST_INDICES_TO_MIGRATE =
      Set.of(
          FormIndex.class,
          // Not pointing to the new TaskTemplate as we care about the old version
          TaskLegacyIndex.class,
          SnapshotTaskVariableTemplate.class,
          TasklistMigrationRepositoryIndex.class,
          TasklistImportPositionIndex.class);

  @VisibleForTesting
  public static final Set<Class<? extends AbstractIndexDescriptor>> OPERATE_INDICES_TO_MIGRATE =
      Set.of(
          ListViewTemplate.class,
          JobTemplate.class,
          MetricIndex.class,
          BatchOperationTemplate.class,
          ProcessIndex.class,
          DecisionRequirementsIndex.class,
          DecisionIndex.class,
          EventTemplate.class,
          VariableTemplate.class,
          PostImporterQueueTemplate.class,
          SequenceFlowTemplate.class,
          MessageTemplate.class,
          DecisionInstanceTemplate.class,
          IncidentTemplate.class,
          FlowNodeInstanceTemplate.class,
          OperationTemplate.class,
          ImportPositionIndex.class,
          MigrationRepositoryIndex.class);

  private static final Logger LOG = LoggerFactory.getLogger(PrefixMigrationHelper.class);

  private PrefixMigrationHelper() {}

  public static void runPrefixMigration(
      final OperateIndexPrefixPropertiesOverride operateProperties,
      final TasklistIndexPrefixPropertiesOverride tasklistProperties,
      final ConnectConfiguration connectConfiguration) {
    final var isElasticsearch = connectConfiguration.getTypeEnum() == DatabaseType.ELASTICSEARCH;

    final var operatePrefix =
        isElasticsearch
            ? operateProperties.elasticsearchIndexPrefix()
            : operateProperties.opensearchIndexPrefix();
    final var tasklistPrefix =
        isElasticsearch
            ? tasklistProperties.elasticsearchIndexPrefix()
            : tasklistProperties.opensearchIndexPrefix();

    final var executor = Executors.newVirtualThreadPerTaskExecutor();

    PrefixMigrationHelper.migrate(
        operatePrefix,
        tasklistPrefix,
        connectConfiguration,
        getPrefixMigrationClient(connectConfiguration),
        executor);

    LOG.info("Migration completed, shutting down executor.");
    executor.close();
  }

  private static PrefixMigrationClient getPrefixMigrationClient(
      final ConnectConfiguration connectConfiguration) {
    if (connectConfiguration.getTypeEnum().isElasticSearch()) {
      return new ElasticsearchPrefixMigrationClient(
          new ElasticsearchConnector(connectConfiguration).createClient());
    } else {
      return new OpensearchPrefixMigrationClient(
          new OpensearchConnector(connectConfiguration).createClient());
    }
  }

  public static void migrate(
      final String operatePrefix,
      final String tasklistPrefix,
      final ConnectConfiguration connectConfig,
      final PrefixMigrationClient prefixMigrationClient,
      final ExecutorService executor) {

    final boolean isElasticsearch = connectConfig.getTypeEnum().isElasticSearch();

    final var descriptors = new IndexDescriptors(connectConfig.getIndexPrefix(), isElasticsearch);
    final var cloneOperations = new ArrayList<AliasCloneTargets>();

    TASKLIST_INDICES_TO_MIGRATE.forEach(
        cls ->
            buildCloneTargets(
                    tasklistPrefix,
                    connectConfig.getIndexPrefix(),
                    isElasticsearch,
                    prefixMigrationClient,
                    cls,
                    descriptors)
                .map(cloneOperations::add));

    OPERATE_INDICES_TO_MIGRATE.forEach(
        cls ->
            buildCloneTargets(
                    operatePrefix,
                    connectConfig.getIndexPrefix(),
                    isElasticsearch,
                    prefixMigrationClient,
                    cls,
                    descriptors)
                .map(cloneOperations::add));

    final var futures =
        cloneOperations.stream()
            .parallel()
            .map(
                entry ->
                    CompletableFuture.supplyAsync(
                        () -> {
                          final var innerFutures =
                              entry.indices().stream()
                                  .parallel()
                                  .map(
                                      idx ->
                                          prefixMigrationClient.cloneAndDeleteIndex(
                                              idx.getLeft(),
                                              entry.srcAlias,
                                              idx.getRight(),
                                              entry.destAlias))
                                  .toArray(CompletableFuture[]::new);
                          return CompletableFuture.allOf(innerFutures).join();
                        },
                        executor))
            .toArray(CompletableFuture[]::new);

    CompletableFuture.allOf(futures).join();
  }

  private static Optional<AliasCloneTargets> buildCloneTargets(
      final String oldPrefix,
      final String newPrefix,
      final boolean isElasticsearch,
      final PrefixMigrationClient prefixMigrationClient,
      final Class<? extends AbstractIndexDescriptor> cls,
      final IndexDescriptors descriptors) {
    final var descriptor = getDescriptor(cls, descriptors, newPrefix, isElasticsearch);
    final var srcAlias = getSourceAliasFromDescriptor(oldPrefix, descriptor);
    final var destAlias = getDestinationAliasFromDescriptor(descriptor);
    final var srcIndices = prefixMigrationClient.getIndicesInAlias(srcAlias);

    final var indicesToMigrate =
        srcIndices.stream()
            .map(
                srcIndex -> {
                  final String destIndex;
                  if (isHistoricIndex(
                      srcIndex, descriptor.getIndexName(), descriptor.getVersion())) {
                    destIndex =
                        srcIndex.replace(
                            oldPrefix,
                            descriptor.getIndexPrefix() + "-" + descriptor.getComponentName());
                  } else {
                    destIndex = descriptor.getFullQualifiedName();
                  }
                  return Tuple.of(srcIndex, destIndex);
                })
            .toList();

    if (!indicesToMigrate.isEmpty()) {
      return Optional.of(new AliasCloneTargets(srcAlias, destAlias, indicesToMigrate));
    }
    return Optional.empty();
  }

  public static boolean isHistoricIndex(
      final String indexName, final String properIndexName, final String version) {
    return !indexName.endsWith("-%s-%s_".formatted(properIndexName, version));
  }

  private static String getSourceAliasFromDescriptor(
      final String oldPrefix, final AbstractIndexDescriptor descriptor) {
    final String aliasFormat = "%s-%s-%s_alias";
    return String.format(
        aliasFormat, oldPrefix, descriptor.getIndexName(), descriptor.getVersion());
  }

  private static String getDestinationAliasFromDescriptor(
      final AbstractIndexDescriptor descriptor) {
    return descriptor.getAlias();
  }

  @VisibleForTesting
  public static AbstractIndexDescriptor getDescriptor(
      final Class<? extends AbstractIndexDescriptor> cls,
      final IndexDescriptors descriptors,
      final String prefix,
      final boolean isElasticsearch) {
    if (cls.equals(TaskLegacyIndex.class)) {
      return new TaskLegacyIndex(prefix, isElasticsearch);
    } else if (cls.equals(TasklistMigrationRepositoryIndex.class)) {
      return new TasklistMigrationRepositoryIndex(prefix, isElasticsearch);
    } else if (cls.equals(MigrationRepositoryIndex.class)) {
      return new MigrationRepositoryIndex(prefix, isElasticsearch);
    }
    return descriptors.get(cls);
  }

  private record AliasCloneTargets(
      String srcAlias, String destAlias, List<Tuple<String, String>> indices) {}
}
