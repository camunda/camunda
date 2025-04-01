/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.migration;

import io.camunda.operate.property.OperateProperties;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.schema.PrefixMigrationClient;
import io.camunda.search.schema.elasticsearch.ElasticsearchPrefixMigrationClient;
import io.camunda.search.schema.opensearch.OpensearchPrefixMigrationClient;
import io.camunda.search.schema.utils.CloneResult;
import io.camunda.search.schema.utils.ReindexResult;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.operate.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.operate.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.operate.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.operate.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.EventTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.JobTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.MessageTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PrefixMigrationHelper {
  private static final Integer MIGRATION_MAX_RETRIES = 3;
  private static final Logger LOG = LoggerFactory.getLogger(PrefixMigrationHelper.class);

  private static final Set<Class<? extends AbstractIndexDescriptor>> TASKLIST_INDICES_TO_MIGRATE =
      new HashSet<>(Arrays.asList(FormIndex.class, TaskTemplate.class));

  private static final Set<Class<? extends AbstractIndexDescriptor>> OPERATE_INDICES_TO_MIGRATE =
      new HashSet<>(
          Arrays.asList(
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
              OperationTemplate.class));

  private PrefixMigrationHelper() {}

  public static void runPrefixMigration(
      final OperateProperties operateProperties,
      final TasklistProperties tasklistProperties,
      final ConnectConfiguration connectConfiguration) {
    final var isElasticsearch = connectConfiguration.getTypeEnum() == DatabaseType.ELASTICSEARCH;

    LOG.info("Migrating runtime indices");

    final var operatePrefix =
        isElasticsearch
            ? operateProperties.getElasticsearch().getIndexPrefix()
            : operateProperties.getOpensearch().getIndexPrefix();
    final var tasklistPrefix =
        isElasticsearch
            ? tasklistProperties.getElasticsearch().getIndexPrefix()
            : tasklistProperties.getOpenSearch().getIndexPrefix();

    final var executor = Executors.newVirtualThreadPerTaskExecutor();

    PrefixMigrationHelper.migrateRuntimeIndices(
        operatePrefix,
        tasklistPrefix,
        connectConfiguration,
        getPrefixMigrationClient(connectConfiguration),
        executor);

    LOG.info("... finished migrating runtime indices");

    LOG.info("Migrating historic indices");

    PrefixMigrationHelper.migrateHistoricIndices(
        operatePrefix,
        tasklistPrefix,
        connectConfiguration,
        getPrefixMigrationClient(connectConfiguration),
        executor);

    LOG.info("... finished migrating historic indices");
  }

  private static PrefixMigrationClient getPrefixMigrationClient(
      final ConnectConfiguration connectConfiguration) {
    if (connectConfiguration.getTypeEnum() == DatabaseType.ELASTICSEARCH) {

      return new ElasticsearchPrefixMigrationClient(
          new ElasticsearchConnector(connectConfiguration).createClient());
    } else {
      return new OpensearchPrefixMigrationClient(
          new OpensearchConnector(connectConfiguration).createClient());
    }
  }

  public static void migrateRuntimeIndices(
      final String operatePrefix,
      final String tasklistPrefix,
      final ConnectConfiguration connectConfig,
      final PrefixMigrationClient prefixMigrationClient,
      final ExecutorService executor) {

    final var srcToDestOperateMigrationMap =
        createSrcToDestMigrationMap(
            operatePrefix, connectConfig.getIndexPrefix(), OPERATE_INDICES_TO_MIGRATE);
    migrateIndicesWithRetry(srcToDestOperateMigrationMap, executor, prefixMigrationClient);

    final var srcToDestTasklistMigrationMap =
        createSrcToDestMigrationMap(
            tasklistPrefix, connectConfig.getIndexPrefix(), TASKLIST_INDICES_TO_MIGRATE);
    migrateIndicesWithRetry(srcToDestTasklistMigrationMap, executor, prefixMigrationClient);
  }

  public static void migrateHistoricIndices(
      final String operatePrefix,
      final String tasklistPrefix,
      final ConnectConfiguration connectConfig,
      final PrefixMigrationClient prefixMigrationClient,
      final ExecutorService executor) {
    final var srcToDestOperateCloneMap =
        createSrcToDestCloneMap(
            operatePrefix, connectConfig.getIndexPrefix() + "-operate", prefixMigrationClient);
    cloneHistoricIndicesWithRetry(srcToDestOperateCloneMap, prefixMigrationClient, executor);

    final var srcToDestTasklistCloneMap =
        createSrcToDestCloneMap(
            tasklistPrefix, connectConfig.getIndexPrefix() + "-tasklist", prefixMigrationClient);
    cloneHistoricIndicesWithRetry(srcToDestTasklistCloneMap, prefixMigrationClient, executor);
  }

  private static void cloneHistoricIndicesWithRetry(
      final Map<String, String> srcToDestCloneMap,
      final PrefixMigrationClient prefixMigrationClient,
      final ExecutorService executor) {

    Map<String, String> failedClones = srcToDestCloneMap;

    final int retries = 0;
    do {
      failedClones =
          cloneIndices(failedClones, prefixMigrationClient, executor).stream()
              .filter(res -> !res.successful())
              .peek(
                  res -> {
                    LOG.warn(
                        "Failed to clone {} to {} due to {}",
                        res.source(),
                        res.destination(),
                        res.failureReason());
                  })
              .collect(Collectors.toMap(CloneResult::source, CloneResult::destination));

      if (!failedClones.isEmpty()) {
        LOG.warn("Clone attempt {} failed, retrying...", retries + 1);
      }
    } while (!failedClones.isEmpty() && retries < MIGRATION_MAX_RETRIES);

    if (!failedClones.isEmpty()) {
      LOG.error("Cloning {} failed, aborting...", srcToDestCloneMap);
    }
  }

  private static void migrateIndicesWithRetry(
      final Map<String, String> srcToDestMigrationMap,
      final ExecutorService executor,
      final PrefixMigrationClient prefixMigrationClient) {
    Map<String, String> failedReindex = srcToDestMigrationMap;

    int retries = 0;
    do {
      failedReindex =
          migrateIndices(failedReindex, executor, prefixMigrationClient).stream()
              .filter(res -> !res.successful())
              .peek(
                  res ->
                      LOG.warn(
                          "Failed to migrate {} to {} due to {}",
                          res.source(),
                          res.destination(),
                          res.failureReason()))
              .collect(Collectors.toMap(ReindexResult::source, ReindexResult::destination));

      if (!failedReindex.isEmpty()) {
        LOG.warn("Reindex attempt {} failed, retrying...", retries + 1);
      }

      retries++;
    } while (!failedReindex.isEmpty() && retries < MIGRATION_MAX_RETRIES);

    if (!failedReindex.isEmpty()) {
      LOG.error("Reindexing {} failed, aborting...", srcToDestMigrationMap);
    }
  }

  private static Map<String, String> createSrcToDestMigrationMap(
      final String oldPrefix,
      final String newPrefix,
      final Set<Class<? extends AbstractIndexDescriptor>> indicesToMigrateClasses) {
    final var indicesWithNewPrefix = new IndexDescriptors(newPrefix, true);

    return indicesToMigrateClasses.stream()
        .map(
            descriptorClass -> {
              final var newIndex = indicesWithNewPrefix.get(descriptorClass);
              // we can use the new version index as it does not change
              final var oldIndexName =
                  String.format(
                      "%s-%s-%s_", oldPrefix, newIndex.getIndexName(), newIndex.getVersion());

              return Map.entry(oldIndexName, newIndex.getFullQualifiedName());
            })
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  private static Map<String, String> createSrcToDestCloneMap(
      final String oldPrefix,
      final String newPrefix,
      final PrefixMigrationClient prefixMigrationClient) {
    return prefixMigrationClient.getAllHistoricIndices(oldPrefix).stream()
        .collect(Collectors.toMap(idx -> idx, idx -> idx.replace(oldPrefix, newPrefix)));
  }

  private static List<ReindexResult> migrateIndices(
      final Map<String, String> indicesToMigrateSrcToDest,
      final ExecutorService executor,
      final PrefixMigrationClient prefixMigrationClient) {
    final var reindexFutures =
        indicesToMigrateSrcToDest.entrySet().stream()
            .map(
                (ent) ->
                    CompletableFuture.supplyAsync(
                        () -> prefixMigrationClient.reindex(ent.getKey(), ent.getValue()),
                        executor))
            .toList();

    CompletableFuture.allOf(reindexFutures.toArray(new CompletableFuture[0])).join();

    return reindexFutures.stream().map(CompletableFuture::join).toList();
  }

  private static List<CloneResult> cloneIndices(
      final Map<String, String> srcToDestCloneMap,
      final PrefixMigrationClient prefixMigrationClient,
      final ExecutorService executor) {
    final var cloneFutures =
        srcToDestCloneMap.entrySet().stream()
            .map(
                (ent) ->
                    CompletableFuture.supplyAsync(
                        () -> prefixMigrationClient.clone(ent.getKey(), ent.getValue()), executor))
            .toList();
    CompletableFuture.allOf(cloneFutures.toArray(new CompletableFuture[0])).join();

    return cloneFutures.stream().map(CompletableFuture::join).toList();
  }
}
